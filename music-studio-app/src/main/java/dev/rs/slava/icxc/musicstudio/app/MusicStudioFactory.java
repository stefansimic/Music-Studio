package dev.rs.slava.icxc.musicstudio.app;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanMediaService;
import dev.rs.slava.icxc.musicstudio.storage.LocalMediaFileSource;
import dev.rs.slava.icxc.musicstudio.tag.jaudiotagger.JaudiotaggerTagReader;

/**
 * The composition root: the only place that chooses adapters and creates services. Manual wiring, no service locator
 * and no reflection, so it works in a native image.
 */
public final class MusicStudioFactory {

    private final AppConfig config;

    /**
     * @param config where the application keeps its files; not null
     */
    public MusicStudioFactory(AppConfig config) {
        this.config = Require.nonNull(config, "config");
    }

    /** @return the application's use cases; never null. Creates nothing on disk. */
    public MusicStudio create() {
        assert config != null;
        return new MusicStudio(new ScanMediaService(new LocalMediaFileSource(), new JaudiotaggerTagReader()));
    }
}
