package io.github.digitalsmile.goldberry.example.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.media.PlaybackState;
import io.github.digitalsmile.goldberry.media.io.MediaIOs;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.controls.select.Select;
import io.github.digitalsmile.goldberry.widgets.controls.toggle.Toggle;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The Media screen without a window, and with **no FFmpeg**: this module's test
/// task pins `goldberry.media.libdir` to a directory that cannot exist, so
/// every machine sees the same screen. What that state must still do is say
/// why, and keep everything that needs no decoding working.
@DisplayName("Media screen")
class MediaScreenTest {

    private JavaPcmDecoder decoder;
    private MediaPlayer player;

    @BeforeEach
    void setUp() {
        decoder = new JavaPcmDecoder();
        player = ShowcaseMedia.player(decoder);
    }

    @AfterEach
    void tearDown() {
        player.close();
    }

    private List<Element> mount() {
        return walk(new ElementTree(new MediaScreen(player, decoder, new Text("player"))).root());
    }

    private static List<Element> walk(Element root) {
        var all = new ArrayList<Element>();
        all.add(root);
        for (var child : root.children()) {
            all.addAll(walk(child));
        }
        return all;
    }

    private static String textsUnder(List<Element> elements, String id) {
        var root = elements.stream().filter(e -> id.equals(e.id())).findFirst().orElseThrow();
        return walk(root).stream()
                .map(Element::widget)
                .filter(Text.class::isInstance)
                .map(w -> ((Text) w).content())
                .collect(Collectors.joining(" "));
    }

    @Test
    @DisplayName("has every card, and the Capabilities card says FFmpeg is not loaded and how to build it")
    void cards() {
        var elements = mount();
        for (var id : List.of(
                "media-player-card",
                "media-sources",
                "media-control",
                "media-status",
                "media-tracks",
                "media-capabilities",
                "media-java-decoder")) {
            assertTrue(elements.stream().anyMatch(e -> id.equals(e.id())), "no #" + id);
        }
        var capabilities = textsUnder(elements, "media-capabilities");
        assertTrue(capabilities.contains("FFmpeg is not loaded"), capabilities);
        assertTrue(capabilities.contains(":media:ffmpegBuild"), capabilities);
    }

    @Test
    @DisplayName("picking a source without FFmpeg says why, and leaves the player idle")
    void pickWithoutFfmpeg() {
        var tree = new ElementTree(new MediaScreen(player, decoder, new Text("player")));
        var select = walk(tree.root()).stream()
                .map(Element::widget)
                .filter(Select.class::isInstance)
                .map(Select.class::cast)
                .findFirst()
                .orElseThrow();
        select.onChange().accept("opus");
        assertEquals(PlaybackState.IDLE, player.status().state());
        tree.flush();
        var note = textsUnder(walk(tree.root()), "media-sources");
        assertTrue(note.contains("FFmpeg is not available"), note);
    }

    @Test
    @DisplayName("the Java decoder's switch is the provider's")
    void javaSwitch() {
        var toggle = mount().stream()
                .map(Element::widget)
                .filter(Toggle.class::isInstance)
                .map(Toggle.class::cast)
                .findFirst()
                .orElseThrow();
        assertTrue(decoder.enabled());
        toggle.onChange().accept(false);
        assertFalse(decoder.enabled());
    }

    @Test
    @DisplayName("every sample the picker lists opens through the showcase's protocols, or fails as meant")
    void samplesResolve() throws Exception {
        var protocols = List.of(new ShowcaseMedia());
        for (var sample : ShowcaseMedia.SAMPLES) {
            if (sample.key().equals("missing")) {
                continue;
            }
            try (var io = MediaIOs.open(sample.source(), protocols)) {
                assertTrue(io.read(java.nio.ByteBuffer.allocate(16)) > 0, sample.key());
                assertEquals(!sample.key().equals("live"), io.isSeekable(), sample.key());
            }
        }
    }
}
