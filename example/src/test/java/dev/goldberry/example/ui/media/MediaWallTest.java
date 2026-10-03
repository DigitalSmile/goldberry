package dev.goldberry.example.ui.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import dev.goldberry.example.media.JavaPcmDecoder;
import dev.goldberry.example.media.ShowcaseMedia;
import dev.goldberry.junit.HeadlessRuntime;
import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlaybackState;
import dev.goldberry.media.Track;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.io.MediaIOs;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.select.Select;
import dev.goldberry.widgets.controls.toggle.Toggle;
import dev.goldberry.widgets.text.Text;

/// The Audio and Video screens without a window, and with **no FFmpeg**: this
/// module's test task pins `goldberry.media.libdir` to a directory that cannot
/// exist, so every machine sees the same screens. What that state must still do
/// is say why, and keep everything that needs no decoding working.
///
/// Under [HeadlessRuntime]: the screen and the players on it post every status
/// change to the UI thread, which needs a runtime to post to.
@DisplayName("Audio and Video screens")
@ExtendWith(HeadlessRuntime.class)
class MediaWallTest {

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
        ShowcaseMedia.shutdown();
    }

    private MediaWall audioScreen() {
        return new MediaWall(MediaKind.AUDIO, player, decoder, new Text("player"));
    }

    private List<Element> mount() {
        return walk(new ElementTree(audioScreen()).root());
    }

    private List<Element> mountVideo() {
        return walk(new ElementTree(new MediaWall(MediaKind.VIDEO, videoPlayer, null, new Text("video"))).root());
    }

    private static List<Element> walk(Element root) {
        var all = new ArrayList<Element>();
        all.add(root);
        for (var child : root.children()) {
            all.addAll(walk(child));
        }
        return all;
    }

    /// The widget of `type` whose own id is `id`. A widget's id is carried down to
    /// what it builds, so the id is read off the widget rather than the element.
    private static <T> T widget(List<Element> elements, String id, Class<T> type) {
        return elements.stream()
                .map(Element::widget)
                .filter(type::isInstance)
                .filter(widget -> widget instanceof Attributed<?> attributed
                        && id.equals(attributed.attributes().id()))
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + type.getSimpleName() + " #" + id));
    }

    private static Button button(List<Element> elements, String id) {
        return widget(elements, id, Button.class);
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
    @DisplayName("has every card, and This build says FFmpeg is not loaded and how to build it")
    void cards() {
        var elements = mount();
        for (var id : List.of(
                "screen-audio",
                "audio-player-card",
                "audio-sources",
                "audio-media-controls",
                "audio-controls",
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
        widget(walk(tree.root()), "audio-source", Select.class).onChange().accept("opus");
        assertEquals(PlaybackState.IDLE, player.status().state());
        tree.flush();
        var note = textsUnder(walk(tree.root()), "audio-sources");
        assertTrue(note.contains("FFmpeg is not available"), note);
    }

    @Test
    @DisplayName("the Java decoder's switch is the provider's")
    void javaSwitch() {
        var toggle = widget(mount(), "audio-java-toggle", Toggle.class);
        assertTrue(decoder.enabled());
        toggle.onChange().accept(false);
        assertFalse(decoder.enabled());
    }

    @Test
    @DisplayName("every sample the picker lists opens through the showcase's protocols, or fails as meant")
    void samplesResolve() throws Exception {
        var protocols = List.of(new ShowcaseMedia());
        var samples = new ArrayList<>(ShowcaseMedia.AUDIO_SAMPLES);
        samples.addAll(ShowcaseMedia.VIDEO_SAMPLES);
        for (var sample : samples) {
            if (sample.key().equals("missing")) {
                continue;
            }
            try (var io = MediaIOs.open(sample.source(), protocols)) {
                assertTrue(io.read(ByteBuffer.allocate(16)) > 0, sample.key());
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
                "video-view-card",
                "video-bare",
                "video-sources",
                "video-control",
                "video-status",
                "video-tracks",
                "video-subtitles",
                "video-hardware",
                "video-capabilities")) {
            assertTrue(elements.stream().anyMatch(e -> id.equals(e.id())), "no #" + id);
        }
        assertFalse(elements.stream().anyMatch(e -> "video-java-decoder".equals(e.id())));
        for (var id : List.of(
                "video-step-back",
                "video-step-on",
                "video-subtitles-srt",
                "video-subtitles-vtt",
                "video-subtitles-file",
                "video-subtitles-hide")) {
            assertTrue(button(elements, id).disabled(), "#" + id + " with nothing open");
        }
        assertFalse(elements.stream().anyMatch(e -> e.id() != null && e.id().startsWith("audio-")));
        widget(elements, "video-source", Select.class).onChange().accept("vp9");
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
        assertEquals(
                List.of("opus", "vorbis", "mp3", "flac", "voices", "live", "served", "java", "broken", "missing"),
                audio);
        assertEquals(List.of("vp9", "subtitled", "angles", "served", "av1", "h264"), video);
        assertEquals(MediaKind.AUDIO.samples(), ShowcaseMedia.AUDIO_SAMPLES);
        assertEquals(MediaKind.VIDEO.samples(), ShowcaseMedia.VIDEO_SAMPLES);
    }

    @Test
    @DisplayName("the Audio screen has no subtitles, hardware, video view or picture-step controls")
    void audioScreenHasNoPictureControls() {
        var elements = mount();
        for (var id :
                List.of("audio-subtitles", "audio-hardware", "audio-step-back", "audio-step-on", "video-view-card")) {
            assertFalse(elements.stream().anyMatch(e -> id.equals(e.id())), "#" + id);
        }
    }

    @Test
    @DisplayName("the speed buttons set the player's rate, which it keeps for the next source")
    void speeds() {
        var elements = mountVideo();
        button(elements, "video-speed-150").onPress().run();
        assertEquals(1.5f, videoPlayer.status().rate());
        button(elements, "video-speed-50").onPress().run();
        assertEquals(0.5f, videoPlayer.status().rate());
        button(elements, "video-speed-100").onPress().run();
        assertEquals(1f, videoPlayer.status().rate());
        assertEquals(
                List.of("0.5×", "1×", "1.5×", "2×"),
                ControlCard.SPEEDS.stream().map(MediaLines::speedLabel).toList());
    }

    @Test
    @DisplayName("the Driven from Java card's volume and mute are the player's")
    void volumeAndMute() {
        var elements = mount();
        button(elements, "audio-java-mute").onPress().run();
        assertTrue(player.status().muted());
        elements.stream()
                .map(Element::widget)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(b -> b.label().equals("Volume 25%"))
                .findFirst()
                .orElseThrow()
                .onPress()
                .run();
        assertEquals(0.25f, player.status().volume());
    }

    @Test
    @DisplayName("the Hardware card's switch is the player's hardware decoding, on by default")
    void hardwareSwitch() {
        assertEquals(HardwareDecoding.AUTO, videoPlayer.hardwareDecoding());
        var toggle = widget(mountVideo(), "video-hardware-toggle", Toggle.class);
        assertTrue(toggle.on());
        toggle.onChange().accept(false);
        assertEquals(HardwareDecoding.OFF, videoPlayer.hardwareDecoding());
        toggle.onChange().accept(true);
        assertEquals(HardwareDecoding.AUTO, videoPlayer.hardwareDecoding());
        assertEquals(PlaybackState.IDLE, videoPlayer.status().state(), "nothing open, nothing reopened");
    }

    @Test
    @DisplayName("a track is named by its index, codec, title and language")
    void trackNames() {
        var params = new TrackParams.Audio(48_000, 2, Optional.empty(), OptionalInt.empty(), OptionalLong.empty());
        var titled = new Track(
                2,
                CodecId.OPUS,
                "opus",
                params,
                Optional.empty(),
                false,
                false,
                Optional.of("fra"),
                Optional.of("Une octave plus bas"));
        assertEquals("#2 opus \"Une octave plus bas\" (fra)", MediaLines.trackName(titled));
        var bare = new Track(0, CodecId.OPUS, "opus", params, Optional.empty(), true, false);
        assertEquals("#0 opus", MediaLines.trackName(bare));
    }

    @Test
    @DisplayName("the bundled subtitle files resolve through the showcase's protocols")
    void subtitleFilesResolve() throws Exception {
        for (var file : ShowcaseMedia.SUBTITLE_FILES) {
            try (var io = MediaIOs.open(file.source(), List.of(new ShowcaseMedia()))) {
                assertTrue(io.read(ByteBuffer.allocate(64)) > 0, file.key());
            }
        }
    }
}
