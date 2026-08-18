package io.github.digitalsmile.goldberry.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// The processor's **refusals**, which are the reason it is worth having.
///
/// Generating the right code is the easy half and the showcase proves it every
/// build. What has to be tested is that a mistake is a *compile* error with the
/// member named — because the whole argument for moving this wiring to compile
/// time is that the failures move with it. A processor that silently skipped a
/// private field would be worse than the hand-written registry it replaces
/// ([ADR-0096](../../../../book/src/adr/0096-a-registry-is-generated-not-reflected.md)).
class RegistryProcessorTest {

    @TempDir
    Path output;

    /// Compiles `source` with the processor and returns javac's diagnostics.
    private String compile(String name, String source) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var messages = new StringBuilder();
        var files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        var classes = Files.createDirectories(output.resolve("classes"));

        var unit = new SimpleJavaFileObject(
                URI.create("string:///" + name.replace('.', '/') + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };

        var task = compiler.getTask(null, files,
                diagnostic -> messages.append(diagnostic.getKind()).append(": ")
                        .append(diagnostic.getMessage(null)).append('\n'),
                List.of("-classpath", System.getProperty("java.class.path"),
                        "-d", classes.toString(),
                        "-proc:only"),
                null, List.of(unit));
        task.setProcessors(List.of(new RegistryProcessor()));
        task.call();
        files.close();
        return messages.toString();
    }

    private static String model(String body) {
        return """
                package example;
                import io.github.digitalsmile.goldberry.bind.*;
                @Registry
                public final class Model {
                %s
                }
                """.formatted(body);
    }

    @Test
    @DisplayName("a private @Bind field is refused, and the message says why")
    void privateFieldIsRefused() throws IOException {
        var messages = compile("example.Model",
                model("    @Bind(\"app.x\") private final Property<String> x = Property.of(\"\");"));

        assertTrue(messages.contains("ERROR"), messages);
        assertTrue(messages.contains("is private"), messages);
        assertTrue(messages.contains("same package"), () -> "no hint about the fix: " + messages);
    }

    /// A `@Bind` on something that is not a `Property` would generate code that
    /// does not compile, which is a worse error than this one.
    @Test
    @DisplayName("a @Bind on a non-Property is refused")
    void nonPropertyIsRefused() throws IOException {
        var messages = compile("example.Model",
                model("    @Bind(\"app.x\") final String x = \"\";"));

        assertTrue(messages.contains("ERROR"), messages);
        assertTrue(messages.contains("a binding is a Property"), messages);
    }

    /// Two features quietly sharing one path is a bug that presents as a value
    /// changing by itself — which `Bindings` refuses at run time and this refuses
    /// before there is a run time.
    @Test
    @DisplayName("two members claiming one path are refused")
    void duplicatePathIsRefused() throws IOException {
        var messages = compile("example.Model", model("""
                    @Bind("app.x") final Property<String> a = Property.of("");
                    @Bind("app.x") final Property<String> b = Property.of("");
                """));

        assertTrue(messages.contains("ERROR"), messages);
        assertTrue(messages.contains("already claimed"), messages);
    }

    @Test
    @DisplayName("an @Action taking two arguments is refused")
    void twoArgumentActionIsRefused() throws IOException {
        var messages = compile("example.Model", model("""
                    @Bind("app.x") final Property<String> x = Property.of("");
                    @Action("app.go") void go(String a, String b) { }
                """));

        assertTrue(messages.contains("ERROR"), messages);
        assertTrue(messages.contains("never both"), messages);
    }

    /// A valued action crosses as a `String`, so the processor has to be able to
    /// write the parse. A type it cannot parse is refused rather than generated
    /// as a cast that fails at run time.
    @Test
    @DisplayName("an @Action taking an unparseable type is refused")
    void unparseableActionIsRefused() throws IOException {
        var messages = compile("example.Model", model("""
                    @Bind("app.x") final Property<String> x = Property.of("");
                    @Action("app.go") void go(java.util.List<String> values) { }
                """));

        assertTrue(messages.contains("ERROR"), messages);
        assertTrue(messages.contains("crosses as a String"), messages);
    }

    /// The mistake with no other symptom at all: an annotated member on a class
    /// nothing generates from is simply never registered, and the control it was
    /// for renders perfectly and never moves.
    @Test
    @DisplayName("an annotated member outside a @Registry class is refused")
    void strayAnnotationIsRefused() throws IOException {
        var messages = compile("example.Stray", """
                package example;
                import io.github.digitalsmile.goldberry.bind.*;
                public final class Stray {
                    @Bind("app.x") final Property<String> x = Property.of("");
                }
                """);

        assertTrue(messages.contains("ERROR"), messages);
        assertTrue(messages.contains("nothing would read it"), messages);
    }

    @Test
    @DisplayName("a path that is not dotted is refused")
    void badPathIsRefused() throws IOException {
        var messages = compile("example.Model",
                model("    @Bind(\"app gain\") final Property<String> x = Property.of(\"\");"));

        assertTrue(messages.contains("ERROR"), messages);
        assertTrue(messages.contains("dotted path"), messages);
    }

    @Test
    @DisplayName("a well-formed model compiles clean")
    void validModelIsAccepted() throws IOException {
        var messages = compile("example.Model", model("""
                    @Bind("app.gain") final Property<Number> gain = Property.of(40);
                    @Action("app.click") void click() { }
                    @Action("app.set") void set(double value) { }
                """));

        assertFalse(messages.contains("ERROR"), messages);
    }
}
