package io.github.digitalsmile.goldberry.media.engine;

import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.media.MediaError;
import io.github.digitalsmile.goldberry.media.MediaException;
import io.github.digitalsmile.goldberry.media.codec.Decoder;
import io.github.digitalsmile.goldberry.media.codec.Frame;
import io.github.digitalsmile.goldberry.media.codec.Received;
import io.github.digitalsmile.goldberry.media.codec.VideoFrame;
import io.github.digitalsmile.goldberry.media.ffi.Decoders;
import io.github.digitalsmile.goldberry.media.ffi.Demuxer;
import io.github.digitalsmile.goldberry.media.ffi.VideoConverter;

/// The video decode thread of one [Playback] (`docs/goldberry-media.md` §3,
/// "Video decode").
///
/// Takes packets, decodes, and converts every picture it keeps to premultiplied
/// BGRA in a buffer from the [FrameQueue], where it waits for the master clock.
/// Converting here rather than at paint time is CPU present's shape: a borrowed
/// frame has to be done with before the decoder's next call, and the conversion
/// is that. The paint then only blits.
///
/// ## Seeking
///
/// An **accurate** seek shows the picture that covers the target: the last one
/// whose time is at or before it. Which one that is is known only when the next
/// one arrives, so every picture up to the target is converted into one pending
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
/// ## Late pictures
///
/// A picture whose time has already passed by a whole frame is dropped before it
/// is converted, unless it is the first since a seek, or no packet is waiting
/// after it (ffplay's rule: the last picture of a stream is always shown).
/// Decoding cannot be skipped, since every picture after it depends on it, but
/// the conversion can.
final class VideoWorker {

    private static final Logger LOG = Logs.of(VideoWorker.class);

    /// What a picture lasts when nothing has said otherwise: 25 fps.
    private static final long DEFAULT_FRAME_NANOS = 40_000_000L;

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

    VideoWorker(Playback playback, Demuxer demuxer, int stream, PacketQueue queue, FrameQueue frames) {
        this.playback = playback;
        this.demuxer = demuxer;
        this.stream = stream;
        this.queue = queue;
        this.frames = frames;
    }

    /// The thread's whole life: open the decoder, then decode until the playback
    /// stops.
    void play() {
        Decoder decoder;
        var skip = 0;
        try {
            var resolved = Decoders.open(playback.ffmpeg(), demuxer, stream, playback.decoderProviders(), 0);
            decoder = resolved.decoder();
            playback.videoDecoder(resolved.provider());
        } catch (MediaException e) {
            playback.fail(e.error(), e);
            return;
        }
        try {
            converter = new VideoConverter(playback.ffmpeg());
            while (!playback.stopping()) {
                if (playback.paused() && queuedSinceFlush) {
                    var flush = queue.takeFlush();
                    if (flush == null) {
                        playback.awaitWhile(() -> playback.paused() && !queue.flushQueued());
                    } else {
                        flushTo(flush, decoder);
                    }
                    continue;
                }
                var item = queue.take(50, TimeUnit.MILLISECONDS);
                if (item == null) {
                    continue;
                }
                switch (item) {
                    case PacketQueue.Item.Flush flush -> flushTo(flush, decoder);
                    case PacketQueue.Item.Data(var packet, var packetSerial) -> {
                        try (packet) {
                            if (packetSerial != serial) {
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
                                // The fallback ladder's mid-stream rung, as for audio.
                                // The next decoder starts from the next packet, which
                                // may not be a keyframe: a picture or two of damage
                                // until one arrives, rather than stopping.
                                LOG.warn("video decoder failed mid-stream; trying the next", e);
                                decoder.close();
                                skip++;
                                var resolved = Decoders.open(
                                        playback.ffmpeg(), demuxer, stream, playback.decoderProviders(), skip);
                                decoder = resolved.decoder();
                                playback.videoDecoder(resolved.provider());
                            }
                        }
                    }
                    case PacketQueue.Item.End(var endSerial) -> {
                        if (endSerial != serial) {
                            continue;
                        }
                        decoder.sendEnd();
                        drainFrames(decoder);
                        queuePending();
                        playOut();
                        decoder.flush();
                    }
                }
            }
        } catch (MediaException e) {
            playback.fail(e.error(), e);
        } catch (RuntimeException e) {
            playback.fail(new MediaError.InvalidData(e.toString()), e);
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

    private void flushTo(PacketQueue.Item.Flush flush, Decoder decoder) {
        serial = flush.serial();
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
        while (!playback.stopping()) {
            var received = decoder.receive();
            if (!(received instanceof Received.Decoded(var frame))) {
                return;
            }
            if (frame instanceof VideoFrame video) {
                picture(video);
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
        }
        lastPts = pts;

        if (discardBeforeNanos != Frame.NO_PTS) {
            if (pts <= discardBeforeNanos) {
                if (pending == null) {
                    pending = frames.obtain(frame.width(), frame.height(), serial, playback::presentationNanos);
                    if (pending == null) {
                        return;
                    }
                } else if (!pending.fits(frame.width(), frame.height())) {
                    frames.recycle(pending);
                    pending = frames.obtain(frame.width(), frame.height(), serial, playback::presentationNanos);
                    if (pending == null) {
                        return;
                    }
                }
                convert(frame, pending);
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
            return;
        }
        var slot = frames.obtain(frame.width(), frame.height(), serial, playback::presentationNanos);
        if (slot == null) {
            return;
        }
        convert(frame, slot);
        queue(slot, pts);
    }

    private void queuePending() {
        var ready = pending;
        pending = null;
        if (ready != null) {
            queue(ready, pendingPts);
        }
    }

    private void queue(FrameQueue.Slot slot, long pts) {
        if (frames.put(slot, pts, serial) && !queuedSinceFlush) {
            queuedSinceFlush = true;
            playback.videoReady(serial);
        }
    }

    private void convert(VideoFrame frame, FrameQueue.Slot slot) {
        if (converter == null) {
            throw new IllegalStateException("no converter");
        }
        converter.toBgra(frame, slot.segment(), slot.stride());
    }

    /// Shows what is queued, then reports the end of the picture: the last one has
    /// been shown for as long as a picture lasts.
    private void playOut() {
        var end = lastPts == Frame.NO_PTS ? 0 : lastPts + frameNanos;
        while (!playback.stopping() && playback.latestSerial() == serial && !playback.seekPending()) {
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
