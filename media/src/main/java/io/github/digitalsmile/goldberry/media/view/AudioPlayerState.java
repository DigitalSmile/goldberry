package io.github.digitalsmile.goldberry.media.view;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.media.PlaybackState;
import io.github.digitalsmile.goldberry.media.PlayerStatus;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.controls.slider.Slider;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// What an [AudioPlayer] keeps between builds: its subscription to the player,
/// the position timer, and its icons.
///
/// Every build reads the player's status afresh, so what is on screen is never a
/// stale copy. What this state adds is *when* to rebuild: when the player pushes a
/// status (on the Engine's threads, so the rebuild is posted to the UI thread),
/// and every [#POSITION_INTERVAL] while playing, because position is not pushed.
final class AudioPlayerState extends State<AudioPlayer> {

    /// How often the position is read while playing: often enough that the
    /// elapsed second never lags, and rarely enough to cost nothing.
    static final Duration POSITION_INTERVAL = Duration.ofMillis(250);

    /// Icon size, the control slot's.
    static final double ICON_SIZE = 16;

    private @Nullable AutoCloseable subscription;
    private EventLoop.@Nullable Timer poll;
    /// The glyphs drawn so far, made on first use and closed on dispose: a
    /// [Button] does not close the icon it is given.
    private final EnumMap<Glyph, Icon> icons = new EnumMap<>(Glyph.class);

    @Override
    protected void initState() {
        subscribe(widget().player());
    }

    @Override
    protected void didUpdateWidget(AudioPlayer previous) {
        if (previous.player() != widget().player()) {
            unsubscribe();
            subscribe(widget().player());
        }
    }

    @Override
    public Widget build(BuildContext context) {
        var player = widget().player();
        var status = player.status();
        schedulePoll(context, status);

        var controls = new ArrayList<Widget>();
        var playing = status.state() == PlaybackState.PLAYING || status.state() == PlaybackState.BUFFERING;
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
            controls.add(new Slider(
                            0,
                            total,
                            Math.min(seconds(status.position()), total),
                            0,
                            value -> player.seek(Duration.ofNanos(Math.round(value * 1e9))))
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

        var parts = new ArrayList<Widget>();
        parts.add(new Row(controls, Attributes.NONE.classes("media-controls")));
        status.error().ifPresent(error -> parts.add(new Text(error.message(), Attributes.NONE.classes("media-error"))));
        var root = widget().attributes()
                .classes("audio-player", "is-" + status.state().name().toLowerCase(Locale.ROOT));
        return new Column(parts, root);
    }

    @Override
    protected void dispose() {
        unsubscribe();
        cancelPoll();
        icons.values().forEach(Icon::close);
        icons.clear();
        super.dispose();
    }

    /// Play from the end starts again from the top, as every player does.
    private static void toggle(MediaPlayer player) {
        switch (player.status().state()) {
            case PLAYING, BUFFERING -> player.pause();
            case ENDED -> player.seek(Duration.ZERO);
            default -> player.play();
        }
    }

    private void subscribe(MediaPlayer player) {
        subscription = player.onStatus(_ -> Goldberry.ui().execute(this::refresh));
    }

    private void unsubscribe() {
        var current = subscription;
        subscription = null;
        if (current != null) {
            try {
                current.close();
            } catch (Exception e) {
                // Removing a listener from a list does not fail; the signature says it might.
                throw new IllegalStateException(e);
            }
        }
    }

    /// Rebuilds, if still on screen. Called on the UI thread.
    private void refresh() {
        if (isMounted()) {
            setState(() -> {});
        }
    }

    private void schedulePoll(BuildContext context, PlayerStatus status) {
        if (status.state() != PlaybackState.PLAYING || poll != null) {
            return;
        }
        context.host()
                .ifPresent(host -> poll = host.after(POSITION_INTERVAL, () -> {
                    poll = null;
                    refresh();
                }));
    }

    private void cancelPoll() {
        var current = poll;
        poll = null;
        if (current != null) {
            current.cancel();
        }
    }

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

    private Icon icon(Glyph glyph) {
        return icons.computeIfAbsent(glyph, g -> Icon.bundled(g.lucide, ICON_SIZE));
    }

    private static double seconds(Duration duration) {
        return duration.toNanos() / 1e9;
    }
}
