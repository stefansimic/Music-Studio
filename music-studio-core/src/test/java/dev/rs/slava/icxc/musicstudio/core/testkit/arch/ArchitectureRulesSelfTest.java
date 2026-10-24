package dev.rs.slava.icxc.musicstudio.core.testkit.arch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations.BadService;
import dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations.ImpureExtract;
import dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations.LoadsDynamically;
import dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations.MutableField;
import dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations.UsesFiles;
import dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations.UsesReflection;
import org.junit.jupiter.api.Test;

/** Proves that the rules actually detect violations (a rule that never fails protects nothing). */
class ArchitectureRulesSelfTest {

    private static final JavaClasses VIOLATIONS = new ClassFileImporter()
            .importClasses(UsesFiles.class, MutableField.class, UsesReflection.class, BadService.class);

    @Test
    void a1DetectsNonWhitelistedDependencies() {
        assertThatThrownBy(() -> ArchitectureRules.A1_CORE_DEPENDS_ONLY_ON_WHITELIST.check(VIOLATIONS))
                .isInstanceOf(AssertionError.class).hasMessageContaining("UsesFiles");
    }

    @Test
    void a2DetectsIo() {
        assertThatThrownBy(() -> ArchitectureRules.A2_CORE_DOES_NO_IO.check(VIOLATIONS))
                .isInstanceOf(AssertionError.class).hasMessageContaining("java.nio.file.Files");
    }

    @Test
    void a6DetectsMutableFields() {
        assertThatThrownBy(() -> ArchitectureRules.A6_CORE_FIELDS_ARE_FINAL.check(VIOLATIONS))
                .isInstanceOf(AssertionError.class).hasMessageContaining("counter");
    }

    @Test
    void a7DetectsBadServices() {
        assertThatThrownBy(() -> ArchitectureRules.A7_SERVICES.check(VIOLATIONS))
                .isInstanceOf(AssertionError.class).hasMessageContaining("BadService");
    }

    @Test
    void holesInTheWhitelistAreClosed() {
        JavaClasses dynamic = new ClassFileImporter().importClasses(LoadsDynamically.class);
        assertThatThrownBy(() -> ArchitectureRules.A1_CORE_DEPENDS_ONLY_ON_WHITELIST.check(dynamic))
                .isInstanceOf(AssertionError.class).hasMessageContaining("java.util.prefs.Preferences")
                .hasMessageContaining("java.util.ServiceLoader");
        assertThatThrownBy(() -> ArchitectureRules.A2_CORE_DOES_NO_IO.check(dynamic))
                .isInstanceOf(AssertionError.class).hasMessageContaining("ProcessBuilder")
                .hasMessageContaining("getenv");
        assertThatThrownBy(() -> ArchitectureRules.A11_NO_REFLECTION.check(dynamic))
                .isInstanceOf(AssertionError.class).hasMessageContaining("loadClass")
                .hasMessageContaining("ServiceLoader");
    }

    @Test
    void a12DetectsImpureFeatures() {
        JavaClasses impure = new ClassFileImporter().importClasses(ImpureExtract.class);
        assertThatThrownBy(() -> ArchitectureRules.pureFeature("..violations..").check(impure))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Cancellation");
    }

    @Test
    void a11DetectsReflection() {
        assertThatThrownBy(() -> ArchitectureRules.A11_NO_REFLECTION.check(VIOLATIONS))
                .isInstanceOf(AssertionError.class).hasMessageContaining("forName");
    }
}
