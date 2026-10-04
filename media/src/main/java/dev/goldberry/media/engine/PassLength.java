package dev.goldberry.media.engine;

import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;

/// How long one pass over a source is, for playing it over and over: measured
/// from the packets the demux thread reads, in the source's own time.
///
/// **The picture sets the length** when there is one: the end of the last
/// picture, which is what a loop made of pictures is cut to join at. The sound
/// is fitted to it at the seam, trimmed where it runs over and filled with
/// silence where it falls short. The sound's packets are no measure when there
/// is a picture: an Opus track's last packet carries the encoder's padding,
/// which is not played. With no picture, the sound's packets are all there is.
///
/// A picture whose packet does not say how long it lasts (WebM often does not)
/// lasts the spacing of the pictures before it, so the last picture of a pass
/// still has its time before the next pass's first.
final class PassLength {

    /// The furthest end of a picture whose packet says how long it lasts.
    private long videoEndNanos = Frame.NO_PTS;
    /// The latest start of a picture whose packet does not.
    private long videoUntimedStartNanos = Frame.NO_PTS;
    private long audioEndNanos = Frame.NO_PTS;
    private long lastVideoStartNanos = Frame.NO_PTS;
    /// The shortest spacing seen between two pictures; 0 before two.
    private long videoStepNanos;

    /// Counts `packet`, of the video track.
    void video(Packet packet) {
        var start = startOf(packet);
        if (start == Frame.NO_PTS) {
            return;
        }
        if (lastVideoStartNanos != Frame.NO_PTS && start > lastVideoStartNanos) {
            var step = start - lastVideoStartNanos;
            videoStepNanos = videoStepNanos == 0 ? step : Math.min(videoStepNanos, step);
        }
        lastVideoStartNanos = start;
        if (packet.duration() > 0) {
            videoEndNanos = Math.max(videoEndNanos, start + packet.timeBase().toNanos(packet.duration()));
        } else {
            videoUntimedStartNanos = Math.max(videoUntimedStartNanos, start);
        }
    }

    /// Counts `packet`, of the audio track.
    void audio(Packet packet) {
        var end = PacketQueue.endOf(packet);
        if (end != Frame.NO_PTS) {
            audioEndNanos = Math.max(audioEndNanos, end);
        }
    }

    /// How long a pass is: the end of the last picture, or of the last sound when
    /// there is no picture; [Frame#NO_PTS] while no packet has said.
    long nanos() {
        var untimedEnd =
                videoUntimedStartNanos == Frame.NO_PTS ? Frame.NO_PTS : videoUntimedStartNanos + videoStepNanos;
        var videoEnd = Math.max(videoEndNanos, untimedEnd);
        return videoEnd != Frame.NO_PTS ? videoEnd : audioEndNanos;
    }

    private static long startOf(Packet packet) {
        var time = packet.pts() != Packet.NO_TIMESTAMP ? packet.pts() : packet.dts();
        return time == Packet.NO_TIMESTAMP ? Frame.NO_PTS : packet.timeBase().toNanos(time);
    }
}
