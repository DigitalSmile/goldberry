package io.github.digitalsmile.goldberry.media.view;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.media.PlaybackState;
import io.github.digitalsmile.goldberry.media.PlayerStatus;
import io.github.digitalsmile.goldberry.media.SeekMode;
import io.github.digitalsmile.goldberry.media.Track;
import io.github.digitalsmile.goldberry.media.codec.MediaType;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import io.github.digitalsmile.goldberry.widgets.controls.select.Select;
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
/// ## Which audio track
///
/// A source with more than one audio track gets a `select` of them in the bar
/// (`.media-audio-track`), labelled by title and language; choosing one is
/// [MediaPlayer#selectTrack]. A source with one gets none.
///
/// ## What is buffered
///
/// A network source's [PlayerStatus#bufferedRanges()] are the seek bar's
/// [Slider#spans()]: drawn in the groove under the fill, so a user sees where a
/// seek will land without waiting.
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
/// | `,` / `.` | a picture back or on, pausing |
/// | `<` / `>` (`Shift`+`,` / `.`) | slower or faster, through [#RATES] |
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

    /// The rates `<` and `>` step through: the ones every player offers.
    static final List<Float> RATES = List.of(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f);

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
    /// remains, mute, volume. A live source shows `LIVE` in place of the bar and
    /// what remains, and one that cannot seek but ends shows only what remains.
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
        var duration = status.duration().filter(length -> !length.isZero());
        if (status.state().hasMedia() && status.seekable() && duration.isPresent()) {
            var total = seconds(duration.get());
            controls.add(
                    new Slider(0, total, Math.min(seconds(status.position()), total), 0, value -> scrub(player, value))
                            .onCommit(value -> settle(player, value))
                            .spans(buffered(status))
                            .withAttributes(Attributes.NONE.classes("media-seek")));
            controls.add(new Text(
                    MediaTime.remaining(status.position(), duration.get()), Attributes.NONE.classes("media-time")));
        } else if (status.state().hasMedia() && status.live()) {
            controls.add(new Text("LIVE", Attributes.NONE.classes("media-live")));
        } else if (status.state().hasMedia() && duration.isPresent()) {
            // Plays to an end but cannot seek: a server that ignores Range. What
            // remains still means something; a bar that cannot be moved does not.
            controls.add(new Text(
                    MediaTime.remaining(status.position(), duration.get()), Attributes.NONE.classes("media-time")));
        }
        audioTracks(player, status).ifPresent(controls::add);
        if (status.rate() != 1f) {
            // Only when it is not 1, so a player at normal speed looks as it did.
            controls.add(new Text(rateLabel(status.rate()), Attributes.NONE.classes("media-rate")));
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

    /// What a network source has fetched, as the seek bar's spans, in seconds:
    /// the stretches a seek lands in without a request (§4, S3). None for a local
    /// file.
    static List<Slider.Span> buffered(PlayerStatus status) {
        return status.bufferedRanges().stream()
                .map(range -> new Slider.Span(seconds(range.start()), seconds(range.end())))
                .toList();
    }

    /// The audio track menu, when the source has more than one audio track to
    /// choose from: a `select` of their labels, choosing with
    /// [MediaPlayer#selectTrack]. None otherwise, so a player of one track looks
    /// as it always did.
    static Optional<Widget> audioTracks(MediaPlayer player, PlayerStatus status) {
        var tracks = status.info()
                .map(info -> info.tracks(MediaType.AUDIO).stream()
                        .filter(track -> !track.attachedPicture())
                        .toList())
                .orElse(List.of());
        if (tracks.size() < 2 || !status.state().hasMedia()) {
            return Optional.empty();
        }
        var locale = Locale.getDefault(Locale.Category.DISPLAY);
        var options = new ArrayList<Option>(tracks.size());
        for (var i = 0; i < tracks.size(); i++) {
            var track = tracks.get(i);
            options.add(new Option(Integer.toString(track.index()), trackLabel(track, i + 1, locale)));
        }
        var chosen = status.audioTrack()
                .map(track -> Integer.toString(track.index()))
                .orElse(null);
        Consumer<String> choose = value -> tracks.stream()
                .filter(track -> Integer.toString(track.index()).equals(value))
                .findFirst()
                .ifPresent(player::selectTrack);
        return Optional.of(new Select(chosen, choose, options.toArray(Option[]::new))
                .withAttributes(Attributes.NONE.classes("media-audio-track")));
    }

    /// What a track menu calls `track`, the `number`th of its kind: its title and
    /// language, "Director's commentary (English)", or either alone, or "Track 2".
    /// The language is named in `locale`, from the container's tag (`eng`, `fr`).
    static String trackLabel(Track track, int number, Locale locale) {
        var language = track.language().map(tag -> languageName(tag, locale));
        var title = track.title();
        if (title.isPresent() && language.isPresent()) {
            return title.get() + " (" + language.get() + ")";
        }
        return title.or(() -> language).orElse("Track " + number);
    }

    /// Two-letter ISO 639-1 codes by their three-letter ISO 639-2/T codes, which
    /// is what containers mostly write (`fra`, `deu`) and what the JDK does not
    /// name on its own; it does name 639-1 and the bibliographic 639-2/B (`fre`).
    private static final Map<String, String> ISO3 = Arrays.stream(Locale.getISOLanguages())
            .collect(Collectors.toUnmodifiableMap(
                    code -> Locale.of(code).getISO3Language(), code -> code, (first, second) -> first));

    /// The name of the language `tag` names, in `locale`, or the tag itself when
    /// it names none this JDK knows.
    static String languageName(String tag, Locale locale) {
        var code = ISO3.getOrDefault(tag.toLowerCase(Locale.ROOT), tag);
        var name = Locale.forLanguageTag(code).getDisplayLanguage(locale);
        return name.isBlank() ? tag : name;
    }

    /// The stream's title as a line of its own, when it announces one (S6).
    static Optional<Widget> nowPlaying(PlayerStatus status) {
        return status.nowPlaying().map(title -> new Text(title, Attributes.NONE.classes("media-now-playing")));
    }

    /// Answers the table above, and consumes what it answers.
    void onKey(MediaPlayer player, KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED) {
            return;
        }
        var status = player.status();
        if (event.modifiers().only(Mod.SHIFT)) {
            var handled =
                    switch (event.key()) {
                        case COMMA -> rateBy(player, status, -1);
                        case PERIOD -> rateBy(player, status, 1);
                        default -> false;
                    };
            if (handled) {
                event.consume();
            }
            return;
        }
        if (!event.modifiers().none()) {
            return;
        }
        var handled =
                switch (event.key()) {
                    case COMMA -> {
                        player.step(-1);
                        yield true;
                    }
                    case PERIOD -> {
                        player.step(1);
                        yield true;
                    }
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

    /// The next rate in [#RATES] up (`by` 1) or down (-1) from the current one;
    /// a rate between two steps goes to the nearer one in that direction.
    private static boolean rateBy(MediaPlayer player, PlayerStatus status, int by) {
        var current = status.rate();
        var next = by > 0
                ? RATES.stream().filter(rate -> rate > current).findFirst()
                : RATES.reversed().stream().filter(rate -> rate < current).findFirst();
        next.ifPresent(rate -> {
            try {
                player.setRate(rate);
            } catch (IllegalStateException e) {
                // A sink that plays at 1 only: the key does nothing, as at the
                // end of the list.
            }
        });
        return true;
    }

    /// `1.5×`, `0.75×`, `2×`: as few digits as the rate needs.
    static String rateLabel(float rate) {
        var text = BigDecimal.valueOf(rate).stripTrailingZeros().toPlainString();
        return text + "\u00d7";
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
