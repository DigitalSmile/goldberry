package dev.goldberry.media.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.lang.module.FindException;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.media.view.gpu.GpuVideo;

/// [PictureRenderer] from an application's named module, which the rest of the
/// suite, on the class path, cannot see.
///
/// A module `probe` that requires this module alone is compiled at run time
/// against the module path of this run's own modules, as strictly as the build
/// compiles: it names the renderer and `:gpu`'s frame and target, which this
/// module's `requires transitive` lets it read, and it cannot name `:gpu`'s video
/// package, which is exported to this module alone. Skipped in the run without
/// `:gpu`, where there is no frame to name.
@DisplayName("a picture renderer, from an application's module")
class PictureRendererModuleTest {

    @BeforeEach
    void needsGpuModule() {
        assumeTrue(GpuVideo.available());
    }

    /// The entries of this run's class path that are named modules: the
    /// toolkit's own and the libraries it requires.
    private static String modulePath() {
        return Stream.of(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(Path::of)
                .filter(PictureRendererModuleTest::isNamedModule)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
    }

    /// Whether `entry` holds a module with a descriptor of its own: a
    /// multi-release jar's counts, an automatic module does not.
    private static boolean isNamedModule(Path entry) {
        if (Files.isDirectory(entry)) {
            return Files.isRegularFile(entry.resolve("module-info.class"));
        }
        if (!entry.toString().endsWith(".jar") || !Files.isRegularFile(entry)) {
            return false;
        }
        try {
            return ModuleFinder.of(entry).findAll().stream()
                    .noneMatch(module -> module.descriptor().isAutomatic());
        } catch (FindException e) {
            return false;
        }
    }

    /// What javac says compiling `probe` with `slot` as its one class: no
    /// diagnostics at all is a clean compile.
    private static List<String> compile(Path dir, String slot) throws IOException {
        var source = Files.createDirectories(dir.resolve("src/probe"));
        var moduleInfo = Files.writeString(dir.resolve("src/module-info.java"), """
                module probe {
                    requires dev.goldberry.media;
                }
                """);
        var slotFile = Files.writeString(source.resolve("Slot.java"), slot);
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            var units = files.getJavaFileObjects(moduleInfo, slotFile);
            var options = List.of(
                    "-Xlint:all",
                    "-Werror",
                    "--module-path",
                    modulePath(),
                    // Not this run's class path, which javac takes by default
                    // and which would hand it every package unencapsulated.
                    "--class-path",
                    Files.createDirectories(dir.resolve("nothing")).toString(),
                    "-d",
                    Files.createDirectories(dir.resolve("classes")).toString());
            compiler.getTask(null, files, diagnostics, options, null, units).call();
        }
        return diagnostics.getDiagnostics().stream()
                .map(diagnostic -> diagnostic.getKind() + ": " + diagnostic.getMessage(Locale.ROOT))
                .toList();
    }

    @Test
    @DisplayName("compiles against the renderer, :gpu's frame and target, with every lint on")
    void readsTheRenderer(@TempDir Path dir) throws IOException {
        var diagnostics = compile(dir, """
                package probe;

                import dev.goldberry.gpu.GpuFrame;
                import dev.goldberry.gpu.TextureView;
                import dev.goldberry.media.gpu.PictureRenderer;
                import dev.goldberry.media.picture.Picture;

                public final class Slot implements AutoCloseable {
                    private final PictureRenderer renderer = new PictureRenderer();

                    public void draw(GpuFrame frame, Picture picture, TextureView layer) {
                        renderer.render(frame, picture, layer);
                    }

                    @Override
                    public void close() {
                        renderer.close();
                    }
                }
                """);
        assertEquals(List.of(), diagnostics);
    }

    @Test
    @DisplayName("cannot name :gpu's video layer, which is exported to this module alone")
    void videoPackageStaysClosed(@TempDir Path dir) throws IOException {
        var diagnostics = compile(dir, """
                package probe;

                import dev.goldberry.gpu.video.VideoLayer;

                public final class Slot {
                    private final VideoLayer layer = new VideoLayer();
                }
                """);
        assertTrue(
                diagnostics.stream()
                        .anyMatch(line -> line.startsWith("ERROR")
                                && line.contains("dev.goldberry.gpu.video")
                                && line.contains("does not export")),
                String.join("\n", diagnostics));
    }
}
