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

/// The Audio and Video screens without a window, and with **no FFmpeg**: this
/// module's test task pins `goldberry.media.libdir` to a directory that cannot
/// exist, so every machine sees the same screens. What that state must still do
/// is say why, and keep everything that needs no decoding working.
@DisplayName("Audio and Video screens")
class MediaScreenTest {

    private JavaPcmDecoder decoder;
    private MediaPlayer player;
    private MediaPlayer videoPlayer;

    @BeforeEach
    void setUp() {
        decoder = new JavaPcmDecoder();
        player = ShowcaseMedia.audioPlayer(decoder);
        videoPlayer = ShowcaseMedia.videoPlayer();
    }

    @AfterEach
    void tearDown() {
        player.close();
        videoPlayer.close();
    }

    private List<Element> mount() {
        return walk(new ElementTree(audioScreen()).root());
    }

    private MediaScreen audioScreen() {
        return new MediaScreen(MediaScreen.Kind.AUDIO, player, decoder, new Text("player"));
    }

    private List<Element> mountVideo() {
        return walk(
                new ElementTree(new MediaScreen(MediaScreen.Kind.VIDEO, videoPlayer, null, new Text("video"))).root());
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
                "audio-player-card",
                "audio-sources",
                "audio-control",
                "audio-status",
                "audio-tracks",
                "audio-capabilities",
                "audio-java-decoder")) {
            assertTrue(elements.stream().anyMatch(e -> id.equals(e.id())), "no #" + id);
        }
        var capabilities = textsUnder(elements, "audio-capabilities");
        assertTrue(capabilities.contains("FFmpeg is not loaded"), capabilities);
        assertTrue(capabilities.contains(":media:ffmpegBuild"), capabilities);
    }

    @Test
    @DisplayName("picking a source without FFmpeg says why, and leaves the player idle")
    void pickWithoutFfmpeg() {
        var tree = new ElementTree(audioScreen());
        var select = walk(tree.root()).stream()
                .map(Element::widget)
                .filter(Select.class::isInstance)
                .map(Select.class::cast)
                .findFirst()
                .orElseThrow();
        select.onChange().accept("opus");
        assertEquals(PlaybackState.IDLE, player.status().state());
        tree.flush();
        var note = textsUnder(walk(tree.root()), "audio-sources");
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
        for (var sample : concat(ShowcaseMedia.AUDIO_SAMPLES, ShowcaseMedia.VIDEO_SAMPLES)) {
            if (sample.key().equals("missing")) {
                continue;
            }
            try (var io = MediaIOs.open(sample.source(), protocols)) {
                assertTrue(io.read(java.nio.ByteBuffer.allocate(16)) > 0, sample.key());
                assertEquals(!sample.key().equals("live"), io.isSeekable(), sample.key());
            }
        }
    }

    @Test
    @DisplayName("the Video screen has its own player, its own cards and no Java decoder card")
    void videoScreen() {
        var elements = mountVideo();
        for (var id : List.of(
                "screen-video",
                "video-player-card",
                "video-sources",
                "video-control",
                "video-status",
                "video-tracks",
                "video-capabilities")) {
            assertTrue(elements.stream().anyMatch(e -> id.equals(e.id())), "no #" + id);
        }
        assertFalse(elements.stream().anyMatch(e -> "video-java-decoder".equals(e.id())));
        assertFalse(elements.stream().anyMatch(e -> e.id() != null && e.id().startsWith("audio-")));
        var picker = elements.stream()
                .map(Element::widget)
                .filter(Select.class::isInstance)
                .map(Select.class::cast)
                .findFirst()
                .orElseThrow();
        picker.onChange().accept("vp9");
        assertEquals(PlaybackState.IDLE, videoPlayer.status().state());
        assertEquals(PlaybackState.IDLE, player.status().state(), "the Audio screen's player is not touched");
    }

    @Test
    @DisplayName("the two screens split the samples: audio on one, video and the H.264 case on the other")
    void samplesSplit() {
        var audio = ShowcaseMedia.AUDIO_SAMPLES.stream()
                .map(ShowcaseMedia.Sample::key)
                .toList();
        var video = ShowcaseMedia.VIDEO_SAMPLES.stream()
                .map(ShowcaseMedia.Sample::key)
                .toList();
        assertEquals(List.of("opus", "vorbis", "mp3", "flac", "live", "java", "broken", "missing"), audio);
        assertEquals(List.of("vp9", "av1", "h264"), video);
        assertEquals(MediaScreen.Kind.AUDIO.samples(), ShowcaseMedia.AUDIO_SAMPLES);
        assertEquals(MediaScreen.Kind.VIDEO.samples(), ShowcaseMedia.VIDEO_SAMPLES);
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        var all = new ArrayList<T>(first);
        all.addAll(second);
        return all;
    }
}
