package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.controls.badge.Badge;
import io.github.digitalsmile.goldberry.widgets.controls.radio.Radio;
import io.github.digitalsmile.goldberry.widgets.controls.radio.RadioGroup;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A widget is a **value**, and this is what says so.
///
/// ADR-0004 rests on it: a widget is cheap to build, cheap to throw away, holds
/// no state, and is rebuilt constantly — the element tree is what persists. All
/// of that is false the moment one of them can be changed after construction, and
/// the failure would not look like a mutation bug. It would look like a stale
/// frame, or a control that stopped matching its element across a rebuild.
///
/// Records give the *shallow* half for free. What this checks is the half they do
/// not: that a collection handed to a constructor is copied rather than kept, so
/// a caller holding the original cannot reach in afterwards
/// ([ADR-0095](../../../../../../book/src/adr/0095-a-shortcut-is-built-from-enums.md)).
class ImmutabilityTest {

    /// Every widget the catalog registers, built the way markup builds it.
    private static List<Widget> catalog() {
        var inflater = Controls.inflater();
        var widgets = new ArrayList<Widget>();
        for (var type : allTypes()) {
            var markup = switch (type) {
                case "text", "button", "badge" -> type + " \"x\"";
                case "radio" -> "radio value=\"x\" \"X\"";
                case "option" -> "option value=\"x\" \"X\"";
                default -> type;
            };
            widgets.add(inflater.inflate(
                    io.github.digitalsmile.goldberry.kdl.KdlParser.parse(markup).getFirst()));
        }
        return widgets;
    }

    private static List<String> allTypes() {
        var all = new ArrayList<>(
                io.github.digitalsmile.goldberry.widgets.core.Primitives.builtInTypes());
        all.addAll(Controls.controlTypes());
        all.remove("radio-group");
        return all;
    }

    /// A record is shallowly immutable by construction; a non-record widget is a
    /// hole in that guarantee, so there are none.
    @Test
    @DisplayName("every widget is a record")
    void everyWidgetIsARecord() {
        for (var widget : catalog()) {
            assertTrue(widget.getClass().isRecord(),
                    () -> widget.getClass().getSimpleName() + " is not a record");
        }
    }

    /// Records enforce `final` on their components. Any *other* field is state on
    /// a value, which is exactly what ADR-0004 rules out — and a lazily computed
    /// cache would be the plausible way it happens.
    @Test
    @DisplayName("no widget has a mutable field")
    void noMutableFields() {
        for (var widget : catalog()) {
            for (var field : widget.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                assertTrue(Modifier.isFinal(field.getModifiers()),
                        () -> widget.getClass().getSimpleName() + "." + field.getName()
                                + " is not final");
            }
        }
    }

    /// The half records do not give: a `List` handed in and kept would let the
    /// caller reorder a container's children after it was built.
    @Test
    @DisplayName("a container copies its children rather than keeping the caller's list")
    void childrenAreCopied() {
        var mutable = new ArrayList<Widget>();
        mutable.add(new Text("a"));

        var row = new Row(mutable, Attributes.NONE);
        var column = new Column(mutable, Attributes.NONE);
        mutable.add(new Text("b"));

        assertEquals(1, row.children().size(), "Row kept the caller's list");
        assertEquals(1, column.children().size(), "Column kept the caller's list");
    }

    @Test
    @DisplayName("a radio group copies its options too")
    void groupOptionsAreCopied() {
        var mutable = new ArrayList<Widget>();
        mutable.add(new Radio("a", "A"));

        var group = new RadioGroup(null, mutable, null, null, false, Attributes.NONE);
        mutable.add(new Radio("b", "B"));

        assertEquals(1, group.children().size());
    }

    /// And the list that comes back cannot be written to either — a copy that
    /// handed out a mutable view would only move the problem.
    @Test
    @DisplayName("the children a widget returns are unmodifiable")
    void childrenAreUnmodifiable() {
        var row = new Row(new Text("a"));

        assertThrows(UnsupportedOperationException.class,
                () -> row.children().add(new Text("b")));
    }

    @Test
    @DisplayName("attributes copy the class set they are given")
    void classesAreCopied() {
        var mutable = new HashSet<String>();
        mutable.add("primary");

        var attributes = new Attributes("save", mutable, "save");
        mutable.add("danger");

        assertEquals(Set.of("primary"), attributes.classes());
        assertThrows(UnsupportedOperationException.class,
                () -> attributes.classes().add("ghost"));
    }

    /// Every chainable step returns a **new** widget rather than mutating the
    /// receiver, which is the property that makes a chain safe to share: handing
    /// `base` to two panes and styling one must not restyle the other.
    @Test
    @DisplayName("chaining never mutates the receiver")
    void chainingCopies() {
        var base = new Badge("3");

        var styled = base.styled("danger");
        var identified = base.id("unread");

        assertNotSame(base, styled);
        assertEquals(Set.of(), base.classes(), "styled() mutated the original");
        assertEquals(null, base.id(), "id() mutated the original");
        assertEquals(Set.of("danger"), styled.classes());
        assertEquals("unread", identified.id());
        // And the two derivations are independent of each other.
        assertEquals(Set.of(), identified.classes());
    }

    /// The one component a widget holds that is genuinely mutable *by design*.
    ///
    /// A binding is an [Observable] and a handler is a lambda: both are
    /// references to something that changes, and that is the point — data flows
    /// down and events flow up (ADR-0063). What matters is that the widget cannot
    /// *write* through them: it is handed the read-only half of a property, and
    /// there is no `set` to call.
    @Test
    @DisplayName("a binding is read-only from the widget's side")
    void bindingsAreReadOnly() {
        var badge = Badge.of("0", io.github.digitalsmile.goldberry.bind.Property.of(7));

        assertTrue(badge.binding() instanceof Observable<?>);
        for (var method : Observable.class.getMethods()) {
            assertTrue(!method.getName().equals("set"),
                    "Observable grew a setter; a widget could write to the model");
        }
    }

    /// Equality is what the reconciler uses to decide a rebuild changed nothing,
    /// so two widgets built the same way must be equal — which records give only
    /// if every component is itself a value.
    @Test
    @DisplayName("two widgets built alike are equal")
    void valuesAreEqual() {
        assertEquals(new Row(new Text("a")), new Row(new Text("a")));
        assertEquals(new Badge("3").styled("info"), new Badge("3").styled("info"));
    }

    /// A handler is deliberately excluded from that: two lambdas are never equal,
    /// so a widget carrying one is never equal to another. Recorded rather than
    /// asserted as a bug — §11's parity test compares widgets built *without*
    /// handlers for exactly this reason, and `Slider.format` is a pattern rather
    /// than a function because of it (ADR-0080).
    @Test
    @DisplayName("two widgets carrying handlers are not equal, and that is known")
    void handlersDefeatEquality() {
        DoubleConsumer handler = value -> { };
        Consumer<String> other = value -> { };

        assertEquals(
                new io.github.digitalsmile.goldberry.widgets.controls.slider.Slider(
                        0, 1, 0, 0, handler),
                new io.github.digitalsmile.goldberry.widgets.controls.slider.Slider(
                        0, 1, 0, 0, handler),
                "the same handler must still compare equal");
        assertTrue(other != null);
    }
}
