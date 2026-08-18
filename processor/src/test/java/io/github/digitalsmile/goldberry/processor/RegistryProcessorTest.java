package io.github.digitalsmile.goldberry.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Bindings;
import io.github.digitalsmile.goldberry.widgets.Actions;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
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

    /// Compiles `source` **and its generated registry** all the way to class
    /// files, then hands back a loader that can see both.
    ///
    /// The other helper stops at `-proc:only`, which proves what the processor
    /// *says*. This proves what it *writes*: generated code that compiles is not
    /// the same claim as generated code that runs, and the whole of ADR-0098 is a
    /// mechanism that cannot fail until it is loaded.
    private ClassLoader compileAndLoad(String name, String source) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var messages = new StringBuilder();
        var files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        var classes = Files.createDirectories(output.resolve("run"));

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
                        "-d", classes.toString()),
                null, List.of(unit));
        task.setProcessors(List.of(new RegistryProcessor()));
        var ok = task.call();
        files.close();
        assertTrue(ok, () -> "the generated code did not compile:\n" + messages);

        // Parent-first, so the toolkit's own `Property`, `Bindings` and `Actions`
        // are the *test's* classes and the results can be used as themselves.
        // Only `example.*` comes from here -- which also puts the model and its
        // registry in one unnamed module, exactly as an application puts them in
        // one named one.
        return new URLClassLoader(new URL[] {classes.toUri().toURL()},
                RegistryProcessorTest.class.getClassLoader());
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

    /// A private member used to be a compile error telling the author to widen
    /// it, which made a model's encapsulation a consequence of how the toolkit
    /// reads it. It gets a handle now ([ADR-0098]).
    @Test
    @DisplayName("a private @Bind field and a private @Action compile clean")
    void privateMembersAreAccepted() throws IOException {
        var messages = compile("example.Model", model("""
                    @Bind("app.x") private final Property<String> x = Property.of("");
                    @Action("app.go") private void go() { }
                    @Action("app.set") private void set(double value) { }
                """));

        assertFalse(messages.contains("ERROR"), messages);
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

    /// The end-to-end claim of ADR-0098, run rather than argued: a model whose
    /// every annotated member is private, wired through the generated handles,
    /// with the values read back and the actions actually invoked.
    ///
    /// Both shapes of action are here because they take different routes through
    /// the handle — a bare one is invoked with the receiver alone, a valued one
    /// with the receiver and a `double` the helper has to unbox on the way in.
    @Test
    @DisplayName("a registry over private members binds, reads and invokes")
    void privateMembersWorkAtRuntime() throws Exception {
        var loader = compileAndLoad("example.Model", """
                package example;
                import io.github.digitalsmile.goldberry.bind.*;
                @Registry
                public final class Model {
                    @Bind("app.x") private final Property<String> x = Property.of("start");
                    @Action("app.go") private void go() { x.set("went"); }
                    @Action("app.set") private void set(double value) { x.set("set " + value); }
                }
                """);

        var modelType = Class.forName("example.Model", true, loader);
        var model = modelType.getDeclaredConstructor().newInstance();
        var registry = Class.forName("example.ModelRegistry", true, loader);

        var bindings = (Bindings) registry.getMethod("bindings", modelType).invoke(null, model);
        var actions = (Actions) registry.getMethod("actions", modelType).invoke(null, model);

        // The binding is the private field itself and not a copy of it: what the
        // handle read is the same `Property` the actions below write through.
        var bound = bindings.resolve("app.x");
        assertEquals("start", bound.get());

        actions.resolve("app.go").run();
        assertEquals("went", bound.get(), "a bare private @Action did not run");

        actions.resolveValued("app.set").accept("0.5");
        assertEquals("set 0.5", bound.get(), "a valued private @Action did not get its value");
    }

    /// An accessible member still gets no handle, which is what keeps a generated
    /// registry readable for the ordinary case: a mechanism you are not using
    /// should not be a line you have to read.
    @Test
    @DisplayName("a package-private model generates no handles at all")
    void accessibleMembersStayDirect() throws Exception {
        var loader = compileAndLoad("example.Plain", """
                package example;
                import io.github.digitalsmile.goldberry.bind.*;
                @Registry
                public final class Plain {
                    @Bind("app.x") final Property<String> x = Property.of("start");
                    @Action("app.go") void go() { x.set("went"); }
                }
                """);

        var registry = Class.forName("example.PlainRegistry", true, loader);

        assertEquals(0, registry.getDeclaredFields().length,
                "an accessible member should need no handle constant");
        var modelType = Class.forName("example.Plain", true, loader);
        var model = modelType.getDeclaredConstructor().newInstance();
        var actions = (Actions) registry.getMethod("actions", modelType).invoke(null, model);
        var bindings = (Bindings) registry.getMethod("bindings", modelType).invoke(null, model);

        actions.resolve("app.go").run();
        assertEquals("went", bindings.resolve("app.x").get());
    }

    /// Two overloads of one name, both private: the constant names are derived
    /// from the member's own name, so this is where they would collide.
    @Test
    @DisplayName("two private actions sharing a name get two handles")
    void overloadedPrivateActions() throws Exception {
        var loader = compileAndLoad("example.Twice", """
                package example;
                import io.github.digitalsmile.goldberry.bind.*;
                @Registry
                public final class Twice {
                    @Bind("app.x") private final Property<String> x = Property.of("start");
                    @Action("app.go") private void go() { x.set("bare"); }
                    @Action("app.go-value") private void go(String value) { x.set(value); }
                }
                """);

        var modelType = Class.forName("example.Twice", true, loader);
        var model = modelType.getDeclaredConstructor().newInstance();
        var registry = Class.forName("example.TwiceRegistry", true, loader);
        var bindings = (Bindings) registry.getMethod("bindings", modelType).invoke(null, model);
        var actions = (Actions) registry.getMethod("actions", modelType).invoke(null, model);

        actions.resolve("app.go").run();
        assertEquals("bare", bindings.resolve("app.x").get());
        actions.resolveValued("app.go-value").accept("valued");
        assertEquals("valued", bindings.resolve("app.x").get());
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
