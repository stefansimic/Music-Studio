package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.testkit.arch.ArchitectureRules;
import dev.rs.slava.icxc.musicstudio.core.testkit.arch.ProductionClassesOnly;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Applies the architecture rules that concern an adapter module. */
@AnalyzeClasses(packages = ArchitectureRules.ROOT, importOptions = ProductionClassesOnly.class)
class JaudiotaggerArchitectureTest {

    @ArchTest
    static final ArchRule a7 = ArchitectureRules.A7_SERVICES;

    @ArchTest
    static final ArchRule a8 = ArchitectureRules.A8_ADAPTERS_ARE_ISOLATED;

    @ArchTest
    static final ArchRule a10 = ArchitectureRules.A10_LIBRARY_CONTAINMENT;

    @ArchTest
    static final ArchRule a11 = ArchitectureRules.A11_NO_REFLECTION;
}
