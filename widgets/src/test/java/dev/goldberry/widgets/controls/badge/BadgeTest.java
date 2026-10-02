package dev.goldberry.widgets.controls.badge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.bind.Property;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Corners;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.layout.Align;
import dev.goldberry.layout.Length;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Widgets;

/// A badge is a small pill of text with a semantic fill, and the first widget in
/// the catalogue that is not a control.
///
/// Nothing here needs the native library: a badge has no behaviour to drive and
/// no geometry of its own beyond what the cascade resolves, so the assertions are
/// on the value and on the computed style. What it *looks* like is
/// [BadgeGoldenTest]'s, and whether its colours are legible is [ContrastTest]'s.
///
/// Read more: [Badge](https://goldberry.dev/docs/components/buttons.html#badge).
class BadgeTest {

    private static ComputedStyle styleOf(Widget widget, Theme theme) {
        return ComputedStyle.of(
                new StyleResolver(Controls.stylesheets(theme)).resolve(new ElementTree(widget).root()),
                CssLength.Context.DEFAULT);
    }

    @Test
    @DisplayName("the Java-built and KDL-built badges are equal values")
    void javaAndKdlAgree() {
        var attributes = new Attributes("unread", Set.of("danger"), "unread");

        var fromKdl = Widgets.inflater().inflateAll(KdlParser.parse("""
                badge id="unread" class="danger" "3"
                """)).getFirst();

        assertEquals(new Badge("3", null, attributes), fromKdl);
    }

    @Test
    @DisplayName("`styled` is the Java spelling of class=, so the two forms stay equal")
    void styledMatchesTheClassAttribute() {
        // Markup and Java must build the same value, and that rules an enum out:
        // KDL has no way to spell one, so a variant has to be a class in both
        // forms or the two forms cannot produce equal values.
        assertEquals(Set.of("danger"), new Badge("3").styled("danger").classes());
    }

    @Test
    @DisplayName("a bound badge reads the property, and the literal is the fallback")
    void bindingWins() {
        var unread = Property.of(7);

        assertEquals("7", Badge.of("0", unread).resolved());
        assertEquals("0", new Badge("0").resolved(), "no binding, so the literal");
        assertSame(unread, Badge.of("0", unread).binding());
        assertNull(new Badge("0").binding());
    }

    /// Unlike a slider's, a badge's binding has **no numeric meaning to fall back
    /// to** — a badge shows a count or a status, so whatever the model holds is
    /// what the chip says.
    @Test
    @DisplayName("a non-numeric binding is the status it says it is")
    void bindingNeedNotBeANumber() {
        assertEquals("offline", Badge.of("", Property.of("offline")).resolved());
    }

    /// The badge's metrics: height 20, min-width 20, padding-x 4, a fully rounded
    /// corner, `caption` type. Asserted against the *resolved* style rather than read off the stylesheet,
    /// so a rule that stopped matching would fail here rather than in a golden.
    @Test
    @DisplayName("the badge's metrics come out of the cascade")
    void metrics() {
        var style = styleOf(new Badge("3"), Theme.NORD_DARK);

        assertEquals(Length.points(20), style.height());
        assertEquals(Length.points(4), style.padding().left());
        assertEquals(Length.points(4), style.padding().right());
        assertEquals(
                Length.points(0),
                style.padding().top(),
                "no vertical padding: the height is pinned and centring does the rest");
        assertEquals(
                Corners.all(10),
                style.decoration().corners(),
                "a fully rounded corner on a 20px box, spelled the way toggle-track spells it");
        assertEquals(11, style.typography().size(), 1e-9, "the `caption` type size");
        assertEquals(Align.CENTER, style.alignItems());
    }

    /// **A one-digit badge is a circle**, which is the metric the badge gained
    /// last and the one it had gone without since it shipped.
    ///
    /// The minimum is asserted **against the height** rather than against 20,
    /// because that is the claim: equal width and height inside a `full` radius
    /// is what a circle *is*, so the two moving apart is the failure worth
    /// naming, and either of them moving alone would pass a test that checked
    /// the literal.
    @Test
    @DisplayName("its minimum width is its height, which is what makes one digit round")
    void oneDigitIsACircle() {
        var style = styleOf(new Badge("3"), Theme.NORD_DARK);

        assertEquals(style.height(), style.limits().minWidth());
        assertEquals(
                Length.UNDEFINED,
                style.limits().maxWidth(),
                "and no maximum: a badge grows with its content, which is the other half of the row");
    }

    /// The padding is **4** and not the component default of 8, and the reason
    /// is arithmetic rather than taste: 8 + a caption digit + 8 is 23 in a 20-tall
    /// box, so a badge with the default padding can never be round however large
    /// its minimum is. 6 would have been the comfortable answer and is off the
    /// spacing ramp, which is `2, 4, 8, 12, …` with no off-ramp values allowed.
    @Test
    @DisplayName("and the padding that makes it possible is on the spacing ramp")
    void thePaddingIsOnTheRamp() {
        var padding = styleOf(new Badge("3"), Theme.NORD_DARK).padding();

        assertTrue(
                List.of(2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64)
                        .contains((int) ((Length.Points) padding.left()).value()),
                () -> padding.left() + " is not on the spacing ramp");
    }
    /// The animation table has no `badge` row, and anything not listed there does
    /// not animate. The absence is the specification, so it is asserted rather than
    /// left to be true by accident — a `transition` added to the shared control
    /// rules would otherwise reach a badge silently.
    @Test
    @DisplayName("nothing about it animates, because the animation table does not list it")
    void nothingAnimates() {
        assertTrue(styleOf(new Badge("3"), Theme.NORD_DARK).transitions().isEmpty());
        assertFalse(new Badge("3").isAnimating());
    }

    /// It is `Widget.Leaf` and `Styled` and nothing else: no [Handles], so no
    /// focus, no pointer, no keys. Its semantics are plain `text`.
    ///
    /// Asserted through a [Widget]-typed reference on purpose — against the record
    /// type the `instanceof` is a *compile* error, which is the stronger guarantee
    /// but not one a reader can see, and it would silently become a runtime check
    /// the day someone added the interface.
    @Test
    @DisplayName("it takes no input and is not in the Tab order")
    void takesNoInput() {
        Widget badge = new Badge("3");

        assertFalse(badge instanceof Handles, "a chip that could be pressed would be a button that looks like a label");
        assertFalse(new Badge("3").isDisabled(), "and there is no state for a disabled chip to be in");
        assertEquals(List.of(), new Badge("3").children());
    }

    /// The variants are the aurora hues' first sanctioned appearance — those hues
    /// appear on a control only with a semantic meaning — so
    /// every one of them has to actually select something — a class with no rule
    /// behind it is a variant that silently renders as the default.
    @Test
    @DisplayName("every variant class changes the fill on both themes")
    void everyVariantSelects() {
        for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
            var plain = styleOf(new Badge("3"), theme).background();
            for (var variant : List.of("accent", "danger", "warning", "success", "info")) {
                assertFalse(
                        plain == styleOf(new Badge("3").styled(variant), theme).background(),
                        () -> "badge." + variant + " resolves to the default fill on " + theme);
            }
        }
    }
}
