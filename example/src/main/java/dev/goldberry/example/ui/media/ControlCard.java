package dev.goldberry.example.ui.media;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlayerStatus;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.controls.button.Button;

/// The **Driven from Java** card: the transport as method calls on the same
/// `MediaPlayer` the widgets show.
///
/// Read more: [A player](https://goldberry.dev/docs/components/media.html#a-player).
///
/// @param kind   which screen; the Video screen adds a picture back and on
/// @param player the screen's player
/// @param status what the player reported last
/// @param desk   what a press asks for, where it is more than one call
record ControlCard(MediaKind kind, MediaPlayer player, PlayerStatus status, MediaDesk desk)
        implements Widget.Stateless {

    /// The speeds the card offers, as `setRate` takes them.
    static final List<Float> SPEEDS = List.of(0.5f, 1f, 1.5f, 2f);

    @Override
    public Widget build(BuildContext context) {
        var seekable = status.seekable();
        var duration = status.duration().orElse(Duration.ZERO);
        var rows = new ArrayList<Widget>(List.of(
                MediaLines.actions(List.of(
                        new Button("Play", player::play).id(kind.id("java-play")),
                        new Button("Pause", player::pause).id(kind.id("java-pause")),
                        new Button("From the top", () -> player.seek(Duration.ZERO))
                                .disabled(!seekable)
                                .id(kind.id("java-restart")))),
                MediaLines.actions(List.of(
                        seekTo("25%", duration, 0.25, seekable),
                        seekTo("50%", duration, 0.5, seekable),
                        seekTo("75%", duration, 0.75, seekable))),
                MediaLines.actions(List.of(
                        new Button(
                                        status.muted() ? "Unmute" : "Mute",
                                        () -> player.setMuted(!player.status().muted()))
                                .id(kind.id("java-mute")),
                        new Button("Volume 25%", () -> player.setVolume(0.25f)),
                        new Button("50%", () -> player.setVolume(0.5f)),
                        new Button("100%", () -> player.setVolume(1f)))),
                speeds()));
        if (kind == MediaKind.VIDEO) {
            rows.add(steps());
        }
        return new ShowcaseCard(
                        kind.id("control"),
                        "Driven from Java",
                        "Every call on a MediaPlayer returns at once, because the engine runs on its own threads."
                                + " Play, pause, seek, the volume and the speed, from code; the speed moves the"
                                + " pitch with it, as a tape would.",
                        MediaDocs.PLAYER)
                .of(rows);
    }

    private Widget speeds() {
        return MediaLines.actions(SPEEDS.stream()
                .map(speed -> new Button("Speed " + MediaLines.speedLabel(speed), () -> desk.setSpeed(speed))
                        .id(kind.id("speed-" + Math.round(speed * 100))))
                .toList());
    }

    /// A picture back and on, which pause.
    private Widget steps() {
        var stepping = status.state().hasMedia() && status.hasVideo() && status.seekable();
        return MediaLines.actions(List.of(
                new Button("◀ Picture", () -> desk.step(-1)).disabled(!stepping).id(kind.id("step-back")),
                new Button("Picture ▶", () -> desk.step(1)).disabled(!stepping).id(kind.id("step-on"))));
    }

    private Widget seekTo(String label, Duration duration, double fraction, boolean seekable) {
        return new Button(label, () -> player.seek(Duration.ofNanos(Math.round(duration.toNanos() * fraction))))
                .disabled(!seekable);
    }
}
