package io.github.digitalsmile.goldberry.widgets.core;

import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;

import io.github.digitalsmile.goldberry.widgets.core.Primitives;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.core.Spacer;
import io.github.digitalsmile.goldberry.widgets.text.Text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Bindings;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// `bind=` from **markup** to a widget (§9,
/// [ADR-0062](../../../../../../../book/src/adr/0062-bind-is-a-path-and-nothing-else.md)).
///
/// The other half — what an element does with a binding once it holds one, and
/// when it lets go — is `:core`'s `BindingLifecycleTest`, because it is about the
/// element tree rather than about `text`. The two were one file until [ADR-0092]
/// moved the primitives out of `:core` and the seam became a module boundary.
class BindingTest {

    private static Widget inflate(String markup, Bindings bindings) {
        return Primitives.inflater(bindings).inflate(KdlParser.parse(markup).getFirst());
    }

    @Nested
    @DisplayName("inflating")
    class Inflating {

        @Test
        @DisplayName("a bound text is the same value however it was built")
        void parity() {
            var name = Property.of("Ada");
            var bindings = Bindings.strict().bind("user.name", name);

            var fromMarkup = inflate("text id=\"who\" bind=\"user.name\"", bindings);
            var fromJava = new Text(
                    "", name, new Attributes("who", java.util.Set.of(), "who"));

            // The parity invariant of §11, extended to the attribute: markup and
            // Java produce the same widget, and the property is the same object
            // rather than a copy of its value.
            assertEquals(fromJava, fromMarkup);
            assertSame(name, ((Text) fromMarkup).binding());
        }

        @Test
        @DisplayName("a bound text shows what the property holds, not its argument")
        void boundValueWins() {
            var bindings = Bindings.strict().bind("user.name", Property.of("Ada"));

            var text = (Text) inflate("text bind=\"user.name\" \"placeholder\"", bindings);

            assertEquals("Ada", text.resolved());
        }

        @Test
        @DisplayName("an unbound path leaves the argument as what is drawn")
        void lenientFallsBackToTheLiteral() {
            // Markup-first: the screen is laid out before the model exists, and a
            // designer needs to see something (ADR-0051).
            var text = (Text) inflate("text bind=\"user.name\" \"Name here\"", Bindings.lenient());

            assertEquals("Name here", text.resolved());
            assertEquals(null, text.binding(), "nothing to follow, so nothing is subscribed to");
        }

        @Test
        @DisplayName("a null value reads as nothing rather than as the word null")
        void nullReadsAsEmpty() {
            var bindings = Bindings.strict().bind("user.name", Property.of(null));

            assertEquals("", ((Text) inflate("text bind=\"user.name\"", bindings)).resolved());
        }

        @Test
        @DisplayName("an expression in bind= fails at inflation, with the text quoted")
        void expressionFailsLoudly() {
            var bindings = Bindings.strict().bind("prefs.frost", Property.of(true));

            var thrown = assertThrows(IllegalArgumentException.class,
                    () -> inflate("text bind=\"!prefs.frost\"", bindings));

            assertTrue(thrown.getMessage().contains("!prefs.frost"), thrown.getMessage());
        }
    }
}
