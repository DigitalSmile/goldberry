package dev.goldberry.example.ui.drawing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.example.ShowcaseStyles;
import dev.goldberry.example.docs.CardShape;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.image.Image;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.core.icon.IconView;
import dev.goldberry.widgets.panel.card.Card;

/// The Drawing screen: a card for every section it shows, each linking that
/// section, and the two canvases that answer the pointer answering it.
class DrawingChapterTest {

    private static final Map<String, DocLink> CARDS = new LinkedHashMap<>();

    static {
        CARDS.put("paths-card", DocLink.to("components/drawing", "canvas"));
        CARDS.put("pointer-card", DocLink.to("components/drawing", "canvas"));
        CARDS.put("plan-card", DocLink.to("components/drawing", "canvas"));
        CARDS.put("sticky-card", DocLink.to("components/drawing", "canvas"));
        CARDS.put("image-widget-card", DocLink.to("components/drawing", "image"));
        CARDS.put("codecs-card", DocLink.to("components/drawing", "image"));
        CARDS.put("qr-card", DocLink.to("components/drawing", "qr-code"));
        CARDS.put("drawing-icon", DocLink.to("components/drawing", "icon"));
        CARDS.put("images-card", DocLink.to("guide/text", "images"));
        CARDS.put("drawing-clipboard", DocLink.to("guide/text", "the-clipboard"));
        CARDS.put("rendered-card", DocLink.to("guide/text", "a-picture-with-no-window"));
    }

    @BeforeEach
    void renderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("the screen holds a card for each section, linking that section")
    void everyCardLinksItsSection() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 900)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(scene.root("drawing"))) {
            assertTrue(session.byId("screen-drawing").isPresent(), "the screen's root is #screen-drawing");
            CARDS.forEach((id, link) -> {
                var card = session.byId(id).orElseThrow(() -> new AssertionError("no card #" + id));
                assertEquals(Optional.of(link), CardShape.link((Card) card.widget()), id);
                assertEquals(List.of(), CardShape.problems((Card) card.widget()), id);
            });
        }
    }

    @Test
    @DisplayName("the icon card has three sizes of one icon, a named one, and a name nothing has")
    void theIconCard() {
        try (var session = session(FigureCards.icons())) {
            var row = session.byId("drawing-icon-row").orElseThrow();
            var icons = row.children().stream()
                    .map(child -> (IconView) child.widget())
                    .toList();
            assertEquals(5, icons.size());
            assertEquals(1, icons.stream().filter(IconView::isMissing).count(), "one name found nothing");
            assertEquals("Not signed in", icons.get(3).attributes().name());
        }
    }

    @Test
    @DisplayName("the pointer card draws a crosshair under the pointer, and nothing extra at rest")
    void thePointerIsFollowed() {
        try (var session = session(new PointerCard())) {
            var grid = rect(session, "pointer");
            var atRest = session.frame();
            assertTrue(samePixels(atRest, session.frame(), grid), "at rest the canvas does not change");

            session.hover("pointer");

            assertFalse(samePixels(atRest, session.frame(), grid), "the crosshair was not drawn");
        }
    }

    @Test
    @DisplayName("the plan moves under a drag and scales under the wheel")
    void thePlanPansAndZooms() {
        try (var session = session(new PlanCard())) {
            var plan = rect(session, "plan");
            var cx = plan.left() + plan.width() / 2;
            var cy = plan.top() + plan.height() / 2;
            var atRest = session.frame();

            session.wheel(cx, cy, 0, -3);
            var zoomed = session.frame();
            assertFalse(samePixels(atRest, zoomed, plan), "the wheel did not scale the plan");

            var router = session.router();
            router.pointerPressed(cx, cy, PointerEvent.Button.PRIMARY, 1);
            router.pointerMoved(cx + 40, cy + 20);
            router.pointerReleased(cx + 40, cy + 20, PointerEvent.Button.PRIMARY, 1);
            assertFalse(samePixels(zoomed, session.frame(), plan), "the drag did not move the plan");
        }
    }

    private static Session session(Widget card) {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.addAll(ShowcaseStyles.sheets());
        return Offscreen.of(560, 480).stylesheets(sheets).session(card);
    }

    /// The outermost drawn box of the node with `id`, or of what is under it.
    static LogicalRect rect(Session session, String id) {
        var target = session.byId(id).orElseThrow();
        for (var region : session.regions()) {
            if (region.owner() instanceof Element owner && within(owner, target)) {
                return region.painted();
            }
        }
        throw new AssertionError("#" + id + " was not drawn");
    }

    private static boolean within(Element node, Element ancestor) {
        for (var at = node; at != null; at = at.parent() instanceof Element parent ? parent : null) {
            if (at == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static boolean samePixels(Image a, Image b, LogicalRect rect) {
        for (var y = (int) rect.top(); y < (int) (rect.top() + rect.height()); y++) {
            for (var x = (int) rect.left(); x < (int) (rect.left() + rect.width()); x++) {
                if (a.argb(x, y) != b.argb(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }
}
