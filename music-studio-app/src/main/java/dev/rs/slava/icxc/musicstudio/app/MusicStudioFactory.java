package dev.rs.slava.icxc.musicstudio.app;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataService;
import dev.rs.slava.icxc.musicstudio.core.port.MediaFileSource;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanMediaService;
import dev.rs.slava.icxc.musicstudio.storage.LocalMediaFileSource;
import dev.rs.slava.icxc.musicstudio.tag.jaudiotagger.JaudiotaggerTagReader;

/**
 * The composition root: the only place that chooses adapters and instantiates services. Contains no logic.
 *
 * <p><b>Thread-safety:</b> {@link #create()} may be called from any thread; the returned use cases are thread-safe.
 */
public final class MusicStudioFactory {

    private final AppConfig config;

    /**
     * @param config application directories and clock; not null
     * @throws NullPointerException if {@code config} is null
     */
    public MusicStudioFactory(AppConfig config) {
        this.config = Require.nonNull(config, "config");
    }

    /** @return the configuration this factory wires with; never null */
    public AppConfig config() {
        return config;
    }

    /**
     * Wires all adapters and use cases. Creates no files or directories.
     *
     * @return the use cases; never null
     */
    public MusicStudio create() {
        MediaFileSource source = new LocalMediaFileSource();
        TagReader reader = new JaudiotaggerTagReader();
        return new MusicStudio(new ScanMediaService(source, reader), new ProposeMetadataService());
    }
}
