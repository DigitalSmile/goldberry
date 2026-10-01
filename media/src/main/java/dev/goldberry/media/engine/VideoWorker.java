package dev.goldberry.media.engine;

import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.ffi.Decoders;
import dev.goldberry.media.ffi.Demuxer;
import dev.goldberry.media.ffi.FfmpegDecoder;
import dev.goldberry.media.ffi.VideoConverter;
import dev.goldberry.media.picture.PictureForm;

/// The video decode thread of one [Playback] (`docs/goldberry-media.md` §3,
/// "Video decode").
///
/// Takes packets, decodes, and prepares every picture it keeps in a buffer from
/// the [FrameQueue], where it waits for the master clock. Preparing here rather
/// than at paint time is what a borrowed frame asks for: it has to be done with
/// before the decoder's next call, and the preparation is that.
///
/// ## Converted or planes
///
/// What a picture is prepared as is the playback's [PictureForm], read afresh
/// for each picture (`docs/gpu-plan.md`, D8). [PictureForm#CONVERTED] converts
/// it to premultiplied BGRA with swscale, which CPU present only blits.
/// [PictureForm#PLANES] copies its planes as they are, for a view that uploads
/// them and converts them on the GPU, and the copy is the lighter of the two
/// for this thread. The form changes between two pictures: a change to planes
/// leaves the converted pictures queued to play out, and a change back comes
/// with a seek, which flushes the planes ([Playback#setPictureForm]).
///
/// ## Seeking
///
/// An **accurate** seek shows the picture that covers the target: the last one
/// whose time is at or before it. Which one that is is known only when the next
/// one arrives, so every picture up to the target is prepared into one pending
/// buffer, overwriting the one before, and the pending picture is queued when a
/// later picture (or the end) shows it was the right one. A **keyframe** seek
/// queues the first picture decoded, which is the keyframe the demuxer landed on.
///
/// ## Pausing
///
/// A paused thread still decodes **one** picture after each seek, then waits. So
/// a paused player shows the position it was moved to, and a seek bar dragged
/// while paused shows each keyframe it passes.
///
/// ## One thread
///
/// Everything here but the constructor runs on the video thread, so its fields
/// are that thread's alone. The constructor runs on the demux thread before
/// `Thread.start`, which publishes it.
///
/// ## Retiring
///
/// A switch of video track (§6) [#retire]s this thread and starts another on the
/// same [FrameQueue]. A retired thread stops at its next check, queues and
/// reports nothing more, and closes its decoder and converter on the way out;
/// the picture it showed last stays up until the new thread's first replaces it.
///
/// ## Hardware, and falling back
///
/// The decoder is opened with the playback's [dev.goldberry.media.ffi.Hardware],
/// so the built-in one may decode on a device (ADR-0470). What it reports as its
/// name follows what it does: `ffmpeg (videotoolbox)` while pictures come from the
/// device, `ffmpeg` once they do not.
///
/// A decoder that fails mid-stream is closed and the next rung of the ladder
/// opened, a provider's or the built-in one's, on the device or in software. The
/// next decoder cannot start from where the last one stopped, since it has none
/// of the pictures the next one refers to, so the thread asks for an accurate
/// seek to the position, and drops the packets queued before it. The demuxer
/// goes back to the keyframe before the position, and the new decoder shows the
/// picture that covers it (S4). A decoder that fails before its first picture
/// since a seek, which is how a device that has no engine for the codec fails,
/// resumes from that seek's target, or from the start.
///
/// ## Late pictures
///
/// A picture whose time has already passed by a whole frame is dropped before it
/// is prepared, unless it is the first since a seek, or no packet is waiting
/// after it (ffplay's rule: the last picture of a stream is always shown).
/// Decoding cannot be skipped, since every picture after it depends on it, but
/// the conversion can.
final class VideoWorker {

    private static final Logger LOG = Logs.of(VideoWorker.class);

    /// What a picture lasts when nothing has said otherwise: 25 fps.
    static final long DEFAULT_FRAME_NANOS = 40_000_000L;

    private final Playback playback;
    private final Demuxer demuxer;
    private final int stream;
    private final PacketQueue queue;
    private final FrameQueue frames;
    private @Nullable VideoConverter converter;

    /// The Serial this thread is playing.
    private int serial;
    /// The target of the last accurate seek, until a picture past it arrives.
    private long discardBeforeNanos = Frame.NO_PTS;
    /// The picture that covers the target so far, during an accurate seek.
    private FrameQueue.@Nullable Slot pending;
    private long pendingPts;
    /// Whether a picture has been queued since the last seek (or the start).
    private boolean queuedSinceFlush;
    private long lastPts = Frame.NO_PTS;
    private long frameNanos = DEFAULT_FRAME_NANOS;
    /// The decoder's name as last reported, so a change is reported once.
    private @Nullable String reportedName;
    /// How many rungs of the fallback ladder have failed on this track.
    private int skip;
    /// Set after a fallback: the packets queued before the seek it asked for are
    /// dropped, since the new decoder cannot decode from the middle of a group of
    /// pictures. The seek's flush clears it.
    private boolean awaitingKeyframe;
    /// Where the last flush moved to: the start before the first.
    private long flushTargetNanos;
    /// Set when another track takes over: the thread stops at its next check,
    /// queues nothing more, and reports nothing more.
    private volatile boolean retired;

    VideoWorker(Playback playback, Demuxer demuxer, int stream, PacketQueue queue, FrameQueue frames) {
        this.playback = playback;
        this.demuxer = demuxer;
        this.stream = stream;
        this.queue = queue;
        this.frames = frames;
    }

    /// Stops this thread for good, from any thread: another video track plays now.
    /// The caller then releases the frame queue's waiters and aborts the packet
    /// queue, which wake a thread waiting on either.
    void retire() {
        retired = true;
    }

    /// Whether this thread still has work: the playback runs and no other track
    /// has taken over.
    private boolean running() {
        return !playback.stopping() && !retired;
    }

    /// The thread's whole life: open the decoder, then decode until the playback
    /// stops, or another track takes over.
    void play() {
        Decoder decoder;
        try {
            var resolved = Decoders.open(
                    playback.ffmpeg(), demuxer, stream, playback.decoderProviders(), playback.hardware(), 0);
            decoder = resolved.decoder();
            report(resolved.provider());
        } catch (MediaException e) {
            playback.fail(e.error(), e);
            return;
        }
        try {
            converter = new VideoConverter(playback.ffmpeg());
            while (running()) {
                if (playback.paused() && queuedSinceFlush) {
                    var flush = queue.takeFlush();
                    if (flush == null) {
                        playback.awaitWhile(() -> !retired && playback.paused() && !queue.flushQueued());
                    } else {
                        flushTo(flush, decoder);
                    }
                    continue;
                }
                var item = queue.take(50, TimeUnit.MILLISECONDS);
                if (item == null) {
                    // Nothing to decode, nothing left to show, and more to come:
                    // the source is not keeping up.
                    if (running() && queuedSinceFlush && !queue.ended() && frames.drained()) {
                        playback.videoUnderrun();
                    }
                    continue;
                }
                switch (item) {
                    case PacketQueue.Item.Flush flush -> flushTo(flush, decoder);
                    case PacketQueue.Item.Data(var packet, var packetSerial) -> {
                        try (packet) {
                            if (packetSerial != serial || awaitingKeyframe) {
                                continue;
                            }
                            try {
                                while (!decoder.send(packet)) {
                                    drainFrames(decoder);
                                }
                                drainFrames(decoder);
                            } catch (MediaException e) {
                                throw e;
                            } catch (RuntimeException e) {
                                if (retired) {
                                    return;
                                }
                                decoder = fallBack(decoder, e);
                            }
                        }
                    }
                    case PacketQueue.Item.End(var endSerial) -> {
                        if (endSerial != serial || awaitingKeyframe) {
                            continue;
                        }
                        try {
                            decoder.sendEnd();
                            drainFrames(decoder);
                        } catch (MediaException e) {
                            throw e;
                        } catch (RuntimeException e) {
                            // A device that fails while it drains: the same rung
                            // down, and the tail decoded again from its keyframe.
                            if (retired) {
                                return;
                            }
                            decoder = fallBack(decoder, e);
                            continue;
                        }
                        queuePending();
                        playOut();
                        decoder.flush();
                    }
                }
            }
        } catch (MediaException e) {
            if (!retired) {
                playback.fail(e.error(), e);
            }
        } catch (RuntimeException e) {
            if (!retired) {
                playback.fail(new MediaError.InvalidData(e.toString()), e);
            }
        } finally {
            if (pending != null) {
                frames.recycle(pending);
                pending = null;
            }
            if (converter != null) {
                converter.close();
            }
            decoder.close();
        }
    }

    /// The fallback ladder's mid-stream rung: closes the decoder that failed,
    /// opens the next one, and seeks back to the keyframe before where to resume,
    /// since the next decoder has none of the pictures the coming packets refer
    /// to. The packets queued before the seek are dropped until its flush.
    ///
    /// @return the next decoder
    /// @throws MediaException when no rung is left, which fails the playback
    private Decoder fallBack(Decoder failed, RuntimeException failure) {
        LOG.warn("video decoder failed mid-stream; trying the next", failure);
        failed.close();
        skip++;
        var resolved = Decoders.open(
                playback.ffmpeg(), demuxer, stream, playback.decoderProviders(), playback.hardware(), skip);
        report(resolved.provider());
        awaitingKeyframe = true;
        playback.reseek(resumeNanos());
        return resolved.decoder();
    }

    private void flushTo(PacketQueue.Item.Flush flush, Decoder decoder) {
        serial = flush.serial();
        awaitingKeyframe = false;
        flushTargetNanos = flush.targetNanos();
        decoder.flush();
        if (pending != null) {
            frames.recycle(pending);
            pending = null;
        }
        discardBeforeNanos = flush.accurate() ? flush.targetNanos() : Frame.NO_PTS;
        queuedSinceFlush = false;
        lastPts = Frame.NO_PTS;
    }

    private void drainFrames(Decoder decoder) {
        while (running()) {
            var received = decoder.receive();
            if (!(received instanceof Received.Decoded(var frame))) {
                return;
            }
            if (frame instanceof VideoFrame video) {
                picture(video);
                if (decoder instanceof FfmpegDecoder builtIn) {
                    report(builtIn.describe());
                }
            }
        }
    }

    /// Decides what one decoded picture becomes: the pending picture of an
    /// accurate seek, a dropped late one, or a queued one.
    private void picture(VideoFrame frame) {
        var pts = frame.ptsNanos() == Frame.NO_PTS
                ? (lastPts == Frame.NO_PTS ? 0 : lastPts + frameNanos)
                : frame.ptsNanos();
        if (lastPts != Frame.NO_PTS && pts > lastPts) {
            frameNanos = pts - lastPts;
            if (!retired) {
                playback.videoFrameNanos(frameNanos);
            }
        }
        lastPts = pts;
        if (!retired) {
            playback.videoDecoded();
        }

        var shape = FrameQueue.Shape.of(frame, playback.pictureForm());
        if (discardBeforeNanos != Frame.NO_PTS) {
            if (pts <= discardBeforeNanos) {
                if (pending == null) {
                    pending = frames.obtain(shape, serial, playback::presentationNanos);
                    if (pending == null) {
                        return;
                    }
                } else if (!pending.fits(shape)) {
                    frames.recycle(pending);
                    pending = frames.obtain(shape, serial, playback::presentationNanos);
                    if (pending == null) {
                        return;
                    }
                }
                prepare(frame, pending);
                pendingPts = pts;
                return;
            }
            // The first picture past the target: the pending one covered it.
            queuePending();
            discardBeforeNanos = Frame.NO_PTS;
        }

        if (queuedSinceFlush
                && queue.hasPackets()
                && !playback.paused()
                && playback.clockRunning()
                && pts + frameNanos < playback.presentationNanos()) {
            playback.videoLate();
            return;
        }
        var slot = frames.obtain(shape, serial, playback::presentationNanos);
        if (slot == null) {
            return;
        }
        prepare(frame, slot);
        queue(slot, pts);
    }

    /// Where a decoder that takes over mid-stream starts: where the clock is, or,
    /// before any picture has been shown since the last seek (a device that
    /// could not decode the first packet), that seek's target. Nothing has been
    /// played from there yet, so nothing is heard twice.
    private long resumeNanos() {
        return queuedSinceFlush ? playback.presentationNanos() : flushTargetNanos;
    }

    /// Tells the playback which decoder plays the video, when that has changed.
    private void report(String name) {
        if (!retired && !name.equals(reportedName)) {
            reportedName = name;
            playback.videoDecoder(name);
        }
    }

    private void queuePending() {
        var ready = pending;
        pending = null;
        if (ready != null) {
            queue(ready, pendingPts);
        }
    }

    private void queue(FrameQueue.Slot slot, long pts) {
        if (retired) {
            frames.recycle(slot);
            return;
        }
        if (frames.put(slot, pts, serial) && !queuedSinceFlush) {
            queuedSinceFlush = true;
            playback.videoReady(serial);
        }
    }

    /// Prepares `frame` in `slot`, in the form of the slot's shape: converted to
    /// BGRA, or its planes copied.
    private void prepare(VideoFrame frame, FrameQueue.Slot slot) {
        if (slot.shape().form() == PictureForm.PLANES) {
            slot.copyPlanes(frame);
            return;
        }
        if (converter == null) {
            throw new IllegalStateException("no converter");
        }
        converter.toBgra(frame, slot.segment(0), slot.stride(0));
    }

    /// Shows what is queued, then reports the end of the picture: the last one has
    /// been shown for as long as a picture lasts.
    private void playOut() {
        var end = lastPts == Frame.NO_PTS ? 0 : lastPts + frameNanos;
        while (running() && playback.latestSerial() == serial && !playback.seekPending()) {
            var now = playback.presentationNanos();
            frames.present(now, false);
            if (frames.drained() && now >= end) {
                playback.videoDone(serial);
                return;
            }
            Playback.sleep(5_000_000L);
        }
    }
}
