package dev.goldberry.text.font;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleFinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.assets.BundledFont.Style;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;

/// A resource in a package an application's module does not open, read the two
/// ways the toolkit offers.
///
/// Every other test runs on the class path, where nothing is encapsulated, so
/// this one builds a real named module at run time: `probe`, with a class, a
/// font and a stylesheet in packages it does not open. The toolkit reading the
/// resource itself finds nothing, and says why; the application's own supplier
/// reads it.
///
/// Read more: [Shipping a face](https://goldberry.dev/docs/guide/text.html#shipping-a-face).
@DisplayName("a resource in a package nobody opened")
class EncapsulatedResourceTest {

    private static final byte[] FONT = "a face's bytes".getBytes(StandardCharsets.UTF_8);

    private static Class<?> anchor;

    @BeforeAll
    static void buildModule(@TempDir Path dir) throws Exception {
        var source = Files.createDirectories(dir.resolve("src/probe"));
        Files.writeString(dir.resolve("src/module-info.java"), "module probe { exports probe; }");
        Files.writeString(source.resolve("Anchor.java"), """
                package probe;

                import java.io.InputStream;
                import java.util.function.Supplier;

                public final class Anchor {
                    public static Supplier<InputStream> own(String name) {
                        return () -> Anchor.class.getResourceAsStream(name);
                    }
                }
                """);
        var classes = dir.resolve("classes");
        var compiler = ToolProvider.getSystemJavaCompiler();
        var status = compiler.run(
                null,
                null,
                null,
                "-d",
                classes.toString(),
                dir.resolve("src/module-info.java").toString(),
                source.resolve("Anchor.java").toString());
        assertEquals(0, status, "the probe module compiles");
        Files.write(Files.createDirectories(classes.resolve("probe/fonts")).resolve("Probe.ttf"), FONT);
        Files.writeString(
                Files.createDirectories(classes.resolve("probe/styles")).resolve("app.css"), "box { }");

        var boot = ModuleLayer.boot();
        var configuration = boot.configuration().resolve(ModuleFinder.of(classes), ModuleFinder.of(), Set.of("probe"));
        var layer = boot.defineModulesWithOneLoader(configuration, ClassLoader.getSystemClassLoader());
        anchor = layer.findLoader("probe").loadClass("probe.Anchor");
    }

    @SuppressWarnings("unchecked")
    private static Supplier<InputStream> own(String name) throws ReflectiveOperationException {
        return (Supplier<InputStream>) anchor.getMethod("own", String.class).invoke(null, name);
    }

    @Test
    @DisplayName("the toolkit reading a font beside the class finds nothing, and names the line to add")
    void resourceIsEncapsulated() {
        var source = FontSource.resource("Probe", 400, Style.UPRIGHT, anchor, "fonts/Probe.ttf");

        var problem = source.problem().orElseThrow();

        assertTrue(problem.contains("does not open probe.fonts"), problem);
        assertTrue(problem.contains("opens probe.fonts"), problem);
        assertTrue(problem.contains("FontSource.stream"), problem);
        assertThrows(UncheckedIOException.class, () -> source.bytes().get());
    }

    @Test
    @DisplayName("the application's own stream reads the same font with no opens at all")
    void streamReadsIt() throws ReflectiveOperationException {
        var source = FontSource.stream("Probe", 400, Style.UPRIGHT, own("fonts/Probe.ttf"));

        assertFalse(source.problem().isPresent());
        assertArrayEquals(FONT, source.bytes().get());
        try (var fonts = Fonts.bundled(List.of(source))) {
            assertEquals(List.of(), fonts.unreadable());
        }
    }

    @Test
    @DisplayName("a stylesheet is the same: the toolkit is refused, and the application's stream reads it")
    void stylesheet() throws ReflectiveOperationException {
        var refused = assertThrows(
                IllegalStateException.class,
                () -> Stylesheet.resource(CascadeLayer.APPLICATION, anchor, "styles/app.css"));
        assertTrue(refused.getMessage().contains("does not open probe.styles"), refused.getMessage());

        var sheet = Stylesheet.stream(CascadeLayer.APPLICATION, "app.css", own("styles/app.css"));

        assertEquals(1, sheet.rules().size());
        assertEquals("app.css", sheet.origin());
    }

    @Test
    @DisplayName("a missing file is told apart from an encapsulated one")
    void missing() {
        var source = FontSource.resource("Probe", 400, Style.UPRIGHT, anchor, "fonts/Absent.ttf");

        var problem = source.problem().orElseThrow();

        // `probe.fonts` is a package of the module, so this is still the
        // encapsulation message: from outside, an unopened package and a
        // missing file in it look the same, and the open is the thing to try.
        assertTrue(problem.contains("probe.fonts"), problem);
        var elsewhere = FontSource.resource("Probe", 400, Style.UPRIGHT, anchor, "nowhere/Absent.ttf");
        assertTrue(
                elsewhere.problem().orElseThrow().contains("missing from src/main/resources/probe/nowhere/Absent.ttf"),
                elsewhere.problem().orElseThrow());
    }
}
