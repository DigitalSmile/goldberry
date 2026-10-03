package dev.goldberry.build.testing;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaForkOptions;

import dev.goldberry.build.benchmark.BenchmarkLane;
import dev.goldberry.build.natives.NativeTarget;

/**
 * What a module whose tests load libgoldberry asks of the build, as the
 * {@code nativeTests} extension that {@code goldberry.native-tests} adds.
 *
 * <p>Every {@link Test} task of such a module, {@code benchmark} included, is
 * wired by {@link #wire} when the plugin is applied: JEP 472's grant, the
 * {@linkplain ForwardedProperties forwarded properties}, the library to load, the
 * library as an input, and the native build it waits for. The GPU lane and the
 * measurement probes are asked for by name, because only some modules have them:
 *
 * <pre>{@code
 * nativeTests.gpuTests('dev.goldberry.gpu')
 * nativeTests.probe('gpuVideoProbe', 'dev.goldberry.gpu.render.GpuVideoProbe', 'Measures ...')
 * }</pre>
 *
 * <p>Read more: <a href="https://goldberry.dev/docs/contributing/testing.html#with-and-without-the-library">With
 * and without the library</a>.
 */
public abstract class NativeTests {

    /** The extension's name. */
    public static final String EXTENSION = "nativeTests";

    /** The library a test loads; a test JVM reads it as a system property. */
    public static final String LIBRARY_PROPERTY = "goldberry.native.library";

    /** {@code -Pgoldberry.skipNative=true}: a Java-only build, whose rendering tests skip. */
    public static final String SKIP_PROPERTY = "goldberry.skipNative";

    /** The task that builds libgoldberry for this machine. */
    public static final String NATIVE_BUILD = ":natives:cmakeBuild";

    /** The JUnit tag of a test that needs a GPU device. */
    public static final String GPU_TAG = "gpu";

    /** Runs the {@link #GPU_TAG} tests on the JVM's first thread; one of {@code :natives}' test fixtures. */
    public static final String GPU_LAUNCHER = "dev.goldberry.natives.sdl.gpu.GpuTestLauncher";

    /** JEP 472, for the unnamed module whitebox tests run in. */
    public static final String NATIVE_ACCESS = "--enable-native-access=ALL-UNNAMED";

    /** What AppKit needs of a JVM that opens a window or a Metal device. */
    public static final String FIRST_THREAD = "-XstartOnFirstThread";

    private final NativeTarget host;
    private final SourceSetContainer sourceSets;

    /**
     * Made by the plugin; a build script reaches it as {@code nativeTests}.
     *
     * @param host       the target this machine builds
     * @param sourceSets the module's source sets
     */
    @Inject
    public NativeTests(NativeTarget host, SourceSetContainer sourceSets) {
        this.host = Objects.requireNonNull(host, "host");
        this.sourceSets = Objects.requireNonNull(sourceSets, "sourceSets");
    }

    /** @return Gradle's provider factory */
    @Inject
    protected abstract ProviderFactory getProviders();

    /** @return the project's layout */
    @Inject
    protected abstract ProjectLayout getLayout();

    /** @return the project's tasks */
    @Inject
    protected abstract TaskContainer getTasks();

    /**
     * The target this machine builds natively.
     *
     * @return the host's row of the matrix
     */
    public NativeTarget getHost() {
        return host;
    }

    /**
     * A library named on the command line, {@code -D} or {@code -P}. CI's verify
     * jobs name the one they downloaded, and naming one is also what tells the
     * build not to make a rival.
     *
     * @return the path given, if any
     */
    public Optional<String> getExplicitLibrary() {
        return Optional.ofNullable(property(LIBRARY_PROPERTY));
    }

    /**
     * The library a test JVM loads: the one named, or else the one this machine's
     * {@code :natives:cmakeBuild} makes. Set whether or not that file exists; a
     * test decides at run time, and skips, when it does not.
     *
     * @return an absolute path
     */
    public String getLibraryPath() {
        return getExplicitLibrary().orElseGet(() -> host.localLibrary(
                        getLayout().getSettingsDirectory().getAsFile().toPath())
                .toString());
    }

    /**
     * Whether this build makes libgoldberry before its tests: not when
     * {@code -Pgoldberry.skipNative=true} asks for a Java-only build, and not when
     * a library was handed over to be verified.
     *
     * @return {@code true} when the tests wait for {@link #NATIVE_BUILD}
     */
    public boolean buildsLibrary() {
        return !Boolean.parseBoolean(property(SKIP_PROPERTY)) && getExplicitLibrary().isEmpty();
    }

    /**
     * Wires a JVM that loads libgoldberry: the grant, the forwarded properties,
     * the library and its build.
     *
     * @param task a {@link Test} or a {@link JavaExec}
     * @param <T>  the task's type
     */
    public <T extends Task & JavaForkOptions> void wire(T task) {
        task.jvmArgs(NATIVE_ACCESS);
        var providers = getProviders();
        ForwardedProperties.resolve(
                        ForwardedProperties.TO_TESTS,
                        name -> Optional.ofNullable(providers.systemProperty(name).getOrNull()),
                        name -> Optional.ofNullable(providers.gradleProperty(name).getOrNull()))
                .forEach(task::systemProperty);
        var library = getLibraryPath();
        task.systemProperty(LIBRARY_PROPERTY, library);
        // An input and not merely something built first: `dependsOn` orders the
        // two, and a rebuilt library would otherwise leave every test that paints
        // through it up to date and green against a library it never saw.
        var file = new File(library);
        task.getInputs()
                .files(providers.provider(() -> file.isFile() ? List.of(file) : List.of()))
                .withPropertyName("nativeLibrary")
                .optional();
        if (buildsLibrary()) {
            task.dependsOn(NATIVE_BUILD);
        }
    }

    /**
     * The GPU lane: the tests tagged {@link #GPU_TAG}, left out of {@code test}
     * and run by {@code gpuTest} on the JVM's first thread, which macOS's Cocoa
     * needs for a Metal device. {@code check} runs it; without a device its tests
     * skip, unless {@code -Pgoldberry.gpu.required=true} says the lane has one.
     *
     * <p>Read more: <a href="https://goldberry.dev/docs/contributing/testing.html#the-gpu-tests">The GPU
     * tests</a>.
     *
     * @param testPackage the package whose tagged tests run, {@code dev.goldberry.gpu}
     * @return the {@code gpuTest} task, for a module to configure further
     */
    public TaskProvider<JavaExec> gpuTests(String testPackage) {
        Objects.requireNonNull(testPackage, "testPackage");
        getTasks().named("test", Test.class).configure(new Action<>() {
            @Override
            public void execute(Test test) {
                test.useJUnitPlatform(options -> options.excludeTags(GPU_TAG));
            }
        });
        var summary = getLayout().getBuildDirectory().file("gpu-test/summary.txt");
        var gpuTest = getTasks().register("gpuTest", JavaExec.class, new Action<>() {
            @Override
            public void execute(JavaExec task) {
                task.setGroup("verification");
                task.setDescription("Runs the GPU tests on the JVM's first thread, as macOS needs.");
                var classpath = sourceSets.getByName("test").getRuntimeClasspath();
                task.setClasspath(classpath);
                task.getMainClass().set(GPU_LAUNCHER);
                wire(task);
                if (host.needsFirstThread()) {
                    task.jvmArgs(FIRST_THREAD);
                }
                task.getInputs().files(classpath).withPropertyName("testClasspath");
                task.getOutputs().file(summary);
                task.getArgumentProviders().add(new GpuLaneArguments(summary, testPackage));
            }
        });
        getTasks().named("check").configure(new Action<>() {
            @Override
            public void execute(Task check) {
                check.dependsOn(gpuTest);
            }
        });
        return gpuTest;
    }

    /**
     * A measurement run on purpose and read by a person: a {@code main} in the
     * benchmark source set that opens a real window or device and prints what it
     * cost. Never part of {@code check}, and never up to date.
     *
     * <p>Read more: <a href="https://goldberry.dev/docs/performance/measuring.html#the-benchmark-lane">The
     * benchmark lane</a>.
     *
     * @param name        the task's name, {@code gpuVideoProbe}
     * @param mainClass   the probe's class
     * @param description what it measures
     * @return the task, for a module to configure further
     */
    public TaskProvider<JavaExec> probe(String name, String mainClass, String description) {
        Objects.requireNonNull(mainClass, "mainClass");
        return getTasks().register(name, JavaExec.class, new Action<>() {
            @Override
            public void execute(JavaExec task) {
                task.setGroup(BenchmarkLane.GROUP);
                task.setDescription(description);
                task.setClasspath(sourceSets.getByName(BenchmarkLane.SOURCE_SET).getRuntimeClasspath());
                task.getMainClass().set(mainClass);
                wire(task);
                if (host.needsFirstThread()) {
                    task.jvmArgs(FIRST_THREAD);
                }
                task.getOutputs().upToDateWhen(ignored -> false);
            }
        });
    }

    private String property(String name) {
        var providers = getProviders();
        return providers.systemProperty(name).orElse(providers.gradleProperty(name)).getOrNull();
    }

    /** The launcher's two arguments: where to write its summary, and which package to run. */
    private static final class GpuLaneArguments implements CommandLineArgumentProvider {

        private final Provider<RegularFile> summary;
        private final String testPackage;

        GpuLaneArguments(Provider<RegularFile> summary, String testPackage) {
            this.summary = summary;
            this.testPackage = testPackage;
        }

        @Internal
        public Provider<RegularFile> getSummary() {
            return summary;
        }

        @Input
        public String getTestPackage() {
            return testPackage;
        }

        @Override
        public Iterable<String> asArguments() {
            return List.of(summary.get().getAsFile().getAbsolutePath(), testPackage);
        }
    }
}
