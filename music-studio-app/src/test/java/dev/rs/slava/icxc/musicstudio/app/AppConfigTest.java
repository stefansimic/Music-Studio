package dev.rs.slava.icxc.musicstudio.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class AppConfigTest {

    private static Properties sys(String os, String home) {
        Properties properties = new Properties();
        properties.setProperty("os.name", os);
        properties.setProperty("user.home", home);
        return properties;
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void linuxUsesXdgVariables() {
        AppConfig config = AppConfig.fromEnvironment(
                Map.of("XDG_CONFIG_HOME", "/xdg/config", "XDG_DATA_HOME", "/xdg/data"), sys("Linux", "/home/u"));
        assertThat(config.configDir()).isEqualTo(Path.of("/xdg/config/music-studio"));
        assertThat(config.dataDir()).isEqualTo(Path.of("/xdg/data/music-studio"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void linuxFallsBackToHome() {
        AppConfig config = AppConfig.fromEnvironment(Map.of(), sys("Linux", "/home/u"));
        assertThat(config.configDir()).isEqualTo(Path.of("/home/u/.config/music-studio"));
        assertThat(config.dataDir()).isEqualTo(Path.of("/home/u/.local/share/music-studio"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void relativeXdgValuesAreIgnored() {
        AppConfig config = AppConfig.fromEnvironment(Map.of("XDG_CONFIG_HOME", "relative"), sys("Linux", "/home/u"));
        assertThat(config.configDir()).isEqualTo(Path.of("/home/u/.config/music-studio"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void macOsUsesApplicationSupport() {
        AppConfig config = AppConfig.fromEnvironment(Map.of(), sys("Mac OS X", "/Users/u"));
        assertThat(config.configDir()).isEqualTo(Path.of("/Users/u/Library/Application Support/MusicStudio"));
        assertThat(config.dataDir()).isEqualTo(config.configDir());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsUsesAppData() {
        AppConfig config = AppConfig.fromEnvironment(
                Map.of("APPDATA", "C:\\Users\\u\\AppData\\Roaming", "LOCALAPPDATA", "C:\\Users\\u\\AppData\\Local"),
                sys("Windows 11", "C:\\Users\\u"));
        assertThat(config.configDir()).isEqualTo(Path.of("C:\\Users\\u\\AppData\\Roaming\\MusicStudio"));
        assertThat(config.dataDir()).isEqualTo(Path.of("C:\\Users\\u\\AppData\\Local\\MusicStudio"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void windowsNameWithUnixPathsStillResolves() {
        AppConfig config = AppConfig.fromEnvironment(Map.of("APPDATA", "/roaming"), sys("Windows 11", "/home/u"));
        assertThat(config.configDir()).isEqualTo(Path.of("/roaming/MusicStudio"));
        assertThat(config.dataDir()).isEqualTo(Path.of("/roaming/MusicStudio"));
    }

    @Test
    void preconditions() {
        assertThatThrownBy(() -> AppConfig.fromEnvironment(null, new Properties()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AppConfig.fromEnvironment(Map.of(), new Properties()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AppConfig.fromEnvironment(Map.of(), sys("Linux", "relative")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
