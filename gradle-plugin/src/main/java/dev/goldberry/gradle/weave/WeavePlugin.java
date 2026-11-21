package dev.goldberry.gradle.weave;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * {@code dev.goldberry.weave}: the weaver, run over an application's compiled
 * classes the way Goldberry's own build runs it over its modules.
 *
 * <pre>{@code
 * plugins {
 *     id 'java'
 *     id 'dev.goldberry.weave' version '2026.2'
 * }
 * }</pre>
 *
 * <p>The weaver does two unrelated things, wanted at different times:
 *
 * <ul>
 *   <li><b>catalog</b>: collects the module's {@code @Markup} widgets into a
 *       {@code WidgetCatalog} and declares it. Every build of a module with its
 *       own markup widgets needs this, because nothing finds annotated classes at
 *       run time, so {@code weaveCatalog} runs before {@code classes} and
 *       {@code weaveTestCatalog} before {@code testClasses}.</li>
 *   <li><b>models</b>: rewires {@code @Bind} fields and writes the {@code @Action}
 *       call sites. Only a native image needs this; a jar binds the same
 *       annotations at run time. {@code weaveModels} is registered always and
 *       runs when asked for, or before {@code classes} when
 *       {@code goldberry.nativeImage} is on.</li>
 * </ul>
 *
 * <p>Both run in a JVM of their own on the project's Java toolchain, because the
 * weaver needs that JDK's class-file API rather than the Gradle daemon's.
 */
public final class WeavePlugin implements Plugin<Project> {

    /** The configuration the weaver is resolved through. */
    public static final String CONFIGURATION = "goldberryWeaver";

    /** The weaver's coordinates, without the version. */
    public static final String WEAVER = "dev.goldberry:goldberry-weaver";

    /** The weaver's entry point. */
    static final String MAIN_CLASS = "dev.goldberry.weaver.WeaverMain";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        var extension = project.getExtensions().create(GoldberryWeaveExtension.NAME, GoldberryWeaveExtension.class);
        extension.getWeaverVersion().convention(pluginVersion());
        extension.getNativeImage().convention(project.getProviders()
                .gradleProperty("goldberry.nativeImage")
                .map(value -> value.isEmpty() || Boolean.parseBoolean(value))
                .orElse(false));

        var weaver = project.getConfigurations().create(CONFIGURATION, configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.setDescription("The Goldberry weaver, run over this module's compiled classes.");
        });
        weaver.defaultDependencies(dependencies -> dependencies.add(project.getDependencies()
                .create(WEAVER + ":" + extension.getWeaverVersion().get())));

        var tasks = project.getTasks();
        var sets = Map.of(
                SourceSet.MAIN_SOURCE_SET_NAME, JavaPlugin.COMPILE_JAVA_TASK_NAME,
                SourceSet.TEST_SOURCE_SET_NAME, JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME);
        for (var set : List.of(SourceSet.MAIN_SOURCE_SET_NAME, SourceSet.TEST_SOURCE_SET_NAME)) {
            var main = set.equals(SourceSet.MAIN_SOURCE_SET_NAME);
            var compile = sets.get(set);
            var catalog = register(
                    project,
                    weaver,
                    main ? "weaveCatalog" : "weaveTestCatalog",
                    set,
                    compile,
                    "--catalog",
                    "Collects the @Markup widgets into a WidgetCatalog and declares it");
            var models = register(
                    project,
                    weaver,
                    main ? "weaveModels" : "weaveTestModels",
                    set,
                    compile,
                    "--models",
                    "Rewires @Bind fields into bindings");
            // Two JVMs writing into one directory at once is a corrupt class file.
            models.configure(task -> task.mustRunAfter(catalog));
            var lifecycle = main ? JavaPlugin.CLASSES_TASK_NAME : "testClasses";
            tasks.named(lifecycle).configure(task -> {
                task.dependsOn(catalog);
                task.dependsOn(extension.getNativeImage().map(on -> on ? List.of(models) : List.of()));
            });
            if (main) {
                // The jar packages woven classes when both are in the graph.
                tasks.named(JavaPlugin.JAR_TASK_NAME).configure(task -> task.mustRunAfter(models));
            }
        }
    }

    /** One weaving task over a compiled source set, running the half `flag` names. */
    private static TaskProvider<JavaExec> register(
            Project project,
            Configuration weaver,
            String name,
            String set,
            String compileTask,
            String flag,
            String what) {
        var java = project.getExtensions().getByType(JavaPluginExtension.class);
        var toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        return project.getTasks().register(name, JavaExec.class, task -> {
            task.setGroup("build");
            task.setDescription(what + " in the compiled " + set + " classes.");
            var compile = project.getTasks().named(compileTask, JavaCompile.class);
            task.dependsOn(compile);
            Provider<Directory> classes = compile.flatMap(JavaCompile::getDestinationDirectory);
            task.getJavaLauncher().set(toolchains.launcherFor(java.getToolchain()));
            // The weaver first, then the classes it rewrites and what they were
            // compiled against: regenerating a stack map frame resolves the
            // author's own types.
            task.setClasspath(project.files(
                    weaver, classes, java.getSourceSets().named(set).map(SourceSet::getCompileClasspath)));
            task.getMainClass().set(MAIN_CLASS);
            task.getArgumentProviders().add((CommandLineArgumentProvider) () -> List.of(
                    flag, classes.get().getAsFile().getAbsolutePath()));
            // Weaving is in place and idempotent, so it never claims to be up to
            // date; the classes directory is an input and NOT an output, or
            // Gradle would see two writers to javac's directory and throw away
            // the compiler's incremental state. A stamp is the output instead.
            var stamp = project.getLayout().getBuildDirectory().file("tmp/" + name + "/stamp");
            task.getInputs().dir(classes).withPathSensitivity(PathSensitivity.RELATIVE);
            task.getOutputs().file(stamp);
            task.getOutputs().upToDateWhen(ignored -> false);
            task.doLast(new StampWriter(stamp, what));
        });
    }

    /** The version this plugin was released as, which names its weaver. */
    static String pluginVersion() {
        try (InputStream in = WeavePlugin.class.getResourceAsStream("version.txt")) {
            if (in == null) {
                throw new IllegalStateException("the plugin jar has no version.txt");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes the task's stamp, as a class so the configuration cache keeps it. */
    private record StampWriter(Provider<RegularFile> stamp, String what) implements Action<Task> {
        @Override
        public void execute(Task task) {
            var file = stamp.get().getAsFile();
            try {
                Files.createDirectories(file.getParentFile().toPath());
                Files.writeString(file.toPath(), "woven " + what.toLowerCase(Locale.ROOT));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
