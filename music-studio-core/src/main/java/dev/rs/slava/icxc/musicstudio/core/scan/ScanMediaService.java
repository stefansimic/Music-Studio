package dev.rs.slava.icxc.musicstudio.core.scan;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.PathOrder;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.port.DirectoryListing;
import dev.rs.slava.icxc.musicstudio.core.port.ListingOptions;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.port.MediaFileSource;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.OperationCancelledException;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressEvent;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Implements {@link ScanMediaUseCase} on a {@link MediaFileSource} and a {@link TagReader}: list → partition by
 * format → sort by {@link PathOrder} → fingerprint and read file by file, emitting each result immediately.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class ScanMediaService implements ScanMediaUseCase {

    private static final String PHASE_LIST = "list";
    private static final String PHASE_READ = "read";

    private final MediaFileSource source;
    private final TagReader reader;

    /**
     * @param source lists and fingerprints files; not null
     * @param reader reads tags; not null
     * @throws NullPointerException if an argument is null
     */
    public ScanMediaService(MediaFileSource source, TagReader reader) {
        this.source = Require.nonNull(source, "source");
        this.reader = Require.nonNull(reader, "reader");
    }

    @Override
    public ScanSummary scan(ScanRequest request, Consumer<? super ScanItem> sink, ProgressListener progress,
                            Cancellation cancel) {
        Require.nonNull(request, "request");
        Require.nonNull(sink, "sink");
        Require.nonNull(progress, "progress");
        Require.nonNull(cancel, "cancel");
        Path root = request.root();

        progress.onProgress(ProgressEvent.of(PHASE_LIST, 0, -1));
        DirectoryListing listing;
        try {
            listing = source.list(root,
                    new ListingOptions(request.recursive(), request.includeHidden(), request.excludedDirectories()),
                    cancel);
        } catch (MediaAccessException e) {
            return failed(root, e);
        } catch (OperationCancelledException e) {
            return new ScanSummary(root, ScanStatus.CANCELLED, 0, 0, 0, 0, 0, Map.of(), List.of(), List.of());
        }

        List<Path> candidates = new ArrayList<>();
        Map<String, Integer> ignoredByExtension = new HashMap<>();
        for (Path file : listing.files()) {
            String name = file.getFileName().toString();
            Optional<MediaFormat> format = MediaFormat.ofFileName(name);
            if (format.isPresent() && !ExportTarget.isTempName(name) && request.formats().contains(format.get())
                    && reader.readableFormats().contains(format.get())) {
                candidates.add(file);
            } else {
                ignoredByExtension.merge(MediaFormat.extensionOf(name).orElse(""), 1, Integer::sum);
            }
        }
        candidates.sort(PathOrder.COMPARATOR);
        List<SkippedEntry> skipped = new ArrayList<>(listing.skipped());
        skipped.sort(Comparator.comparing(SkippedEntry::path, PathOrder.COMPARATOR));
        int ignored = listing.files().size() - candidates.size();
        int total = candidates.size();
        progress.onProgress(ProgressEvent.of(PHASE_LIST, listing.files().size(), listing.files().size()));

        int read = 0;
        int failed = 0;
        int withErrors = 0;
        ScanStatus status = ScanStatus.COMPLETED;
        for (Path file : candidates) {
            if (cancel.isCancelled()) {
                status = ScanStatus.CANCELLED;
                break;
            }
            ScanItem item = readOne(file, request);
            if (item instanceof ScannedFile scanned) {
                read++;
                if (scanned.tags().issues().stream().anyMatch(i -> i.severity() == Severity.ERROR)) {
                    withErrors++;
                }
            } else {
                failed++;
            }
            sink.accept(item);
            progress.onProgress(ProgressEvent.of(PHASE_READ, read + failed, total, file));
        }
        assert status != ScanStatus.COMPLETED || read + failed == total : "every found file must be delivered";
        return new ScanSummary(root, status, total, read, failed, withErrors, ignored, ignoredByExtension, skipped,
                listing.issues());
    }

    private ScanItem readOne(Path file, ScanRequest request) {
        MediaFormat format = MediaFormat.ofFileName(file.getFileName().toString()).orElseThrow();
        try {
            var fingerprint = source.fingerprint(file);
            TagReadResult tags = reader.read(file);
            return new ScannedFile(file, format, fingerprint, tags, tags.conflicts(request.v1Comparison()));
        } catch (MediaAccessException e) {
            return new UnreadableFile(file, format, Issue.error(IssueCode.READ_FAILED, e.getMessage()).forFile(file));
        } catch (OperationCancelledException e) {
            throw e;
        } catch (RuntimeException e) {
            // an adapter broke its contract; one bad file must not abort a scan of thousands
            String detail = e.getMessage() == null ? e.getClass().getSimpleName()
                    : e.getClass().getSimpleName() + ": " + e.getMessage();
            return new UnreadableFile(file, format,
                    Issue.error(IssueCode.READ_FAILED, "unexpected error while reading (" + detail + ")").forFile(file));
        }
    }

    private static ScanSummary failed(Path root, MediaAccessException e) {
        IssueCode code = switch (e.reason()) {
            case ACCESS_DENIED -> IssueCode.LISTING_ACCESS_DENIED;
            case NOT_FOUND, NOT_A_DIRECTORY -> IssueCode.ROOT_NOT_FOUND;
            default -> IssueCode.READ_FAILED;
        };
        Issue issue = Issue.error(code, e.getMessage()).forFile(root);
        return new ScanSummary(root, ScanStatus.FAILED, 0, 0, 0, 0, 0, Map.of(), List.of(), List.of(issue));
    }

}
