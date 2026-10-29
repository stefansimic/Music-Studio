package dev.rs.slava.icxc.musicstudio.core.scan;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.PathOrder;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.port.DirectoryListing;
import dev.rs.slava.icxc.musicstudio.core.port.ListingOptions;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Default {@link ScanMediaUseCase}: list, filter by format, sort, then read file by file and stream the results.
 *
 * <p><b>Thread-safety:</b> stateless; safe for concurrent use as long as the ports are.
 */
public final class ScanMediaService implements ScanMediaUseCase {

    private final MediaFileSource files;
    private final TagReader reader;

    /**
     * @param files lists and fingerprints files; not null
     * @param reader reads tags; not null
     */
    public ScanMediaService(MediaFileSource files, TagReader reader) {
        this.files = Require.nonNull(files, "files");
        this.reader = Require.nonNull(reader, "reader");
    }

    @Override
    public ScanSummary scan(ScanRequest request, Consumer<? super ScanItem> sink, ProgressListener progress,
                            Cancellation cancel) {
        Require.nonNull(request, "request");
        Require.nonNull(sink, "sink");
        Require.nonNull(progress, "progress");
        Require.nonNull(cancel, "cancel");

        progress.onProgress(ProgressEvent.of("list", 0, -1, request.root()));
        DirectoryListing listing;
        try {
            ListingOptions options = new ListingOptions(request.recursive(), request.includeHidden(),
                    request.excludedDirectories());
            listing = files.list(request.root(), options, cancel);
        } catch (MediaAccessException e) {
            return failed(request, e);
        } catch (OperationCancelledException e) {
            return new ScanSummary(request.root(), ScanStatus.CANCELLED, 0, 0, 0, 0, Map.of(), List.of(), List.of());
        }

        Set<MediaFormat> formats = new HashSet<>(request.formats());
        formats.retainAll(reader.readableFormats());
        List<Path> candidates = new ArrayList<>();
        Map<String, Integer> ignoredByExtension = new TreeMap<>();
        int ignored = 0;
        for (Path file : listing.files()) {
            String name = file.getFileName().toString();
            if (MediaFormat.ofFileName(name).filter(formats::contains).isPresent()) {
                candidates.add(file);
            } else {
                ignored++;
                ignoredByExtension.merge(extensionOf(name), 1, Integer::sum);
            }
        }
        candidates.sort(PathOrder.COMPARATOR);
        List<SkippedEntry> skipped = new ArrayList<>(listing.skipped());
        skipped.sort(Comparator.comparing(SkippedEntry::path, PathOrder.COMPARATOR));
        int total = candidates.size();
        progress.onProgress(ProgressEvent.of("list", total, total, request.root()));
        progress.onProgress(ProgressEvent.of("read", 0, total));

        int read = 0;
        int failed = 0;
        ScanStatus status = ScanStatus.COMPLETED;
        for (Path file : candidates) {
            if (cancel.isCancelled()) {
                status = ScanStatus.CANCELLED;
                break;
            }
            ScanItem item = readOne(file, request);
            if (item instanceof ScannedFile) {
                read++;
            } else {
                failed++;
            }
            sink.accept(item);
            progress.onProgress(ProgressEvent.of("read", read + failed, total, file));
        }
        assert status != ScanStatus.COMPLETED || total == read + failed : "every found file must be delivered";
        return new ScanSummary(request.root(), status, total, read, failed, ignored, ignoredByExtension, skipped,
                listing.issues());
    }

    private ScanItem readOne(Path file, ScanRequest request) {
        MediaFormat format = MediaFormat.ofFileName(file.getFileName().toString()).orElseThrow();
        try {
            FileFingerprint fingerprint = files.fingerprint(file);
            TagReadResult tags = reader.read(file);
            return new ScannedFile(file, format, fingerprint, tags, tags.conflicts(request.v1Comparison()));
        } catch (MediaAccessException e) {
            Issue error = Issue.error(IssueCode.READ_FAILED, e.reason() + ": " + e.getMessage()).forFile(file);
            return new UnreadableFile(file, format, error);
        }
    }

    private static ScanSummary failed(ScanRequest request, MediaAccessException e) {
        IssueCode code = switch (e.reason()) {
            case NOT_FOUND, NOT_A_DIRECTORY -> IssueCode.ROOT_NOT_FOUND;
            default -> IssueCode.LISTING_ACCESS_DENIED;
        };
        Issue issue = Issue.error(code, e.getMessage()).forFile(request.root());
        return new ScanSummary(request.root(), ScanStatus.FAILED, 0, 0, 0, 0, Map.of(), List.of(), List.of(issue));
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return ScanSummary.NO_EXTENSION;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
