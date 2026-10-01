package dev.goldberry.media.view;

import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Goldberry;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlaybackState;
import dev.goldberry.media.PlayerStatus;
import dev.goldberry.media.TimeRange;
import dev.goldberry.media.picture.PictureForm;
import dev.goldberry.media.view.gpu.GpuVideo;
import dev.goldberry.media.view.gpu.VideoPresenter;
import dev.goldberry.render.event.EventLoop;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;

/// What every media widget's state does to follow its [MediaPlayer]: subscribe
/// to its status, and read the position while it plays.
///
/// Every build reads the player's status afresh, so what is on screen is never a
/// stale copy. What this adds is *when* to rebuild: when the player pushes a
/// status (on the Engine's threads, so the rebuild is posted to the UI thread),
/// and every [#POSITION_INTERVAL] while playing, because position is not pushed,
/// and while a network source is still fetching, paused or not, because the
/// buffered stretches on the seek bar are not pushed either.
/// A state that shows pictures ([#showsPictures()]) also rebuilds when the next
/// picture falls due, on a timer set from [MediaPlayer#untilNextPicture()]. So a
/// 25 fps video paints 25 frames a second, not one per display refresh.
///
/// While it shows pictures it is also attached to the player
/// ([MediaPlayer#attachView]) with the form it draws them in, so the player
/// never prepares a form this state cannot draw. With `:gpu` on the module path
/// it holds a [VideoPresenter] for its surface to show the pictures through,
/// and asks for planes while that places them on the GPU; otherwise, and
/// wherever the GPU cannot show them, it asks for converted pictures
/// (ADR-0484).
///
/// @param <W> the widget
abstract class FollowingState<W extends Widget> extends State<W> {

    /// How often the position is read while playing: often enough that the
    /// elapsed second never lags, and rarely enough to cost nothing.
    static final Duration POSITION_INTERVAL = Duration.ofMillis(250);

    /// The shortest wait for the next picture: a picture due now is painted on
    /// the next turn of the loop, not in a spin.
    static final Duration PICTURE_MIN_WAIT = Duration.ofMillis(2);

    /// How often to look again while playing with no picture waiting: the decoder
    /// is behind, or a seek is settling.
    static final Duration PICTURE_RETRY = Duration.ofMillis(10);

    private @Nullable AutoCloseable subscription;
    private MediaPlayer.@Nullable Attachment attachment;
    private @Nullable VideoPresenter presenter;
    private EventLoop.@Nullable Timer poll;
    private EventLoop.@Nullable Timer picturePoll;

    /// The player `widget` shows.
    abstract MediaPlayer player(W widget);

    /// Builds from `status`, read once for this build.
    abstract Widget build(BuildContext context, PlayerStatus status);

    /// Whether this widget draws the player's pictures, and so rebuilds as each
    /// falls due.
    boolean showsPictures() {
        return false;
    }

    /// What the surface places pictures on the GPU with, while this widget
    /// [#showsPictures()] and `:gpu` is here; null otherwise.
    @Nullable
    VideoPresenter presenter() {
        return presenter;
    }

    @Override
    protected void initState() {
        follow(player(widget()));
    }

    @Override
    protected void didUpdateWidget(W previous) {
        if (player(previous) != player(widget())) {
            unfollow();
            follow(player(widget()));
        }
    }

    @Override
    public final Widget build(BuildContext context) {
        var player = player(widget());
        attach(player);
        var status = player.status();
        var playing = status.state() == PlaybackState.PLAYING;
        if ((playing || fetching(status)) && poll == null) {
            context.host()
                    .ifPresent(host -> poll = host.after(POSITION_INTERVAL, () -> {
                        poll = null;
                        refresh();
                    }));
        }
        if (playing && showsPictures() && picturePoll == null) {
            var wait = player.untilNextPicture().orElse(PICTURE_RETRY);
            var delay = wait.compareTo(PICTURE_MIN_WAIT) < 0 ? PICTURE_MIN_WAIT : wait;
            context.host()
                    .ifPresent(host -> picturePoll = host.after(delay, () -> {
                        picturePoll = null;
                        refresh();
                    }));
        }
        return build(context, status);
    }

    /// Whether a network source has fetched part of the presentation and not all
    /// of it: its buffered stretches are still growing.
    static boolean fetching(PlayerStatus status) {
        if (!status.state().hasMedia() || status.bufferedRanges().isEmpty()) {
            return false;
        }
        // Ranges come only with a duration to map them onto; the guard is for
        // a status built by hand.
        return status.duration()
                .map(duration -> !status.bufferedRanges().equals(List.of(new TimeRange(Duration.ZERO, duration))))
                .orElse(true);
    }

    @Override
    protected void dispose() {
        unfollow();
        for (var timer : new EventLoop.@Nullable Timer[] {poll, picturePoll}) {
            if (timer != null) {
                timer.cancel();
            }
        }
        poll = null;
        picturePoll = null;
        super.dispose();
    }

    /// Rebuilds, if still on screen. Called on the UI thread.
    void refresh() {
        if (isMounted()) {
            setState(() -> {});
        }
    }

    private void follow(MediaPlayer player) {
        subscription = player.onStatus(_ -> Goldberry.ui().execute(this::refresh));
    }

    /// Attaches to `player` while this widget shows its pictures, and detaches
    /// when it stops, as a fullscreen copy takes over. Converted until a paint
    /// places a picture on the GPU: a view that has not been painted yet may be
    /// in a window with no GPU at all.
    private void attach(MediaPlayer player) {
        if (showsPictures() && attachment == null) {
            attachment = player.attachView(PictureForm.CONVERTED);
            presenter = GpuVideo.presenter(this::shownOnGpu);
        } else if (!showsPictures()) {
            detach();
        }
    }

    /// Called from inside a paint when the presenter starts or stops placing
    /// pictures on the GPU: planes while it does, converted while the CPU draws.
    private void shownOnGpu(boolean onGpu) {
        var current = attachment;
        if (current != null) {
            current.setForm(onGpu ? PictureForm.PLANES : PictureForm.CONVERTED);
        }
    }

    private void detach() {
        var current = attachment;
        attachment = null;
        if (current != null) {
            current.close();
        }
        var gpu = presenter;
        presenter = null;
        if (gpu != null) {
            gpu.close();
        }
    }

    private void unfollow() {
        detach();
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
}
