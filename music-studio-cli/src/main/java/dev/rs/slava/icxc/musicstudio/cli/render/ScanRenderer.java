package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;

/** Renders a scan while it streams: {@link #begin()}, one {@link #item} per file, then {@link #end}. */
public interface ScanRenderer {

    /** Called once before the first item. */
    void begin();

    /**
     * Called once per file, in scan order.
     *
     * @param item the scanned file; not null
     */
    void item(ScanItem item);

    /**
     * Called once after the last item.
     *
     * @param summary the scan summary; not null
     */
    void end(ScanSummary summary);
}
