package io.github.digitalsmile.goldberry.widgets.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// `panel` — §5's plain surface, the one container that owns nothing.
///
/// `PanelsGoldenTest` carries what it looks like. What is here is the one thing a
/// record with two components can still get wrong: what it does with a null.
class PanelTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// **The defect this pins.** `card` and `group-box` both default their
    /// attributes in the compact constructor and `panel` did not, so
    /// `new Panel(children, null)` was accepted and then threw from [Panel#id()]
    /// — a null dereference a frame later, raised while the cascade was asking
    /// the widget what it is called rather than where the null was written.
    ///
    /// Two components mean the second is often the one a caller leaves out, and
    /// [Attributes#NONE] is exactly the word for "none of them".
    @Test
    @DisplayName("null attributes are none of them, as on a card")
    void nullAttributesAreNone() {
        var panel = new Panel(List.of(new Text("Settings")), null);

        assertSame(Attributes.NONE, panel.attributes());
        assertNull(panel.id(), "id() threw rather than answering that it has none");
        assertNull(panel.key());
        assertEquals(Set.of(), panel.classes());
    }

    /// The other null the same constructor takes, which it already handled — kept
    /// beside the one above so the pair reads as one rule.
    @Test
    @DisplayName("null children are no children")
    void nullChildrenAreNone() {
        var panel = new Panel(null, Attributes.NONE);

        assertEquals(List.of(), panel.children());
    }

    @Test
    @DisplayName("a panel is a container and styles nothing of its own")
    void plain() {
        var panel = new Panel(new Text("Settings"));

        assertEquals("panel", panel.cssType());
        assertEquals(1, panel.children().size());
    }
}
