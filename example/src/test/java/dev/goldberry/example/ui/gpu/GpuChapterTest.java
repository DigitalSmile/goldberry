package dev.goldberry.example.ui.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import dev.goldberry.gpu.view.Canvas3d;
import dev.goldberry.input.key.Key;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.panel.card.Card;

/// The GPU screen: a card for each section of the chapter, and an on-demand
/// canvas that is drawn again when its slider moves.
class GpuChapterTest {

    private static final Map<String, DocLink> CARDS = new LinkedHashMap<>();

    static {
        CARDS.put("gpu-frames-card", DocLink.to("components/gpu", "what-the-module-does-to-a-window"));
        CARDS.put("gpu-spinning-card", DocLink.to("components/gpu", "canvas3d"));
        CARDS.put("gpu-turned-card", DocLink.to("components/gpu", "canvas3d"));
        CARDS.put("gpu-measured-card", DocLink.to("components/gpu", "what-is-measured-and-what-is-not-yet"));
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
                        .session(scene.root("gpu"))) {
            assertTrue(session.byId("screen-gpu").isPresent(), "the screen's root is #screen-gpu");
            CARDS.forEach((id, link) -> {
                var card = session.byId(id).orElseThrow(() -> new AssertionError("no card #" + id));
                assertEquals(Optional.of(link), CardShape.link((Card) card.widget()), id);
                assertEquals(List.of(), CardShape.problems((Card) card.widget()), id);
            });
        }
    }

    @Test
    @DisplayName("moving the slider gives the on-demand canvas a new revision, and only then")
    void theSliderRedraws() {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.addAll(ShowcaseStyles.sheets());
        try (var session = Offscreen.of(560, 520).stylesheets(sheets).session(new TurnedCube())) {
            var first = revision(session);
            session.frame();
            assertEquals(first, revision(session), "a frame is not a move");

            assertTrue(session.focus("gpu-turn"), "the slider takes the keyboard");
            session.key(Key.RIGHT);

            assertEquals(first + 1, revision(session));
        }
    }

    /// The revision of the on-demand canvas, read off the widget the card built.
    private static long revision(Session session) {
        return canvasUnder(session.byId("gpu-turned-card").orElseThrow())
                .orElseThrow(() -> new AssertionError("the card holds no canvas3d"))
                .revision();
    }

    private static Optional<Canvas3d> canvasUnder(Element element) {
        if (element.widget() instanceof Canvas3d canvas) {
            return Optional.of(canvas);
        }
        return element.children().stream()
                .map(GpuChapterTest::canvasUnder)
                .flatMap(Optional::stream)
                .findFirst();
    }
}
