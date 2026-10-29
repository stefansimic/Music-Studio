package dev.rs.slava.icxc.musicstudio.core.testkit.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

/**
 * Import option for the architecture tests: only production classes. Besides the usual test-class directories it
 * also excludes the core {@code -tests.jar}, which other modules have on their test classpath (it holds the
 * contract tests and the deliberate violations of the rules' self-test).
 */
public final class ProductionClassesOnly implements ImportOption {

    private final ImportOption doNotIncludeTests = new ImportOption.DoNotIncludeTests();

    @Override
    public boolean includes(Location location) {
        return doNotIncludeTests.includes(location) && !location.contains("-tests.jar");
    }
}
