package dev.rs.slava.icxc.musicstudio.core.testkit.arch;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.Architectures;
import java.util.List;
import java.util.Set;

/**
 * The architecture rules A1–A11 (architecture §14.4), defined once and applied by a small test in every module.
 * Rules are never weakened to make code compile.
 */
public final class ArchitectureRules {

    /** Root package of all our code. */
    public static final String ROOT = "dev.rs.slava.icxc.musicstudio";
    private static final String CORE = ROOT + ".core";

    private static final String[] ADAPTER_PACKAGES = {
            ROOT + ".tag..", ROOT + ".storage..", ROOT + ".image..", ROOT + ".player.."};

    private ArchitectureRules() {
    }

    /**
     * Imports our production classes from the classpath: test classes, the core test-jar and the testkit are
     * excluded.
     *
     * @return the classes to check
     */
    public static JavaClasses productionClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(location -> !location.contains("-tests.jar"))
                .withImportOption(location -> !location.contains("/testkit/"))
                .importPackages(ROOT);
    }

    // --- A1 / A2: core is pure ------------------------------------------------------------------------------

    private static final DescribedPredicate<JavaClass> CORE_ALLOWED_DEPENDENCY = describe(
            "core, java.lang, java.util, java.time, java.text, java.nio.file.Path, java.nio.charset, java.math",
            target -> {
                JavaClass type = target.getBaseComponentType();
                String pkg = type.getPackageName();
                return type.isPrimitive()
                        || pkg.equals(CORE) || pkg.startsWith(CORE + ".")
                        || pkg.equals("java.lang") || pkg.equals("java.lang.runtime") || pkg.equals("java.lang.invoke")
                        || (pkg.equals("java.util") || pkg.startsWith("java.util.")) && !forbiddenUtil(type)
                        || pkg.equals("java.time") || pkg.startsWith("java.time.")
                        || pkg.equals("java.text")
                        || type.getName().equals("java.nio.file.Path")
                        || pkg.equals("java.nio.charset")
                        || pkg.equals("java.math");
            });

    /** java.util parts that do I/O, load classes dynamically or are service locators. */
    private static boolean forbiddenUtil(JavaClass type) {
        String pkg = type.getPackageName();
        return pkg.startsWith("java.util.logging") || pkg.startsWith("java.util.prefs")
                || pkg.startsWith("java.util.zip") || pkg.startsWith("java.util.jar") || pkg.startsWith("java.util.spi")
                || type.getName().equals("java.util.ServiceLoader");
    }

    /** A1: core depends only on core and whitelisted JDK packages. */
    public static final ArchRule A1_CORE_DEPENDS_ONLY_ON_WHITELIST = classes()
            .that().resideInAPackage(CORE + "..")
            .should().onlyDependOnClassesThat(CORE_ALLOWED_DEPENDENCY)
            .as("A1: core depends only on core and whitelisted JDK packages");

    /** A2: core does no I/O, reflection, logging or console output. */
    public static final ArchRule A2_CORE_DOES_NO_IO = noClasses()
            .that().resideInAPackage(CORE + "..")
            .should().dependOnClassesThat().resideInAnyPackage("java.io..", "java.nio.channels..", "java.net..",
                    "java.awt..", "javax..", "java.sql..", "java.util.logging..", "java.lang.reflect..")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.nio.file.Files")
            .orShould().accessField(System.class, "out")
            .orShould().accessField(System.class, "err")
            .orShould().callMethod(java.nio.file.Path.class, "toFile")
            .orShould().callMethod(java.nio.file.Path.class, "toRealPath", java.nio.file.LinkOption[].class)
            .orShould().callMethod(Class.class, "forName", String.class)
            .orShould().dependOnClassesThat().haveFullyQualifiedName(ProcessBuilder.class.getName())
            .orShould().dependOnClassesThat().haveFullyQualifiedName(Runtime.class.getName())
            .orShould().dependOnClassesThat().haveFullyQualifiedName(ClassLoader.class.getName())
            .orShould().callMethod(System.class, "getenv")
            .orShould().callMethod(System.class, "getenv", String.class)
            .orShould().callMethod(System.class, "getProperty", String.class)
            .orShould().callMethod(System.class, "getProperty", String.class, String.class)
            .as("A2: core does no I/O, processes, environment access, reflection, logging or console output");

    // --- A3: core layering ----------------------------------------------------------------------------------

    /** A3: core packages only use the packages below them (architecture §3). */
    public static final ArchRule A3_CORE_LAYERING = Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("contract").definedBy(CORE + ".contract..")
            .layer("text").definedBy(CORE + ".text..")
            .layer("model").definedBy(CORE + ".model..")
            .layer("tag").definedBy(CORE + ".tag..")
            .layer("pattern").definedBy(CORE + ".pattern..")
            .layer("task").definedBy(CORE + ".task..")
            .layer("port").definedBy(CORE + ".port..")
            .layer("scan").definedBy(CORE + ".scan..")
            .layer("extract").definedBy(CORE + ".extract..")
            .layer("export").definedBy(CORE + ".export..")
            .whereLayer("export").mayNotBeAccessedByAnyLayer()
            .whereLayer("extract").mayOnlyBeAccessedByLayers("export")
            .whereLayer("scan").mayOnlyBeAccessedByLayers("extract", "export")
            .whereLayer("port").mayOnlyBeAccessedByLayers("scan", "extract", "export")
            .whereLayer("tag").mayOnlyBeAccessedByLayers("port", "scan", "extract", "export")
            .whereLayer("pattern").mayOnlyBeAccessedByLayers("port", "scan", "extract", "export")
            .whereLayer("task").mayOnlyBeAccessedByLayers("port", "scan", "extract", "export")
            .whereLayer("model").mayOnlyBeAccessedByLayers("tag", "pattern", "task", "port", "scan", "extract",
                    "export")
            .whereLayer("text").mayOnlyBeAccessedByLayers("model", "tag", "pattern", "task", "port", "scan",
                    "extract", "export")
            .as("A3: core layering contract ← text ← model ← tag/pattern/task ← port ← scan ← extract ← export");

    // --- A4 / A5 / A6: shape of core types ------------------------------------------------------------------

    private static final Set<String> PORT_EXCEPTIONS = Set.of(
            CORE + ".port.MediaAccessException", CORE + ".port.StoreException");

    /** A4: core.port contains only interfaces, records, enums and the two exception classes. */
    public static final ArchRule A4_PORTS_ARE_INTERFACES_AND_VALUES = classes()
            .that().resideInAPackage(CORE + ".port..")
            .should(new ArchCondition<>("be an interface, record, enum or a port exception") {
                @Override
                public void check(JavaClass type, ConditionEvents events) {
                    boolean ok = type.isInterface() || type.isRecord() || type.isEnum()
                            || isSyntheticOrAnonymous(type) || PORT_EXCEPTIONS.contains(type.getName());
                    events.add(new SimpleConditionEvent(type, ok, type.getName() + " is not allowed in core.port"));
                }
            })
            .as("A4: core.port contains only interfaces, records, enums and the two exception classes");

    /** A5: value packages hold only records, enums, interfaces and final classes with static final fields. */
    public static final ArchRule A5_VALUE_PACKAGES_ARE_IMMUTABLE = classes()
            .that().resideInAnyPackage(CORE + ".model..", CORE + ".tag..", CORE + ".pattern..", CORE + ".task..")
            .should(new ArchCondition<>("be a record, enum, interface or final class with only static final fields") {
                @Override
                public void check(JavaClass type, ConditionEvents events) {
                    boolean ok = type.isRecord() || type.isEnum() || type.isInterface()
                            || isSyntheticOrAnonymous(type)
                            || type.getModifiers().contains(JavaModifier.FINAL)
                            && (onlyStaticFinalFields(type) || isAllowedStatefulTask(type));
                    events.add(new SimpleConditionEvent(type, ok, type.getName()
                            + " must be a record, enum, interface or final class with only static final fields"));
                }
            })
            .as("A5: model/tag/pattern/task classes are records, enums, interfaces or final classes with only "
                    + "static final fields");

    /** A6: all fields in core are final. */
    public static final ArchRule A6_CORE_FIELDS_ARE_FINAL = fields()
            .that().areDeclaredInClassesThat().resideInAPackage(CORE + "..")
            .should().beFinal()
            .as("A6: all fields in core are final");

    // --- A12: pure features --------------------------------------------------------------------------------

    /**
     * A feature package that must stay pure: no ports (no I/O) and no task primitives (no long-running work).
     *
     * @param packagePattern an ArchUnit package pattern, e.g. {@code "..core.extract.."}
     * @return the rule
     */
    public static ArchRule pureFeature(String packagePattern) {
        return noClasses()
                .that().resideInAPackage(packagePattern)
                .should().dependOnClassesThat().resideInAnyPackage(CORE + ".port..", CORE + ".task..")
                .as("A12: " + packagePattern + " is pure (no core.port, no core.task)");
    }

    /** A12: extraction (propose) is pure: it gets values, never ports. */
    public static final ArchRule A12_EXTRACT_IS_PURE = pureFeature(CORE + ".extract..");

    // --- A7: services ---------------------------------------------------------------------------------------

    /** A7: services are final, public, implement exactly one use case and are only used by core and app. */
    public static final ArchRule A7_SERVICES = classes()
            .that().haveSimpleNameEndingWith("Service").and().resideInAPackage(CORE + "..")
            .should().haveModifier(JavaModifier.FINAL)
            .andShould().bePublic()
            .andShould(new ArchCondition<>("implement exactly one *UseCase interface") {
                @Override
                public void check(JavaClass type, ConditionEvents events) {
                    long useCases = type.getRawInterfaces().stream()
                            .filter(i -> i.getSimpleName().endsWith("UseCase")).count();
                    events.add(new SimpleConditionEvent(type, useCases == 1,
                            type.getName() + " implements " + useCases + " *UseCase interfaces"));
                }
            })
            .andShould().onlyHaveDependentClassesThat().resideInAnyPackage(CORE + "..", ROOT + ".app..")
            .allowEmptyShould(true)
            .as("A7: *Service classes are final, public, implement one *UseCase and are only used by core and app");

    // --- A8 / A9: adapters and frontends --------------------------------------------------------------------

    /** A8: adapters depend on core and their library only – not on each other, app, frontends or services. */
    public static final ArchRule A8_ADAPTERS_ARE_INDEPENDENT = adapterIndependence();

    private static ArchRule adapterIndependence() {
        return classes()
                .that().resideInAnyPackage(ADAPTER_PACKAGES)
                .should(new ArchCondition<>("depend only on core, the JDK and their own library") {
                    @Override
                    public void check(JavaClass type, ConditionEvents events) {
                        String own = adapterRoot(type.getPackageName());
                        type.getDirectDependenciesFromSelf().forEach(dependency -> {
                            JavaClass target = dependency.getTargetClass().getBaseComponentType();
                            String pkg = target.getPackageName();
                            boolean violation = pkg.startsWith(ROOT + ".app") || pkg.startsWith(ROOT + ".cli")
                                    || pkg.startsWith(ROOT + ".gui")
                                    || target.getSimpleName().endsWith("Service") && pkg.startsWith(CORE)
                                    || isAdapter(pkg) && !pkg.startsWith(own);
                            if (violation) {
                                events.add(SimpleConditionEvent.violated(type, dependency.getDescription()));
                            }
                        });
                    }
                })
                .allowEmptyShould(true)
                .as("A8: adapters do not depend on each other, on app, cli, gui or on *Service");
    }

    /** A9: frontends use use-case interfaces and records only – no ports, services or adapters. */
    public static final ArchRule A9_FRONTENDS_USE_USE_CASES_ONLY = classes()
            .that().resideInAnyPackage(ROOT + ".cli..", ROOT + ".gui..")
            .should(new ArchCondition<>("not depend on ports, services or adapters") {
                @Override
                public void check(JavaClass type, ConditionEvents events) {
                    boolean guiMain = type.getName().equals(ROOT + ".gui.GuiMain");
                    type.getDirectDependenciesFromSelf().forEach(dependency -> {
                        JavaClass target = dependency.getTargetClass().getBaseComponentType();
                        String pkg = target.getPackageName();
                        boolean violation = pkg.startsWith(CORE + ".port")
                                || target.getSimpleName().endsWith("Service") && pkg.startsWith(CORE)
                                || isAdapter(pkg) && !(guiMain && pkg.startsWith(ROOT + ".player.javafx"));
                        if (violation) {
                            events.add(SimpleConditionEvent.violated(type, dependency.getDescription()));
                        }
                    });
                }
            })
            .allowEmptyShould(true)
            .as("A9: cli and gui do not depend on core.port, *Service or adapter packages");

    // --- A10 / A11: libraries and reflection ----------------------------------------------------------------

    /** A10: every library is used only in the module that wraps it. */
    public static final List<ArchRule> A10_LIBRARY_CONTAINMENT = List.of(
            contained("org.jaudiotagger..", ROOT + ".tag.jaudiotagger.."),
            contained("picocli..", ROOT + ".cli.."),
            contained("javafx..", ROOT + ".gui..", ROOT + ".player.javafx.."),
            contained("jakarta.json..", ROOT + ".storage.."),
            contained("org.eclipse.parsson..", ROOT + ".storage.."));

    private static ArchRule contained(String library, String... allowedIn) {
        return noClasses()
                .that().resideOutsideOfPackages(allowedIn)
                .should().dependOnClassesThat().resideInAPackage(library)
                .allowEmptyShould(true)
                .as("A10: " + library + " is only used in " + String.join(", ", allowedIn));
    }

    /** A11: no reflection in our code (native-image friendliness). */
    public static final ArchRule A11_NO_REFLECTION = noClasses()
            .should().dependOnClassesThat().resideInAPackage("java.lang.reflect..")
            .orShould().callMethod(Class.class, "forName", String.class)
            .orShould().callMethod(Class.class, "forName", String.class, boolean.class, ClassLoader.class)
            .orShould().callMethodWhere(describe("ClassLoader.loadClass or a MethodHandles.Lookup find method",
                    call -> call.getTargetOwner().isAssignableTo(ClassLoader.class)
                            && call.getName().equals("loadClass")
                            || call.getTargetOwner().getName().equals("java.lang.invoke.MethodHandles$Lookup")
                            && call.getName().startsWith("find")))
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.ServiceLoader")
            .allowEmptyShould(true)
            .as("A11: no class uses reflection, dynamic class loading or ServiceLoader");

    // --- helpers --------------------------------------------------------------------------------------------

    private static boolean isAdapter(String pkg) {
        for (String adapter : ADAPTER_PACKAGES) {
            String prefix = adapter.substring(0, adapter.length() - 2);
            if (pkg.equals(prefix) || pkg.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    private static String adapterRoot(String pkg) {
        // e.g. dev.rs.slava.icxc.musicstudio.tag.jaudiotagger.x → dev.rs.slava.icxc.musicstudio.tag.jaudiotagger
        String rest = pkg.substring(ROOT.length() + 1);
        String[] parts = rest.split("\\.");
        return parts.length >= 2 ? ROOT + "." + parts[0] + "." + parts[1] : ROOT + "." + parts[0];
    }

    private static boolean isSyntheticOrAnonymous(JavaClass type) {
        return type.getModifiers().contains(JavaModifier.SYNTHETIC) || type.isAnonymousClass();
    }

    private static boolean onlyStaticFinalFields(JavaClass type) {
        for (JavaField field : type.getFields()) {
            if (!field.getModifiers().containsAll(Set.of(JavaModifier.STATIC, JavaModifier.FINAL))) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code CancellationSource} is the one task primitive with (final, thread-safe) instance state; architecture
     * §12 defines it as a final class holding an AtomicBoolean.
     */
    private static boolean isAllowedStatefulTask(JavaClass type) {
        return type.getName().equals(CORE + ".task.CancellationSource");
    }
}
