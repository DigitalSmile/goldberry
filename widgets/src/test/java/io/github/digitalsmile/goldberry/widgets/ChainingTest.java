package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Bindable;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.controls.badge.Badge;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.controls.slider.Slider;
import io.github.digitalsmile.goldberry.widgets.core.Primitives;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Every widget is chainable, and the chain keeps its type
/// ([ADR-0093](../../../../../../book/src/adr/0093-an-application-is-a-root-widget.md)).
///
/// The parity invariant of §11 covers the *shapes* — a widget is a record, a KDL
/// node and a CSS type. This covers the third form of building one: a
/// constructor with the arguments that matter followed by named steps for the
/// ones that are usually defaults, which is what an application actually writes.
class ChainingTest {

    /// The claim that makes the interface worth having, and the one a raw
    /// `Widget` return type would break: `new Badge(…).styled(…)` is a `Badge`,
    /// so the next call in the chain can be a `Badge`'s.
    @Test
    @DisplayName("a chain keeps the widget's own type")
    void selfTyped() {
        Badge badge = new Badge("3").id("unread").styled("danger");
        Slider slider = new Slider(0, 100, 0, 5, null).ticks(5).format("%.0f%%").id("gain");

        assertEquals("unread", badge.id());
        assertEquals(5, slider.ticks());
        assertEquals("%.0f%%", slider.format());
    }

    /// `id` is also the key, which is what lets the element tree pair a rebuilt
    /// description with the element that already exists — so a focused button
    /// keeps its focus across a `setState` that replaced every widget.
    @Test
    @DisplayName("an id is also the key")
    void idIsTheKey() {
        var badge = new Badge("3").id("unread");

        assertEquals("unread", badge.id());
        assertEquals("unread", badge.key());
    }

    /// Order must not matter, or a chain is a puzzle. `styled` preserves the id
    /// and the key; `id` preserves the classes.
    @Test
    @DisplayName("the steps commute")
    void orderDoesNotMatter() {
        assertEquals(
                new Badge("3").id("unread").styled("danger"),
                new Badge("3").styled("danger").id("unread"));
    }

    /// Replaced and not accumulated, because that is what a `class=` attribute
    /// does in markup and §11 says the two forms must agree.
    @Test
    @DisplayName("styled replaces the classes rather than adding to them")
    void styledReplaces() {
        assertEquals(Set.of("b"), new Badge("3").styled("a").styled("b").classes());
    }

    @Test
    @DisplayName("a key that is not an id survives an id being set")
    void keyedIsIndependent() {
        var badge = new Badge("3").keyed(7).styled("info");

        assertEquals(7, badge.key());
        assertEquals(Set.of("info"), badge.classes());
    }

    /// The chain is what a document already says, so the two forms still produce
    /// equal values — the parity invariant, extended to the fluent form.
    @Test
    @DisplayName("a chained widget equals the one KDL builds")
    void parityWithMarkup() {
        var fromKdl = Widgets.inflater().inflateAll(KdlParser.parse("""
                badge id="unread" class="danger" "3"
                """)).getFirst();

        assertEquals(new Badge("3").id("unread").styled("danger"), fromKdl);
    }

    /// A binding is a named step rather than the fourth `null` in a row, which
    /// is the whole reason [Bindable] exists.
    @Test
    @DisplayName("bound() attaches the property, and it is the same object")
    void boundAttachesTheProperty() {
        var gain = Property.of(40);

        var slider = Slider.of(0, 100, 5, gain, null).id("gain");

        assertSame(gain, slider.binding());
        assertEquals(40.0, slider.resolved(), 1e-9);
    }

    /// Structural widgets take their children as an array, so a tree reads as a
    /// tree — no `List.of` between a parent and its children.
    @Test
    @DisplayName("containers take children varargs and still chain")
    void varargsChildren() {
        var row = new Row(new Text("a"), new Text("b")).id("bar");

        assertEquals(2, row.children().size());
        assertEquals("bar", row.id());
    }

    /// Every widget the catalog registers implements the contract, so "chainable"
    /// is a property of the catalog rather than of the widgets somebody
    /// remembered. A control added without it fails here.
    @Test
    @DisplayName("every registered widget is chainable")
    void everyWidgetIsAttributed() {
        var inflater = Widgets.inflater();
        for (var type : names()) {
            var markup = switch (type) {
                case "text", "button", "badge" -> type + " \"x\"";
                case "radio" -> "radio value=\"x\" \"X\"";
                case "option" -> "option value=\"x\" \"X\"";
                default -> type;
            };
            Widget widget = inflater.inflate(KdlParser.parse(markup).getFirst());
            assertTrue(widget instanceof Attributed<?>,
                    () -> type + " cannot be chained: it does not implement Attributed");
        }
    }

    /// And the ones that carry a value can be bound the same way.
    @Test
    @DisplayName("every widget with a binding implements Bindable")
    void everyBoundWidgetIsBindable() {
        for (var widget : List.<Widget>of(
                new Badge("3"), new Text("x"), new Slider(0, 1, 0, 0, null))) {
            assertTrue(widget instanceof Bindable<?>,
                    () -> widget.getClass().getSimpleName() + " has a source and no bound()");
        }
    }

    /// A widget built with no attributes at all is still chainable from
    /// [Attributes#NONE], which is what makes the fluent form the *only* form an
    /// application needs.
    @Test
    @DisplayName("chaining starts from NONE without a constructor that takes attributes")
    void startsFromNone() {
        assertEquals(Attributes.NONE, new Button("Save").attributes());
        assertEquals("save", new Button("Save").id("save").id());
    }

    private static List<String> names() {
        var all = new java.util.ArrayList<>(Primitives.builtInTypes());
        all.addAll(Controls.controlTypes());
        all.remove("radio-group");
        return all;
    }
}
