package dev.rs.slava.icxc.musicstudio.core.testkit.arch;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.Architectures.LayeredArchitecture;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The architecture rules of docs/architecture.md section 14.4, defined once. Each module has a small
 * {@code ArchitectureTest} that applies the rules relevant to it. Rules are never weakened to make code compile.
 *
 * <p>Analysed package: {@link #ROOT}; test classes are excluded by the importing test
 * ({@code ImportOption.DoNotIncludeTests}). Rules about packages that do not exist yet allow empty selections.
 */
public final class ArchitectureRules {

    public static final String ROOT = "dev.rs.slava.icxc.musicstudio";
    private static final String CORE = ROOT + ".core..";
    private static final String CORE_PORT = ROOT + ".core.port..";
    private static final String[] ADAPTERS = {ROOT + ".tag..", ROOT + ".storage..", ROOT + ".image..", ROOT + ".player.."};
    private static final String[] FRONTENDS = {ROOT + ".cli..", ROOT + ".gui.."};

    /** A class of ours whose name ends with "Service" (not {@code ExecutorService} and the like). */
    private static final DescribedPredicate<JavaClass> OUR_SERVICE =
            resideInAPackage(ROOT + "..").and(simpleNameEndingWith("Service"))
                    .as("a *Service class of Music-Studio");

    private ArchitectureRules() {}

    /** A1: core depends only on core and whitelisted JDK packages. */
    public static final ArchRule A1_CORE_DEPENDS_ONLY_ON_WHITELIST = classes().that().resideInAPackage(CORE)
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    CORE, "java.lang", "java.lang.annotation", "java.lang.invoke", "java.lang.runtime",
                    "java.util..", "java.time..", "java.text", "java.nio.file", "java.nio.charset", "java.math")
            .allowEmptyShould(true);

    /** A2: core does no I/O and uses no logging, reflection, console or class loading. */
    public static final ArchRule A2_CORE_DOES_NO_IO = noClasses().that().resideInAPackage(CORE)
            .should().dependOnClassesThat().belongToAnyOf(Files.class, File.class, RandomAccessFile.class,
                    FileInputStream.class, FileOutputStream.class, FileReader.class, FileWriter.class)
            .orShould().dependOnClassesThat().resideInAnyPackage("java.nio.channels..", "java.net..", "java.awt..",
                    "javax..", "java.sql..", "java.util.logging..", "java.lang.reflect..")
            .orShould().callMethod(Class.class, "forName", String.class)
            .orShould().callMethod(Class.class, "forName", String.class, boolean.class, ClassLoader.class)
            .orShould().accessField(System.class, "out")
            .orShould().accessField(System.class, "err")
            .orShould().callMethod(Path.class, "toFile")
            .orShould().callMethod(Path.class, "toRealPath", LinkOption[].class)
            .allowEmptyShould(true);

    /** A3: core layering contract <- text <- model <- tag/pattern/task <- port <- scan <- extract <- export <- ... */
    public static final ArchRule A3_CORE_LAYERING = layering();

    /** A4: core.port contains only interfaces, records, enums and the two exception classes. */
    public static final ArchRule A4_PORT_CONTAINS_ONLY_CONTRACTS = classes().that().resideInAPackage(CORE_PORT)
            .and().doNotHaveSimpleName("MediaAccessException").and().doNotHaveSimpleName("StoreException")
            .should().beInterfaces().orShould().beRecords().orShould().beEnums()
            .allowEmptyShould(true);

    /** A5: in model, tag and pattern every class is a record, enum, interface, or final class with static fields. */
    public static final ArchRule A5_VALUE_PACKAGES_ARE_IMMUTABLE_SHAPES = classes().that()
            .resideInAnyPackage(ROOT + ".core.model..", ROOT + ".core.tag..", ROOT + ".core.pattern..")
            .should(beRecordEnumInterfaceOrStaticFinalClass()).allowEmptyShould(true);

    /**
     * A5 for {@code core.task}: records, enums, interfaces or final classes. A final class may hold final instance
     * fields, because {@code CancellationSource} wraps an {@code AtomicBoolean} (architecture section 12).
     */
    public static final ArchRule A5_TASK_CLASSES_ARE_FINAL = classes().that().resideInAPackage(ROOT + ".core.task..")
            .should().beInterfaces().orShould().beRecords().orShould().beEnums().orShould().haveModifier(JavaModifier.FINAL)
            .allowEmptyShould(true);

    /** A6: all fields in core are final. */
    public static final ArchRule A6_CORE_FIELDS_ARE_FINAL = fields().that().areDeclaredInClassesThat()
            .resideInAPackage(CORE).should().beFinal().allowEmptyShould(true);

    /** A7: *Service classes are final, public, implement exactly one *UseCase, and are referenced only by core and app. */
    public static final ArchRule A7_SERVICES = CompositeArchRule
            .of(classes().that().haveSimpleNameEndingWith("Service").and().resideInAPackage(ROOT + "..")
                    .should().haveModifier(JavaModifier.FINAL).andShould().haveModifier(JavaModifier.PUBLIC)
                    .andShould(implementExactlyOneUseCase()).allowEmptyShould(true))
            .and(noClasses().that().resideOutsideOfPackages(CORE, ROOT + ".app..")
                    .should().dependOnClassesThat(OUR_SERVICE).allowEmptyShould(true));

    /** A8: adapters do not depend on each other, on app, cli, gui or on any *Service. */
    public static final ArchRule A8_ADAPTERS_ARE_ISOLATED = CompositeArchRule
            .of(noClasses().that().resideInAnyPackage(ADAPTERS)
                    .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".app..", ROOT + ".cli..", ROOT + ".gui..")
                    .orShould().dependOnClassesThat(OUR_SERVICE).allowEmptyShould(true))
            .and(classes().that().resideInAnyPackage(ADAPTERS).should(notDependOnAnotherAdapter())
                    .allowEmptyShould(true));

    /** A9: cli and gui do not depend on core.port, *Service or adapters (exception: GuiMain -> player.javafx). */
    public static final ArchRule A9_FRONTENDS_USE_ONLY_USE_CASES = CompositeArchRule
            .of(noClasses().that().resideInAnyPackage(FRONTENDS)
                    .should().dependOnClassesThat().resideInAnyPackage(CORE_PORT)
                    .orShould().dependOnClassesThat(OUR_SERVICE)
                    .orShould().dependOnClassesThat().resideInAnyPackage(ROOT + ".tag..", ROOT + ".storage..",
                            ROOT + ".image..")
                    .allowEmptyShould(true))
            .and(noClasses().that().resideInAnyPackage(FRONTENDS).and().doNotHaveFullyQualifiedName(ROOT + ".gui.GuiMain")
                    .should().dependOnClassesThat().resideInAPackage(ROOT + ".player..").allowEmptyShould(true));

    /** A10: library containment. */
    public static final ArchRule A10_LIBRARY_CONTAINMENT = CompositeArchRule
            .of(noClasses().that().resideOutsideOfPackage(ROOT + ".tag.jaudiotagger..")
                    .should().dependOnClassesThat().resideInAPackage("org.jaudiotagger..").allowEmptyShould(true))
            .and(noClasses().that().resideOutsideOfPackage(ROOT + ".cli..")
                    .should().dependOnClassesThat().resideInAPackage("picocli..").allowEmptyShould(true))
            .and(noClasses().that().resideOutsideOfPackages(ROOT + ".gui..", ROOT + ".player.javafx..")
                    .should().dependOnClassesThat().resideInAPackage("javafx..").allowEmptyShould(true))
            .and(noClasses().that().resideOutsideOfPackage(ROOT + ".storage..")
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.json..", "org.eclipse.parsson..")
                    .allowEmptyShould(true));

    /** A11: no reflection and no dynamic class loading in our code (native-image friendliness). */
    public static final ArchRule A11_NO_REFLECTION = noClasses().that().resideInAPackage(ROOT + "..")
            .should().dependOnClassesThat().resideInAPackage("java.lang.reflect..")
            .orShould().callMethod(Class.class, "forName", String.class)
            .orShould().callMethod(Class.class, "forName", String.class, boolean.class, ClassLoader.class)
            .allowEmptyShould(true);

    private static ArchRule layering() {
        String contract = "contract";
        List<List<String>> levels = List.of(
                List.of(contract), List.of("text"), List.of("model"), List.of("tag", "pattern", "task"),
                List.of("port"), List.of("scan"), List.of("extract"), List.of("export"), List.of("cleanup"),
                List.of("profile"), List.of("preset"), List.of("genre"), List.of("cover"), List.of("library"),
                List.of("playback"));
        LayeredArchitecture architecture = layeredArchitecture().consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true);
        for (List<String> level : levels) {
            for (String layer : level) {
                architecture = architecture.layer(layer).definedBy(ROOT + ".core." + layer + "..");
            }
        }
        for (int i = 0; i < levels.size(); i++) {
            List<String> above = new ArrayList<>();
            for (int j = i + 1; j < levels.size(); j++) {
                above.addAll(levels.get(j));
            }
            for (String layer : levels.get(i)) {
                architecture = above.isEmpty()
                        ? architecture.whereLayer(layer).mayNotBeAccessedByAnyLayer()
                        : architecture.whereLayer(layer).mayOnlyBeAccessedByLayers(above.toArray(String[]::new));
            }
        }
        return architecture.allowEmptyShould(true);
    }

    private static ArchCondition<JavaClass> beRecordEnumInterfaceOrStaticFinalClass() {
        return new ArchCondition<>("be a record, enum, interface, or final class with only static final fields") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (javaClass.getModifiers().contains(JavaModifier.SYNTHETIC)
                        || javaClass.isInterface() || javaClass.isEnum() || javaClass.isRecord()) {
                    return;
                }
                boolean isFinal = javaClass.getModifiers().contains(JavaModifier.FINAL);
                List<String> offenders = new ArrayList<>();
                for (JavaField field : javaClass.getFields()) {
                    if (!field.getModifiers().contains(JavaModifier.STATIC)
                            || !field.getModifiers().contains(JavaModifier.FINAL)) {
                        offenders.add(field.getName());
                    }
                }
                if (!isFinal || !offenders.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName()
                            + " is neither record/enum/interface nor a final class with only static final fields"
                            + (offenders.isEmpty() ? "" : " (fields: " + offenders + ")")));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> implementExactlyOneUseCase() {
        return new ArchCondition<>("implement exactly one *UseCase interface") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                long count = javaClass.getRawInterfaces().stream()
                        .filter(i -> i.getSimpleName().endsWith("UseCase")).count();
                if (count != 1) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getName() + " implements " + count + " *UseCase interfaces instead of exactly one"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notDependOnAnotherAdapter() {
        return new ArchCondition<>("not depend on a different adapter") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String own = adapterOf(javaClass.getPackageName());
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    String other = adapterOf(dependency.getTargetClass().getPackageName());
                    if (other != null && !other.equals(own)) {
                        events.add(SimpleConditionEvent.violated(javaClass, dependency.getDescription()));
                    }
                }
            }
        };
    }

    /** @return the adapter family ("tag.jaudiotagger", "storage", "player.javafx", ...) or null if not an adapter */
    private static String adapterOf(String packageName) {
        String prefix = ROOT + ".";
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        String[] parts = packageName.substring(prefix.length()).split("\\.");
        return switch (parts[0]) {
            case "tag", "player", "image" -> parts.length > 1 ? parts[0] + "." + parts[1] : parts[0];
            case "storage" -> "storage";
            default -> null;
        };
    }

    /** Predicate used by self-tests. */
    static DescribedPredicate<JavaClass> notCore() {
        return not(resideInAPackage(CORE));
    }
}
