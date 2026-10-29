package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;

/**
 * Renders a scan as it streams in: {@link #begin} once, {@link #item} per file in path order, {@link #end} once.
 * A renderer holds no business logic; it only formats what the use case returned.
 */
public interface ScanRenderer {

    /** Called before the first item. @param request what is being scanned; not null */
    void begin(ScanRequest request);

    /** Called for every file, in the order the use case delivers them. @param item not null */
    void item(ScanItem item);

    /** Called after the last item, also for failed and cancelled scans. @param summary not null */
    void end(ScanSummary summary);
}
