package dev.rs.slava.icxc.musicstudio.app;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Application directories and clock, resolved once from the environment (architecture §13). Pure: resolving
 * creates no directory.
 *
 * <ul>
 *   <li>Linux/other: config {@code $XDG_CONFIG_HOME/music-studio} (default {@code ~/.config/music-studio}),
 *       data {@code $XDG_DATA_HOME/music-studio} (default {@code ~/.local/share/music-studio})</li>
 *   <li>Windows: config {@code %APPDATA%\MusicStudio}, data {@code %LOCALAPPDATA%\MusicStudio} (fallback APPDATA)</li>
 *   <li>macOS: both {@code ~/Library/Application Support/MusicStudio}</li>
 * </ul>
 * Relative XDG/APPDATA values are ignored (the XDG spec requires absolute paths).
 *
 * @param configDir settings, profiles, presets, genres; absolute, not null
 * @param dataDir   export journals, library index; absolute, not null
 * @param clock     time source; not null
 */
public record AppConfig(Path configDir, Path dataDir, Clock clock) {

    private static final String LINUX_NAME = "music-studio";
    private static final String DESKTOP_NAME = "MusicStudio";

    /**
     * Validates the components.
     *
     * @throws NullPointerException     if a component is null
     * @throws IllegalArgumentException if a directory is relative
     */
    public AppConfig {
        Require.absolute(configDir, "configDir");
        Require.absolute(dataDir, "dataDir");
        Require.nonNull(clock, "clock");
    }

    /**
     * Resolves the directories for the current platform.
     *
     * @param env the environment variables (e.g. {@code System.getenv()}); not null
     * @param sys the system properties (e.g. {@code System.getProperties()}); not null, must contain
     *            {@code user.home} as an absolute path
     * @return the configuration with the system clock; never null
     * @throws NullPointerException     if an argument is null
     * @throws IllegalArgumentException if {@code user.home} is missing or relative
     */
    public static AppConfig fromEnvironment(Map<String, String> env, Properties sys) {
        Require.nonNull(env, "env");
        Require.nonNull(sys, "sys");
        String homeValue = sys.getProperty("user.home");
        Require.that(homeValue != null && !homeValue.isBlank(), "user.home must be set");
        Path home = Path.of(homeValue);
        Require.absolute(home, "user.home");
        String os = sys.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            Path roaming = absolute(env.get("APPDATA")).orElse(home.resolve("AppData").resolve("Roaming"));
            Path local = absolute(env.get("LOCALAPPDATA")).orElse(roaming);
            return new AppConfig(roaming.resolve(DESKTOP_NAME), local.resolve(DESKTOP_NAME), Clock.systemUTC());
        }
        if (os.startsWith("mac")) {
            Path support = home.resolve("Library").resolve("Application Support").resolve(DESKTOP_NAME);
            return new AppConfig(support, support, Clock.systemUTC());
        }
        Path config = absolute(env.get("XDG_CONFIG_HOME")).orElse(home.resolve(".config"));
        Path data = absolute(env.get("XDG_DATA_HOME")).orElse(home.resolve(".local").resolve("share"));
        return new AppConfig(config.resolve(LINUX_NAME), data.resolve(LINUX_NAME), Clock.systemUTC());
    }

    private static Optional<Path> absolute(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Path path = Path.of(value);
        return path.isAbsolute() ? Optional.of(path) : Optional.empty();
    }
}
