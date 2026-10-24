package dev.rs.slava.icxc.musicstudio.cli;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import dev.rs.slava.icxc.musicstudio.core.testkit.arch.ArchitectureRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Applies A7, A9, A10 and A11 to the CLI. */
class CliArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = ArchitectureRules.productionClasses();
    }

    @Test
    void a7Services() {
        ArchitectureRules.A7_SERVICES.check(classes);
    }

    @Test
    void a9FrontendsUseUseCasesOnly() {
        ArchitectureRules.A9_FRONTENDS_USE_USE_CASES_ONLY.check(classes);
    }

    @Test
    void a13NoDirectWrites() {
        ArchitectureRules.onlyWrites("dev.rs.slava.icxc.musicstudio.core.export.ApplyExportService").check(classes);
    }

    @Test
    void a10LibraryContainment() {
        for (ArchRule rule : ArchitectureRules.A10_LIBRARY_CONTAINMENT) {
            rule.check(classes);
        }
    }

    @Test
    void a11NoReflection() {
        ArchitectureRules.A11_NO_REFLECTION.check(classes);
    }
}
