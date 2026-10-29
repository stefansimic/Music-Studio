package dev.rs.slava.icxc.musicstudio.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class AppConfigTest {

    private static Properties sys(String os, String home) {
        Properties properties = new Properties();
        properties.setProperty("os.name", os);
        if (home != null) {
            properties.setProperty("user.home", home);
        }
        return properties;
    }

    @Test
    void linuxDefaults() {
        AppConfig config = AppConfig.fromEnvironment(Map.of(), sys("Linux", "/home/ana"));
        assertThat(config.configDir()).isEqualTo(Path.of("/home/ana/.config/music-studio"));
        assertThat(config.dataDir()).isEqualTo(Path.of("/home/ana/.local/share/music-studio"));
    }

    @Test
    void linuxXdgVariablesWin() {
        AppConfig config = AppConfig.fromEnvironment(
                Map.of("XDG_CONFIG_HOME", "/cfg", "XDG_DATA_HOME", "/data"), sys("Linux", "/home/ana"));
        assertThat(config.configDir()).isEqualTo(Path.of("/cfg/music-studio"));
        assertThat(config.dataDir()).isEqualTo(Path.of("/data/music-studio"));
    }

    @Test
    void relativeOrBlankXdgValuesAreIgnored() {
        AppConfig config = AppConfig.fromEnvironment(
                Map.of("XDG_CONFIG_HOME", "relative/dir", "XDG_DATA_HOME", "  "), sys("Linux", "/home/ana"));
        assertThat(config.configDir()).isEqualTo(Path.of("/home/ana/.config/music-studio"));
        assertThat(config.dataDir()).isEqualTo(Path.of("/home/ana/.local/share/music-studio"));
    }

    @Test
    void windowsUsesAppData() {
        AppConfig config = AppConfig.fromEnvironment(
                Map.of("APPDATA", "/win/Roaming", "LOCALAPPDATA", "/win/Local"), sys("Windows 11", "/win/home"));
        assertThat(config.configDir()).isEqualTo(Path.of("/win/Roaming/MusicStudio"));
        assertThat(config.dataDir()).isEqualTo(Path.of("/win/Local/MusicStudio"));
    }

    @Test
    void windowsFallsBackToTheUserProfile() {
        AppConfig config = AppConfig.fromEnvironment(Map.of(), sys("Windows 10", "/win/home"));
        assertThat(config.configDir()).isEqualTo(Path.of("/win/home/AppData/Roaming/MusicStudio"));
        assertThat(config.dataDir()).isEqualTo(config.configDir());
    }

    @Test
    void macUsesApplicationSupport() {
        AppConfig config = AppConfig.fromEnvironment(Map.of(), sys("Mac OS X", "/Users/ana"));
        assertThat(config.configDir()).isEqualTo(Path.of("/Users/ana/Library/Application Support/MusicStudio"));
        assertThat(config.dataDir()).isEqualTo(config.configDir());
    }

    @Test
    void nothingIsCreatedOnDisk() {
        AppConfig config = AppConfig.fromEnvironment(Map.of(), sys("Linux", "/definitely/not/there"));
        assertThat(config.configDir().toFile()).doesNotExist();
    }

    @Test
    void preconditions() {
        assertThatThrownBy(() -> AppConfig.fromEnvironment(null, sys("Linux", "/h"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AppConfig.fromEnvironment(Map.of(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AppConfig.fromEnvironment(Map.of(), sys("Linux", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AppConfig.fromEnvironment(Map.of(), sys("Linux", "relative")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppConfig(Path.of("rel"), Path.of("/abs"), java.time.Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
