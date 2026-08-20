package io.github.digitalsmile.goldberry.weaver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Weaves a directory of compiled classes, in place.
///
/// What the `goldberry.weave` Gradle tasks run, between `compileJava` and `jar`.
/// A plain `main` rather than a Gradle plugin class, so the weaver is a program
/// with an argument and an exit code — runnable from any build, from a script,
/// and from a terminal when somebody wants to see what it did to one class.
///
/// ```
/// java -jar goldberry-weaver.jar build/classes/java/main
/// java -jar goldberry-weaver.jar --catalog build/classes/java/main
/// java -jar goldberry-weaver.jar --models  build/classes/java/main
/// ```
///
/// ## Two halves, and they are wanted at different times
///
/// The weaver does two unrelated jobs to the same tree, and since
/// [ADR-0155](../../../../book/src/adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md)
/// a build asks for them separately:
///
/// - `--models` rewires `@Bind` fields and writes the `@Action` call sites. Only
///   a **native image** needs it: an ordinary jar binds the same annotations at
///   run time, so running this over a jar buys speed and costs a build step
///   (ADR-0125, ADR-0127).
/// - `--catalog` writes the module's `WidgetCatalog` from its `@Markup` widgets
///   and declares it. **Every** build needs it, image or not: there is no runtime
///   equivalent — finding annotated classes at run time would mean scanning the
///   path, which is the thing a `provides` exists to avoid (ADR-0131).
///
/// Neither flag means both, which is what a build that wants the lot writes and
/// what every pre-0155 integration already wrote.
///
/// ## It rewrites in place, and says what it touched
///
/// A class the requested halves have nothing to do to is not rewritten at all —
/// not re-serialised, not touched — so the task is incremental in the only way
/// that matters and a build that changes nothing changes no timestamps.
public final class WeaverMain {

    private WeaverMain() {
    }

    /// @param args `--models`, `--catalog`, or neither, then one or more
    ///        directories of compiled classes
    public static void main(String[] args) {
        var models = false;
        var catalog = false;
        var roots = new ArrayList<String>();
        for (var argument : args) {
            switch (argument) {
                case "--models" -> models = true;
                case "--catalog" -> catalog = true;
                default -> {
                    if (argument.startsWith("--")) {
                        System.err.println("goldberry: unknown option " + argument);
                        System.exit(2);
                        return;
                    }
                    roots.add(argument);
                }
            }
        }
        if (roots.isEmpty()) {
            System.err.println("usage: WeaverMain [--models] [--catalog] <classes-dir>...");
            System.exit(2);
            return;
        }
        // Neither named is both named: the flags narrow, and a build that says
        // nothing gets what the weaver always did.
        if (!models && !catalog) {
            models = true;
            catalog = true;
        }
        var woven = new ArrayList<String>();
        try {
            for (var argument : roots) {
                weaveTree(Path.of(argument), woven, models, catalog);
            }
        } catch (WeaveException e) {
            // The message names the member and says what is wrong with it. A
            // stack trace through the class-file API would bury that.
            System.err.println("goldberry: " + e.getMessage());
            System.exit(1);
            return;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (var name : woven) {
            System.out.println("goldberry: wove " + name);
        }
    }

    /// Weaves every class under `root`, collecting the names it changed.
    ///
    /// Two passes. The first only asks which classes are models, because one
    /// rule needs to know about the others: a `@Model` extending a `@Model`
    /// breaks silently and has to be refused, and that cannot be seen from
    /// either class alone.
    ///
    /// @throws WeaveException if any `@Model` below it is one the toolkit refuses
    public static void weaveTree(Path root, List<String> woven) throws IOException {
        weaveTree(root, woven, true, true);
    }

    /// The same, doing only the halves asked for.
    ///
    /// @param models whether to rewire `@Bind` fields and write `@Action` call
    ///        sites — what a native image needs and a jar does not (ADR-0155)
    /// @param catalog whether to write the module's `WidgetCatalog` — what every
    ///        build needs, because nothing finds `@Markup` widgets at run time
    /// @throws WeaveException if any `@Model` below it is one the toolkit refuses
    public static void weaveTree(Path root, List<String> woven, boolean models, boolean catalog)
            throws IOException {

        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> classes;
        try (var tree = Files.walk(root)) {
            classes = tree.filter(p -> p.toString().endsWith(".class")).toList();
        }
        var rewired = new java.util.LinkedHashMap<String, ModelWeaver.Rewired>();
        var widgets = new java.util.LinkedHashMap<String, java.lang.constant.ClassDesc>();
        java.nio.file.Path descriptor = null;
        for (var file : classes) {
            var bytes = Files.readAllBytes(file);
            if (file.getFileName().toString().equals("module-info.class")) {
                descriptor = file;
                continue;
            }
            var model = models ? ModelWeaver.rewired(bytes) : null;
            if (model != null) {
                rewired.put(model.owner().descriptorString()
                        .substring(1, model.owner().descriptorString().length() - 1), model);
            }
            var node = catalog ? CatalogWeaver.markupName(bytes) : null;
            if (node != null) {
                var widget = java.lang.classfile.ClassFile.of().parse(bytes).thisClass().asSymbol();
                var clash = widgets.put(node, widget);
                if (clash != null) {
                    throw new WeaveException("both " + clash.displayName() + " and "
                            + widget.displayName() + " claim the markup name \"" + node + "\";"
                            + " a document writing it would get whichever the build saw last");
                }
            }
        }
        // Which models are written to from outside their own nest, so their
        // setters -- and only theirs -- have to open up to the package. A model
        // whose actions are a nested class keeps everything private (ADR-0137).
        var open = new java.util.HashSet<String>();
        if (models) {
            for (var file : classes) {
                var bytes = Files.readAllBytes(file);
                var host = ModelWeaver.nestHost(bytes);
                for (var written : ModelWeaver.modelsWrittenBy(bytes, rewired)) {
                    if (!ModelWeaver.nestHost(Files.readAllBytes(fileOf(root, written)))
                            .equals(host)) {
                        open.add(written);
                    }
                }
            }
            for (var file : classes) {
                weaveFile(file, rewired, open, woven);
            }
        }
        if (catalog) {
            writeCatalog(root, descriptor, widgets, woven);
        }
    }

    /// Writes the module's [WidgetCatalog] and declares it.
    ///
    /// Three artefacts, because a jar has to work in both worlds: the catalog
    /// class itself, a `provides` patched into `module-info.class` for the module
    /// path, and a `META-INF/services` entry for the class path. A module with no
    /// `@Markup` widget gets none of them.
    private static void writeCatalog(Path root, Path descriptor,
            java.util.Map<String, java.lang.constant.ClassDesc> widgets, List<String> woven)
            throws IOException {

        if (widgets.isEmpty()) {
            return;
        }
        var ordered = CatalogWeaver.sorted(widgets);
        var pkg = CatalogWeaver.rootPackage(List.copyOf(ordered.values()));
        var bytes = CatalogWeaver.catalog(pkg, ordered);
        var name = pkg.isEmpty() ? CatalogWeaver.CATALOG_CLASS : pkg + "." + CatalogWeaver.CATALOG_CLASS;

        var target = root.resolve(name.replace('.', '/') + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        woven.add(name + " (" + ordered.size() + " widgets)");

        // The class path half. Harmless on the module path, where the module
        // system reads the descriptor instead and ignores this file.
        var services = root.resolve("META-INF/services/"
                + "io.github.digitalsmile.goldberry.widgets.WidgetCatalog");
        Files.createDirectories(services.getParent());
        Files.writeString(services, name + "\n");

        if (descriptor != null) {
            var patched = CatalogWeaver.provideCatalog(
                    Files.readAllBytes(descriptor), java.lang.constant.ClassDesc.of(name));
            if (patched != null) {
                Files.write(descriptor, patched);
                woven.add("module-info (provides WidgetCatalog)");
            }
        }
    }

    /// Where a class with this internal name was compiled to.
    private static Path fileOf(Path root, String internalName) {
        return root.resolve(internalName + ".class");
    }

    private static void weaveFile(Path file, java.util.Map<String, ModelWeaver.Rewired> models,
            java.util.Set<String> open, List<String> woven)
            throws IOException {

        byte[] result;
        try {
            result = ModelWeaver.weave(Files.readAllBytes(file), models, open);
        } catch (WeaveException e) {
            throw new WeaveException(e.getMessage() + "\n  (in " + file + ")", e);
        }
        if (result != null) {
            Files.write(file, result);
            woven.add(file.getFileName().toString().replace(".class", ""));
        }
    }
}
