package io.github.digitalsmile.goldberry.bind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// What a `bind=` path resolves to (ADR-0062).
class BindingsTest {

    @Test
    @DisplayName("a bound path resolves to its property")
    void resolvesWhatWasBound() {
        var frost = Property.of(true);
        var bindings = Bindings.strict().bind("prefs.frost", frost);

        assertSame(frost, bindings.resolve("prefs.frost"));
    }

    @Test
    @DisplayName("no path at all is not an error: most nodes have no bind=")
    void nullPathResolvesToNothing() {
        assertNull(Bindings.strict().resolve(null));
    }

    @Test
    @DisplayName("a strict registry refuses an unknown path and says what it knows")
    void strictRefusesUnknown() {
        var bindings = Bindings.strict().bind("prefs.frost", Property.of(true));

        var thrown = assertThrows(IllegalArgumentException.class,
                () -> bindings.resolve("prefs.frsot"));

        // A checkbox bound to nothing looks exactly like one bound to something
        // that never changes, so the typo has to be loud.
        assertTrue(thrown.getMessage().contains("prefs.frsot"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("prefs.frost"),
                () -> "the message should list what is bound: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a lenient registry resolves an unknown path to nothing")
    void lenientAllowsUnknown() {
        // Markup-first development: the screen is laid out before the model
        // exists, and reload is deliberately forgiving (ADR-0051).
        assertNull(Bindings.lenient().resolve("prefs.frost"));
        assertNull(Bindings.none().resolve("anything"));
    }

    @Test
    @DisplayName("binding the same path twice is refused, replacing it deliberately is not")
    void noAccidentalShadowing() {
        var first = Property.of(1);
        var second = Property.of(2);
        var bindings = Bindings.strict().bind("count", first);

        assertThrows(IllegalStateException.class, () -> bindings.bind("count", second));

        bindings.rebind("count", second);
        assertSame(second, bindings.resolve("count"));
    }

    @Test
    @DisplayName("a registry can declare the property it holds")
    void bindsAndCreates() {
        var bindings = Bindings.strict();

        var opacity = bindings.bind("window.opacity", Double.class, 0.8);

        assertEquals(0.8, opacity.get());
        assertSame(opacity, bindings.resolve("window.opacity"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"frost", "prefs.frost", "prefs.window.opacity", "_private", "kebab-case"})
    @DisplayName("a dotted path is a name, or names joined by dots")
    void validPaths(String path) {
        var bindings = Bindings.lenient();
        bindings.bind(path, Property.of(1));
        assertEquals(1, bindings.resolve(path).get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "!prefs.frost",
        "prefs.frost == true",
        "prefs.frost ? \"on\" : \"off\"",
        "prefs..frost",
        "prefs.",
        ".frost",
        "",
        "1prefs",
        "prefs frost",
    })
    @DisplayName("anything that is not a path is refused, rather than resolving to nothing")
    void expressionsAreRefused(String path) {
        // The decision in ADR-0062 is that `bind` is a path and nothing else.
        // Enforcing it here is what makes that a contract rather than a wish:
        // `bind="!prefs.frost"` fails at inflation with the text quoted, instead
        // of producing a control that silently never updates.
        var bindings = Bindings.lenient();

        assertThrows(IllegalArgumentException.class, () -> bindings.resolve(path));
        assertThrows(IllegalArgumentException.class, () -> bindings.bind(path, Property.of(1)));
    }

    @Test
    @DisplayName("what markup resolves has no way to write, and that is a type not a promise")
    void resolvingHandsOutTheReadOnlyHalf() throws ReflectiveOperationException {
        // One-way binding is enforced by the signature (ADR-0063). Widening any
        // of these back to Property is what this test exists to fail on: a
        // control could then write to the application's model from a `bind=`
        // attribute, and "who changed this value?" would stop having an answer.
        assertEquals(Observable.class,
                Bindings.class.getMethod("resolve", String.class).getReturnType());
        assertEquals(Observable.class,
                Bindings.class.getMethod("resolve", String.class, Class.class).getReturnType());
        assertEquals(Observable.class,
                io.github.digitalsmile.goldberry.widget.Widget.class.getMethod("binding").getReturnType());

        assertTrue(java.util.Arrays.stream(Observable.class.getMethods())
                        .noneMatch(method -> method.getName().equals("set")),
                "Observable is the half of a Property that cannot be written");
    }

    @Test
    @DisplayName("a typed resolve refuses a property holding something else")
    void typedResolveChecks() {
        var bindings = Bindings.strict().bind("prefs.frost", Property.of("yes"));

        // What a two-way control asks for: writing a Boolean into a path the
        // application holds a String in is a bug that would otherwise surface as
        // a ClassCastException three frames later, in a listener.
        assertThrows(IllegalArgumentException.class,
                () -> bindings.resolve("prefs.frost", Boolean.class));
    }

    @Test
    @DisplayName("a typed resolve accepts a property holding nothing yet")
    void typedResolveAllowsNull() {
        var pending = Property.<String>of(null);
        var bindings = Bindings.strict().bind("user.name", pending);

        // "Not loaded yet" has to be expressible, or every application is pushed
        // into a sentinel value.
        assertSame(pending, bindings.resolve("user.name", String.class));
    }
}
