package dev.rs.slava.icxc.musicstudio.core;

import com.tngtech.archunit.core.domain.JavaClasses;
import dev.rs.slava.icxc.musicstudio.core.testkit.arch.ArchitectureRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Applies A1–A7, A11 and A12 to core's production classes. */
class CoreArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = ArchitectureRules.productionClasses();
    }

    @Test
    void a1CoreDependsOnlyOnWhitelist() {
        ArchitectureRules.A1_CORE_DEPENDS_ONLY_ON_WHITELIST.check(classes);
    }

    @Test
    void a2CoreDoesNoIo() {
        ArchitectureRules.A2_CORE_DOES_NO_IO.check(classes);
    }

    @Test
    void a3CoreLayering() {
        ArchitectureRules.A3_CORE_LAYERING.check(classes);
    }

    @Test
    void a4PortsAreInterfacesAndValues() {
        ArchitectureRules.A4_PORTS_ARE_INTERFACES_AND_VALUES.check(classes);
    }

    @Test
    void a5ValuePackagesAreImmutable() {
        ArchitectureRules.A5_VALUE_PACKAGES_ARE_IMMUTABLE.check(classes);
    }

    @Test
    void a6CoreFieldsAreFinal() {
        ArchitectureRules.A6_CORE_FIELDS_ARE_FINAL.check(classes);
    }

    @Test
    void a7Services() {
        ArchitectureRules.A7_SERVICES.check(classes);
    }

    @Test
    void a12ExtractIsPure() {
        ArchitectureRules.A12_EXTRACT_IS_PURE.check(classes);
    }

    @Test
    void a11NoReflection() {
        ArchitectureRules.A11_NO_REFLECTION.check(classes);
    }
}
