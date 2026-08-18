package io.github.digitalsmile.goldberry.widgets.controls.badge;

import io.github.digitalsmile.goldberry.widget.Attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.natives.yoga.Align;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.badge.Badge;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The ninth entry in §3's table and the first that is not a control
/// ([ADR-0087]).
///
/// Nothing here needs the native library: a badge has no behaviour to drive and
/// no geometry of its own beyond what the cascade resolves, so the assertions are
/// on the value and on the computed style. What it *looks* like is
/// [BadgeGoldenTest]'s, and whether its colours are legible is [ContrastTest]'s.
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

        var fromKdl = Controls.inflater().inflateAll(KdlParser.parse("""
                badge id="unread" class="danger" "3"
                """)).getFirst();

        assertEquals(new Badge("3", null, attributes), fromKdl);
    }

    @Test
    @DisplayName("`styled` is the Java spelling of class=, so the two forms stay equal")
    void styledMatchesTheClassAttribute() {
        // §11's parity invariant is what rules an enum out: KDL has no way to
        // spell one, so a variant has to be a class in both forms or the two
        // forms cannot produce equal values.
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
    /// to** — §3 says "count *or status*", so whatever the model holds is what the
    /// chip says.
    @Test
    @DisplayName("a non-numeric binding is the status it says it is")
    void bindingNeedNotBeANumber() {
        assertEquals("offline", Badge.of("", Property.of("offline")).resolved());
    }

    @Test
    @DisplayName("it is a widget in the catalog and not a part")
    void isACatalogWidget() {
        assertTrue(Controls.controlTypes().contains("badge"));
        assertTrue(Controls.inflater().registered().contains("badge"));
        assertEquals("badge", new Badge("3").cssType());
    }

    /// §3's row: height 20, padding-x 8, radius `full`, `caption`. Asserted
    /// against the *resolved* style rather than read off the stylesheet, so a
    /// rule that stopped matching would fail here rather than in a golden.
    @Test
    @DisplayName("§3's metrics come out of the cascade")
    void metrics() {
        var style = styleOf(new Badge("3"), Theme.NORD_DARK);

        assertEquals(StyleLength.points(20), style.height());
        assertEquals(StyleLength.points(8), style.padding().left());
        assertEquals(StyleLength.points(8), style.padding().right());
        assertEquals(StyleLength.points(0), style.padding().top(),
                "no vertical padding: the height is pinned and centring does the rest");
        assertEquals(10, style.decoration().radius(), 1e-9,
                "§1.5's `full` on a 20px box, spelled the way toggle-track spells it");
        assertEquals(11, style.typography().size(), 1e-9, "§1.4's `caption`");
        assertEquals(Align.CENTER, style.alignItems());
    }

    /// §3.1 has no `badge` row, and its preamble says anything not listed does not
    /// animate. The absence is the specification, so it is asserted rather than
    /// left to be true by accident — a `transition` added to the shared control
    /// rules would otherwise reach a badge silently.
    @Test
    @DisplayName("nothing about it animates, because §3.1 does not list it")
    void nothingAnimates() {
        assertTrue(styleOf(new Badge("3"), Theme.NORD_DARK).transitions().isEmpty());
        assertFalse(new Badge("3").isAnimating());
    }

    /// It is `Widget.Leaf` and `Styled` and nothing else: no [Handles], so no
    /// focus, no pointer, no keys. §3 gives its semantics as `text`.
    ///
    /// Asserted through a [Widget]-typed reference on purpose — against the record
    /// type the `instanceof` is a *compile* error, which is the stronger guarantee
    /// but not one a reader can see, and it would silently become a runtime check
    /// the day someone added the interface.
    @Test
    @DisplayName("it takes no input and is not in the Tab order")
    void takesNoInput() {
        Widget badge = new Badge("3");

        assertFalse(badge instanceof Handles,
                "a chip that could be pressed would be a button that looks like a label");
        assertFalse(new Badge("3").isDisabled(),
                "and there is no state for a disabled chip to be in");
        assertEquals(List.of(), new Badge("3").children());
    }

    /// The variants are the aurora hues' first sanctioned appearance (§1.2), so
    /// every one of them has to actually select something — a class with no rule
    /// behind it is a variant that silently renders as the default.
    @Test
    @DisplayName("every variant class changes the fill on both themes")
    void everyVariantSelects() {
        for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
            var plain = styleOf(new Badge("3"), theme).background();
            for (var variant : List.of("accent", "danger", "warning", "success", "info")) {
                assertFalse(plain == styleOf(new Badge("3").styled(variant), theme).background(),
                        () -> "badge." + variant + " resolves to the default fill on " + theme);
            }
        }
    }
}
