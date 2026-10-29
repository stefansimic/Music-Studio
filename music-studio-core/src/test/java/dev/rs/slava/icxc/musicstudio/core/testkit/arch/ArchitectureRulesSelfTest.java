package dev.rs.slava.icxc.musicstudio.core.testkit.arch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Proves that the rules can fail: each one is applied to a tiny class that violates it on purpose. A rule that
 * never fails would give false confidence.
 */
class ArchitectureRulesSelfTest {

    private static final JavaClasses VIOLATIONS =
            new ClassFileImporter().importPackages("dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations");

    private static void assertViolated(ArchRule rule) {
        assertThatThrownBy(() -> rule.check(VIOLATIONS)).isInstanceOf(AssertionError.class);
    }

    @Test
    void a1CatchesDependenciesOutsideTheWhitelist() {
        assertViolated(ArchitectureRules.A1_CORE_DEPENDS_ONLY_ON_WHITELIST);
    }

    @Test
    void a2CatchesFilesConsoleAndClassLoading() {
        assertViolated(ArchitectureRules.A2_CORE_DOES_NO_IO);
    }

    @Test
    void a6CatchesMutableFields() {
        assertViolated(ArchitectureRules.A6_CORE_FIELDS_ARE_FINAL);
    }

    @Test
    void a7CatchesBadServices() {
        assertViolated(ArchitectureRules.A7_SERVICES);
    }

    @Test
    void a11CatchesReflection() {
        assertViolated(ArchitectureRules.A11_NO_REFLECTION);
    }

    @Test
    void d02CatchesFilesAndRegex() {
        assertViolated(ArchitectureRules.noWritePath("dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations.."));
    }

    @Test
    void d02CatchesRegexAlone() {
        JavaClasses regexOnly = new ClassFileImporter().importClasses(
                dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations.UsesRegex.class);
        assertThatThrownBy(() -> ArchitectureRules.noWritePath(
                "dev.rs.slava.icxc.musicstudio.core.testkit.arch.violations..").check(regexOnly))
                .isInstanceOf(AssertionError.class);
    }
}
