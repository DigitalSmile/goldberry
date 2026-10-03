package dev.goldberry.build.assets;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

/**
 * What a module that bundles a pinned font, icon set or table asks of
 * {@code :assets}, as the {@code assetTool} extension that
 * {@code goldberry.asset-tool} adds.
 *
 * <p>The tool is ordinary Java with its own tests; a module only says which
 * entries of its manifest it ships and under which package, and gets two tasks:
 * {@code prepareAssets}, whose output is part of the module's resources, and
 * {@code vendorLicences}, run by hand to copy the upstream licence texts into
 * {@code licenses/}.
 *
 * <pre>{@code
 * assetTool.bundle('noto-emoji', 'dev/goldberry/emoji')
 * }</pre>
 *
 * <p>The archives are cached under {@code .gradle/assets} rather than in a build
 * directory: they are pinned by checksum, so they cannot go stale, and a
 * {@code clean} should not mean downloading 90 MB again.
 *
 * <p>Read more: <a href="https://goldberry.dev/docs/guide/text.html">Text</a>.
 */
public abstract class AssetTool {

    /** The extension's name. */
    public static final String EXTENSION = "assetTool";

    /** The configuration {@code :assets} is resolved through. */
    public static final String CONFIGURATION = "assetTool";

    /** The tool that fetches and prepares an entry. */
    public static final String PREPARE = "dev.goldberry.assets.prepare.PrepareAssets";

    /** The tool that compiles the showcase's category tables. */
    public static final String CATALOGS = "dev.goldberry.assets.prepare.PrepareCatalogs";

    private static final String GROUP = "assets";

    private final Configuration tool;
    private final SourceSetContainer sourceSets;

    /**
     * Made by the plugin; a build script reaches it as {@code assetTool}.
     *
     * @param tool       the configuration holding {@code :assets}
     * @param sourceSets the module's source sets
     */
    @Inject
    public AssetTool(Configuration tool, SourceSetContainer sourceSets) {
        this.tool = Objects.requireNonNull(tool, "tool");
        this.sourceSets = Objects.requireNonNull(sourceSets, "sourceSets");
    }

    /** @return the project's layout */
    @Inject
    protected abstract ProjectLayout getLayout();

    /** @return the project's tasks */
    @Inject
    protected abstract TaskContainer getTasks();

    /** @return file operations, for emptying an output before it is rewritten */
    @Inject
    protected abstract FileSystemOperations getFiles();

    /**
     * Bundles manifest entries as resources: {@code prepareAssets} writes them,
     * and the main source set's resources include what it wrote.
     *
     * @param only the entries, comma-separated: {@code inter,jetbrains-mono,lucide}
     * @param root the package directory they are written under, or {@code null}
     *             for the tool's own default
     * @return the {@code prepareAssets} task
     */
    public TaskProvider<JavaExec> bundle(String only, String root) {
        var output = getLayout().getBuildDirectory().dir("generated/assets");
        var prepare = register("prepareAssets", PREPARE,
                "Fetches the pinned " + only + " and prepares them as resources.",
                arguments(output.get().getAsFile(), null, only, root), output);
        sourceSets.getByName("main").getResources().srcDir(prepare.map(ignored -> output.get()));
        licences(only, root);
        return prepare;
    }

    /**
     * Only the licence texts, for a module whose resources come from elsewhere.
     *
     * @param only the entries whose licences to copy
     * @param root the package directory the tool would write under, or {@code null}
     * @return the {@code vendorLicences} task
     */
    public TaskProvider<JavaExec> licences(String only, String root) {
        var scratch = getLayout().getBuildDirectory().dir("generated/assets").get().getAsFile();
        var licenses = getLayout().getSettingsDirectory().dir("licenses").getAsFile();
        return register("vendorLicences", PREPARE,
                "Copies the licences of " + only + " into licenses/ from the pinned archives.",
                arguments(scratch, licenses, only, root), null);
    }

    /**
     * The showcase's category tables, compiled from the icon set's metadata and
     * Unicode's emoji groups, and bundled as resources.
     *
     * @param packageDirectory where under the resources they are written,
     *                         {@code dev/goldberry/example/catalog}
     * @return the {@code prepareCatalogs} task
     */
    public TaskProvider<JavaExec> catalogs(String packageDirectory) {
        var output = getLayout().getBuildDirectory().dir("generated/catalogs");
        var tables = output.get().dir(packageDirectory).getAsFile();
        var prepare = register("prepareCatalogs", CATALOGS,
                "Compiles the icon categories and emoji groups the showcase sheets are grouped by.",
                List.of(cache().getAbsolutePath(), tables.getAbsolutePath()), output);
        sourceSets.getByName("main").getResources().srcDir(prepare.map(ignored -> output.get()));
        return prepare;
    }

    /**
     * The tool's command line. Plain strings fixed at configuration time, as every
     * path in it is: an argument provider the build cannot fingerprint would make
     * the task out of date on every run, and preparing the icons is not cheap.
     */
    private List<String> arguments(File output, File licenses, String only, String root) {
        var arguments = new ArrayList<String>();
        arguments.add(cache().getAbsolutePath());
        arguments.add(output.getAbsolutePath());
        if (licenses != null) {
            arguments.add(licenses.getAbsolutePath());
        }
        arguments.add("--only=" + only);
        if (root != null) {
            arguments.add("--root=" + root);
        }
        return List.copyOf(arguments);
    }

    private File cache() {
        return getLayout().getSettingsDirectory().dir(".gradle/assets").getAsFile();
    }

    private TaskProvider<JavaExec> register(
            String name, String mainClass, String description, List<String> arguments,
            Provider<Directory> output) {
        var files = getFiles();
        return getTasks().register(name, JavaExec.class, new Action<>() {
            @Override
            public void execute(JavaExec task) {
                task.setGroup(GROUP);
                task.setDescription(description);
                task.setClasspath(tool);
                task.getMainClass().set(mainClass);
                task.setArgs(arguments);
                if (output != null) {
                    // The tool is the input: change the manifest -- a version, a
                    // checksum, the list of entries -- and this reruns. The cache
                    // directory deliberately is not.
                    task.getInputs().files(tool);
                    task.getOutputs().dir(output);
                    // Emptied first, so a file this build no longer prepares does
                    // not survive in it: a face that moved to another module kept
                    // shipping from an incremental build until this was here.
                    task.doFirst(new Action<>() {
                        @Override
                        public void execute(Task ignored) {
                            files.delete(spec -> spec.delete(output));
                        }
                    });
                }
            }
        });
    }
}
