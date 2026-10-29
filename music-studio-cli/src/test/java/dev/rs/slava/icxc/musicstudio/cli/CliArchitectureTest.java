package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.core.testkit.arch.ArchitectureRules;
import dev.rs.slava.icxc.musicstudio.core.testkit.arch.ProductionClassesOnly;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Applies the architecture rules that concern a frontend. */
@AnalyzeClasses(packages = ArchitectureRules.ROOT, importOptions = ProductionClassesOnly.class)
class CliArchitectureTest {

    @ArchTest
    static final ArchRule a7 = ArchitectureRules.A7_SERVICES;

    @ArchTest
    static final ArchRule a9 = ArchitectureRules.A9_FRONTENDS_USE_ONLY_USE_CASES;

    @ArchTest
    static final ArchRule a10 = ArchitectureRules.A10_LIBRARY_CONTAINMENT;

    @ArchTest
    static final ArchRule a11 = ArchitectureRules.A11_NO_REFLECTION;

    @ArchTest
    static final ArchRule x04 = ArchitectureRules.X04_JAUDIOTAGGER_ADAPTER_ONLY_USED_BY_APP;

    @ArchTest
    static final ArchRule d05 = ArchitectureRules.D05_EXTRACT_COMMAND_HAS_NO_WRITE_PATH;
}
