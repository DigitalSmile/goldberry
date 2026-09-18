package io.github.digitalsmile.goldberry.widgets.panel.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// `card` — §5's elevated surface ([ADR-0164]).
///
/// There is very little to assert, and that is the widget: a card is a `panel`
/// whose stylesheet says "raised". `PanelsGoldenTest` carries the part that
/// matters, which is whether it *looks* raised with no shadow to raise it with.
class CardTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("a card is a container and nothing else")
    void plain() {
        var card = new Card(new Text("Disk usage"));

        assertEquals(1, card.children().size());
    }

    @Test
    @DisplayName("a card inflates from markup")
    void inflates() {
        var widget = Widgets.inflater()
                .inflate(KdlParser.parse("card id=\"c\" { text \"Inside\" }").getFirst());
        var card = assertInstanceOf(Card.class, widget);

        assertEquals("c", card.id());
        assertEquals(1, card.children().size());
    }
}
