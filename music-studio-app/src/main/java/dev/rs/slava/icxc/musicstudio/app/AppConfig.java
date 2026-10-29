package dev.rs.slava.icxc.musicstudio.app;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Where the application keeps its files, and the clock it uses. Resolved once from the environment and handed to the
 * adapters; core never sees these paths. Nothing is created on disk here.
 *
 * @param configDir directory for settings, profiles, presets and genres; absolute
 * @param dataDir   directory for export journals and the library index; absolute
 * @param clock     time source; not null
 */
public record AppConfig(Path configDir, Path dataDir, Clock clock) {

    private static final String LINUX_DIR = "music-studio";
    private static final String CAPITALISED_DIR = "MusicStudio";

    public AppConfig {
        Require.absolute(configDir, "configDir");
        Require.absolute(dataDir, "dataDir");
        Require.nonNull(clock, "clock");
    }

    /**
     * Resolves the directories like the platform expects it:
     * <ul>
     *   <li>Linux and others: {@code $XDG_CONFIG_HOME/music-studio} (default {@code ~/.config/music-studio}) and
     *       {@code $XDG_DATA_HOME/music-studio} (default {@code ~/.local/share/music-studio}); a relative XDG value is
     *       ignored, as the XDG specification demands</li>
     *   <li>Windows: {@code %APPDATA%\MusicStudio} and {@code %LOCALAPPDATA%\MusicStudio}</li>
     *   <li>macOS: {@code ~/Library/Application Support/MusicStudio} for both</li>
     * </ul>
     *
     * @param env environment variables; not null
     * @param sys system properties, at least {@code os.name} and {@code user.home}; not null
     * @return the configuration with the system UTC clock; never null
     * @throws NullPointerException     if an argument is null
     * @throws IllegalArgumentException if {@code user.home} is missing or not absolute and the platform needs it
     */
    public static AppConfig fromEnvironment(Map<String, String> env, Properties sys) {
        Require.nonNull(env, "env");
        Require.nonNull(sys, "sys");
        String os = sys.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            Path home = home(sys);
            Path roaming = absoluteEnv(env, "APPDATA").orElseGet(() -> home.resolve("AppData").resolve("Roaming"));
            Path local = absoluteEnv(env, "LOCALAPPDATA").orElse(roaming);
            return new AppConfig(roaming.resolve(CAPITALISED_DIR), local.resolve(CAPITALISED_DIR), Clock.systemUTC());
        }
        Path home = home(sys);
        if (os.startsWith("mac")) {
            Path support = home.resolve("Library").resolve("Application Support").resolve(CAPITALISED_DIR);
            return new AppConfig(support, support, Clock.systemUTC());
        }
        Path config = absoluteEnv(env, "XDG_CONFIG_HOME").orElseGet(() -> home.resolve(".config"));
        Path data = absoluteEnv(env, "XDG_DATA_HOME").orElseGet(() -> home.resolve(".local").resolve("share"));
        return new AppConfig(config.resolve(LINUX_DIR), data.resolve(LINUX_DIR), Clock.systemUTC());
    }

    private static Path home(Properties sys) {
        String home = sys.getProperty("user.home");
        Require.that(home != null && !home.isBlank(), "system property user.home is not set");
        return Require.absolute(Path.of(home), "user.home");
    }

    private static Optional<Path> absoluteEnv(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(value);
            return path.isAbsolute() ? Optional.of(path) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }
}
