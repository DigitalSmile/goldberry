package dev.goldberry.example.ui.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.panel.card.Card;

/// The Audio and Video screens opened the way the window opens them, through the
/// application's own wiring: the player from the screen's document in the first
/// card, and the cards in the order a reader meets them.
@DisplayName("the media screens")
class MediaScreensTest {

    private ShowcaseScene scene;
    private Fonts fonts;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        scene = new ShowcaseScene();
        fonts = Fonts.bundled();
    }

    @AfterEach
    void tearDown() {
        if (fonts != null) {
            fonts.close();
        }
        if (scene != null) {
            scene.close();
        }
    }

    private static List<String> cardIds(MediaKind kind) {
        return switch (kind) {
            case AUDIO ->
                List.of(
                        "audio-player-card",
                        "audio-sources",
                        "audio-media-controls",
                        "audio-control",
                        "audio-status",
                        "audio-tracks",
                        "audio-java-decoder",
                        "audio-capabilities");
            case VIDEO ->
                List.of(
                        "video-player-card",
                        "video-sources",
                        "video-view-card",
                        "video-control",
                        "video-status",
                        "video-tracks",
                        "video-subtitles",
                        "video-hardware",
                        "video-capabilities");
        };
    }

    @ParameterizedTest
    @EnumSource(MediaKind.class)
    @DisplayName("has the player from its document above every card")
    void cards(MediaKind kind) {
        try (var session = Offscreen.of(1280, 900)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(scene.root(kind.id()))) {
            var screen = session.byId("screen-" + kind.id()).orElseThrow();
            var cards = walk(screen).stream()
                    .filter(element -> element.widget() instanceof Card)
                    .map(Element::id)
                    .toList();
            // A masonry deals each card to the shortest column, so the tree is in
            // column order; the player is first because it is above the wall.
            assertEquals(cardIds(kind).getFirst(), cards.getFirst());
            assertEquals(
                    cardIds(kind).stream().sorted().toList(),
                    cards.stream().sorted().toList());
            var player = kind == MediaKind.AUDIO ? "audio-player" : "video-player";
            assertTrue(
                    walk(session.byId(kind.id("player-card")).orElseThrow()).stream()
                            .anyMatch(element -> player.equals(element.id())),
                    "the player card holds #" + player + " from the document");
        }
    }

    private static List<Element> walk(Element root) {
        var all = new ArrayList<Element>();
        all.add(root);
        root.children().forEach(child -> all.addAll(walk(child)));
        return all;
    }
}
