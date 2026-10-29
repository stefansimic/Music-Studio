package dev.rs.slava.icxc.musicstudio.core;

import dev.rs.slava.icxc.musicstudio.core.testkit.arch.ArchitectureRules;
import dev.rs.slava.icxc.musicstudio.core.testkit.arch.ProductionClassesOnly;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Applies the architecture rules that concern the core module (production classes only). */
@AnalyzeClasses(packages = ArchitectureRules.ROOT, importOptions = ProductionClassesOnly.class)
class CoreArchitectureTest {

    @ArchTest
    static final ArchRule a1 = ArchitectureRules.A1_CORE_DEPENDS_ONLY_ON_WHITELIST;

    @ArchTest
    static final ArchRule a2 = ArchitectureRules.A2_CORE_DOES_NO_IO;

    @ArchTest
    static final ArchRule a3 = ArchitectureRules.A3_CORE_LAYERING;

    @ArchTest
    static final ArchRule a4 = ArchitectureRules.A4_PORT_CONTAINS_ONLY_CONTRACTS;

    @ArchTest
    static final ArchRule a5 = ArchitectureRules.A5_VALUE_PACKAGES_ARE_IMMUTABLE_SHAPES;

    @ArchTest
    static final ArchRule a5Task = ArchitectureRules.A5_TASK_CLASSES_ARE_FINAL;

    @ArchTest
    static final ArchRule a6 = ArchitectureRules.A6_CORE_FIELDS_ARE_FINAL;

    @ArchTest
    static final ArchRule a7 = ArchitectureRules.A7_SERVICES;

    @ArchTest
    static final ArchRule a11 = ArchitectureRules.A11_NO_REFLECTION;

    @ArchTest
    static final ArchRule x04Export = ArchitectureRules.X04_EXPORT_DEPENDS_ONLY_ON_LOWER_LAYERS;
}
