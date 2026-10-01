package dev.goldberry.media.view;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlayerStatus;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.text.Text;

/// What an [AudioPlayer] keeps between builds: its subscription to the player and
/// the position timer ([FollowingState]), and its [Transport], which holds the
/// icons and whether the seek bar is being dragged.
///
/// The controls are the same `media-controls` bar every media widget shows, so
/// the keys work here too. A stream's title, when it announces one, is a line
/// over them, and the error, when there is one, is a line under them.
final class AudioPlayerState extends FollowingState<AudioPlayer> {

    private final Transport transport = new Transport();

    @Override
    MediaPlayer player(AudioPlayer widget) {
        return widget.player();
    }

    @Override
    Widget build(BuildContext context, PlayerStatus status) {
        var player = widget().player();
        var parts = new ArrayList<Widget>(3);
        Transport.nowPlaying(status).ifPresent(parts::add);
        parts.add(new MediaControlsBar(
                transport.controls(player, status),
                null,
                Set.of("media-controls"),
                event -> transport.onKey(player, event)));
        status.error().ifPresent(error -> parts.add(new Text(error.message(), Attributes.NONE.classes("media-error"))));
        var root = widget().attributes()
                .classes("audio-player", "is-" + status.state().name().toLowerCase(Locale.ROOT));
        return new Column(parts, root);
    }

    @Override
    protected void dispose() {
        transport.close();
        super.dispose();
    }
}
