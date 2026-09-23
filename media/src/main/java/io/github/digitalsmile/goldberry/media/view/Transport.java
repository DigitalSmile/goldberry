package io.github.digitalsmile.goldberry.media.view;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.media.PlaybackState;
import io.github.digitalsmile.goldberry.media.PlayerStatus;
import io.github.digitalsmile.goldberry.media.SeekMode;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.controls.slider.Slider;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The transport controls every media widget shows, and the keys they answer
/// (`docs/goldberry-media.md` §6): one object per mounted widget, owned by its
/// state, so `audio-player`, `media-controls` and `media-player` cannot drift
/// apart.
///
/// ## The seek bar scrubs, and settles on release
///
/// §3's "Seeking": while the bar is **dragged**, each step is a
/// [SeekMode#KEYFRAME] seek, which shows the nearest keyframe fast, and a playing
/// player is paused for the drag. On **release** ([Slider#onCommit]) one
/// [SeekMode#ACCURATE] seek lands on the exact position, and a player that was
/// playing plays on from there. A click is a press and a release, so it is one
/// scrub step and one exact seek; a key on the bar is the same.
///
/// ## The keys
///
/// | Key | Does |
/// |-----|------|
/// | `Space`, `K` | play or pause |
/// | `←` / `→` | back or forward five seconds |
/// | `↑` / `↓` | volume up or down a twentieth |
/// | `M` | mute or unmute |
/// | `Home` | back to the start |
///
/// Answered by the widget's own node, which takes focus when clicked and is where
/// a key bubbles to from any control inside it. A focused seek bar keeps its
/// arrows (they move the bar), and a focused button keeps `Space` (it presses the
/// button, which for the play button is the same thing).
final class Transport {

    /// How far `←` and `→` seek.
    static final Duration KEY_SEEK = Duration.ofSeconds(5);

    /// How far `↑` and `↓` move the volume.
    static final float KEY_VOLUME = 0.05f;

    /// Icon size, the control slot's.
    static final double ICON_SIZE = 16;

    /// The four glyphs, by their Lucide names.
    enum Glyph {
        PLAY("play"),
        PAUSE("pause"),
        VOLUME("volume-2"),
        MUTED("volume-x");

        final String lucide;

        Glyph(String lucide) {
            this.lucide = lucide;
        }
    }

    private final EnumMap<Glyph, Icon> icons = new EnumMap<>(Glyph.class);
    private boolean scrubbing;
    private boolean playAfterScrub;

    /// Whether the seek bar is being dragged: the widget shows it as scrubbing.
    boolean scrubbing() {
        return scrubbing;
    }

    /// The controls for `status`, in order: play, elapsed, the seek bar and what
    /// remains (or `LIVE`), mute, volume.
    List<Widget> controls(MediaPlayer player, PlayerStatus status) {
        var controls = new ArrayList<Widget>(6);
        var playing = status.state() == PlaybackState.PLAYING
                || status.state() == PlaybackState.BUFFERING
                || (scrubbing && playAfterScrub);
        controls.add(new Button(
                "",
                playing ? icon(Glyph.PAUSE) : icon(Glyph.PLAY),
                () -> toggle(player),
                !status.state().hasMedia(),
                Attributes.NONE.classes("media-play")));
        controls.add(new Text(MediaTime.format(status.position()), Attributes.NONE.classes("media-time")));
        var duration = status.duration();
        if (status.state().hasMedia()
                && status.seekable()
                && duration.isPresent()
                && !duration.get().isZero()) {
            var total = seconds(duration.get());
            controls.add(
                    new Slider(0, total, Math.min(seconds(status.position()), total), 0, value -> scrub(player, value))
                            .onCommit(value -> settle(player, value))
                            .withAttributes(Attributes.NONE.classes("media-seek")));
            controls.add(new Text(
                    MediaTime.remaining(status.position(), duration.get()), Attributes.NONE.classes("media-time")));
        } else if (status.state().hasMedia()) {
            controls.add(new Text("LIVE", Attributes.NONE.classes("media-live")));
        }
        controls.add(new Button(
                "",
                status.muted() ? icon(Glyph.MUTED) : icon(Glyph.VOLUME),
                () -> player.setMuted(!player.status().muted()),
                false,
                Attributes.NONE.classes("media-mute")));
        controls.add(new Slider(0, 1, status.volume(), 0, value -> player.setVolume((float) value))
                .withAttributes(Attributes.NONE.classes("media-volume")));
        return List.copyOf(controls);
    }

    /// Answers the table above, and consumes what it answers.
    void onKey(MediaPlayer player, KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || !event.modifiers().none()) {
            return;
        }
        var status = player.status();
        var handled =
                switch (event.key()) {
                    case SPACE, K -> {
                        if (event.isRepeat()) {
                            yield true;
                        }
                        toggle(player);
                        yield true;
                    }
                    case LEFT -> seekBy(player, status, KEY_SEEK.negated());
                    case RIGHT -> seekBy(player, status, KEY_SEEK);
                    case UP -> volumeBy(player, status, KEY_VOLUME);
                    case DOWN -> volumeBy(player, status, -KEY_VOLUME);
                    case M -> {
                        if (!event.isRepeat()) {
                            player.setMuted(!status.muted());
                        }
                        yield true;
                    }
                    case HOME -> {
                        if (status.seekable()) {
                            player.seek(Duration.ZERO);
                        }
                        yield true;
                    }
                    default -> false;
                };
        if (handled) {
            event.consume();
        }
    }

    /// Play from the end starts again from the top, as every player does.
    static void toggle(MediaPlayer player) {
        switch (player.status().state()) {
            case PLAYING, BUFFERING -> player.pause();
            case ENDED -> player.seek(Duration.ZERO);
            default -> player.play();
        }
    }

    /// A step of a drag: pause for it on the first step, then show the keyframe.
    private void scrub(MediaPlayer player, double seconds) {
        if (!scrubbing) {
            scrubbing = true;
            var state = player.status().state();
            playAfterScrub = state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING;
            if (playAfterScrub) {
                player.pause();
            }
        }
        player.seek(nanos(seconds), SeekMode.KEYFRAME);
    }

    /// The release: the exact position, and play on if it was playing.
    private void settle(MediaPlayer player, double seconds) {
        player.seek(nanos(seconds), SeekMode.ACCURATE);
        if (scrubbing && playAfterScrub) {
            player.play();
        }
        scrubbing = false;
        playAfterScrub = false;
    }

    private static boolean seekBy(MediaPlayer player, PlayerStatus status, Duration by) {
        if (status.seekable()) {
            var target = status.position().plus(by);
            var end = status.duration().orElse(target);
            player.seek(target.isNegative() ? Duration.ZERO : target.compareTo(end) > 0 ? end : target);
        }
        // Consumed either way: a live stream still owns its arrows rather than
        // handing them to whatever scrolls around the player.
        return true;
    }

    private static boolean volumeBy(MediaPlayer player, PlayerStatus status, float by) {
        player.setVolume(Math.clamp(status.volume() + by, 0f, 1f));
        return true;
    }

    private Icon icon(Glyph glyph) {
        return icons.computeIfAbsent(glyph, g -> Icon.bundled(g.lucide, ICON_SIZE));
    }

    /// Closes the icons: a [Button] does not close the icon it is given.
    void close() {
        icons.values().forEach(Icon::close);
        icons.clear();
    }

    private static Duration nanos(double seconds) {
        return Duration.ofNanos(Math.round(seconds * 1e9));
    }

    private static double seconds(Duration duration) {
        return duration.toNanos() / 1e9;
    }
}
