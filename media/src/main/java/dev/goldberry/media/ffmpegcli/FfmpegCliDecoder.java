package dev.goldberry.media.ffmpegcli;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.bitstream.ParameterSets;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.container.matroska.MatroskaContainer;
import dev.goldberry.media.container.matroska.MatroskaWriter;

/// One track decoded by `ffmpeg`: the Decoder SPI's send and receive over a
/// program that decodes in a process of its own ([FfmpegProcess]).
///
/// **In:** each packet is copied and queued for the writer, at most
/// [#MAX_QUEUED] of them; past that [#send] refuses, and [#receive] waits for
/// `ffmpeg` to take one or hand a frame back. Times go in shifted by
/// [#OFFSET_NANOS], since Matroska cannot hold a time before zero and an
/// encoder's priming is stamped there.
///
/// **Out:** pictures as NV12, or P010 for a track the container says has more
/// than 8 bits, which is what the frame contract takes and what `ffmpeg`
/// converts to with no loss from 4:2:0; sound as interleaved f32 in
/// `ffmpeg`'s own channel order, which is the contract's. A frame is the
/// packet that carried it out of `ffmpeg`, lent until the next call. Each comes
/// out at a millisecond, Matroska's `ffmpeg` writes no finer, so its time is
/// put back to the time of the packet that went in within a millisecond of
/// it: the exact time for a picture, and for the sound of a codec that decodes
/// a packet to a frame.
///
/// **Colour:** what `ffmpeg` writes in the output track's `Colour`, else what
/// the stream's parameter sets signal, else the built-in decoder's default:
/// BT.709 from 720 rows up, BT.601 below, limited range.
///
/// **Flushing** kills the program; the next packet starts another. **Failing:**
/// a program that exits with a status other than 0, or that ends without
/// having decoded a frame, is thrown on the next call, and the Engine walks
/// its fallback ladder.
final class FfmpegCliDecoder implements Decoder {

    /// Packets queued for `ffmpeg` before [#send] refuses another.
    static final int MAX_QUEUED = 8;

    /// Pictures decoded ahead of the Engine; each is a whole raw picture.
    static final int MAX_PICTURES = 4;

    /// Runs of samples decoded ahead of the Engine.
    static final int MAX_SOUNDS = 16;

    /// What every time is moved by on its way to `ffmpeg`, and back by on its way
    /// out: far enough that priming before zero stays after it.
    static final long OFFSET_NANOS = TimeUnit.SECONDS.toNanos(1_000);

    /// How long one wait for a frame lasts.
    private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(10);

    /// How long `ffmpeg` may take nothing and give nothing before it is taken to
    /// be stuck. Long, for a hardware decoder's start.
    static final long STALL_NANOS = TimeUnit.SECONDS.toNanos(20);

    /// How far, in microseconds, a time out of `ffmpeg` may be from the time
    /// that went in for the two to be taken as one.
    private static final long MATCH_MICROS = 1_000;

    /// The times of packets sent and not yet matched, at most this many.
    private static final int MAX_TIMES = 512;

    /// Pixel formats with more than eight bits: `yuv420p10le`, `p010le`, `gray12le`.
    private static final Pattern DEEP = Pattern.compile(".*?(9|1[0-6])(le|be)$");

    private final String codecName;
    private final MatroskaWriter.Track track;
    private final List<String> command;
    private final int maxDecoded;
    /// The layout pictures are asked for, or null for sound.
    private final @Nullable PixelFormat pictures;

    private final ParameterSets.Signal signal;
    /// The packets sent in this run of the program, by their shifted time in
    /// microseconds, each to its exact time in nanoseconds.
    private final TreeMap<Long, Long> times = new TreeMap<>();

    private @Nullable FfmpegProcess process;
    private @Nullable Packet lent;
    private boolean ending;
    private boolean ended;
    private long decoded;
    private long lastNanos;

    /// A decoder for `request`, which `binary` decodes as `claim` says, started.
    ///
    /// @throws FfmpegCliException when `ffmpeg` cannot be started
    FfmpegCliDecoder(FfmpegBinary binary, Claim claim, DecoderRequest request) {
        this.codecName = request.codecName();
        var extradata = request.extradata().byteSize() == 0
                ? new byte[0]
                : request.extradata().toArray(JAVA_BYTE);
        switch (request.params()) {
            case TrackParams.Video video -> {
                this.track = new MatroskaWriter.Video(claim.matroskaId(), extradata, video.width(), video.height());
                this.pictures = video.pixelFormat()
                                .filter(format -> DEEP.matcher(format).matches())
                                .isPresent()
                        ? PixelFormat.P010
                        : PixelFormat.NV12;
                this.maxDecoded = MAX_PICTURES;
            }
            case TrackParams.Audio audio -> {
                this.track = new MatroskaWriter.Audio(
                        claim.matroskaId(), extradata, audio.sampleRate(), audio.channels(), 0);
                this.pictures = null;
                this.maxDecoded = MAX_SOUNDS;
            }
            default -> throw new IllegalArgumentException(codecName + " is neither video nor audio");
        }
        this.signal = ParameterSets.of(request)
                .map(configuration -> configuration.shape().signal())
                .orElse(ParameterSets.Signal.UNSPECIFIED);
        this.command = command(binary, pictures, !request.softwareOnly());
        this.process = FfmpegProcess.start(command, track, MAX_QUEUED, maxDecoded);
    }

    /// The command line that decodes a track: Matroska in on the standard input,
    /// Matroska out on the standard output, nothing else read or written.
    ///
    /// @param pictures the layout pictures are asked for, or null for sound
    /// @param hardware whether `ffmpeg` may decode on the GPU
    static List<String> command(FfmpegBinary binary, @Nullable PixelFormat pictures, boolean hardware) {
        var command = new ArrayList<String>(
                List.of(binary.path().toString(), "-hide_banner", "-nostdin", "-nostats", "-loglevel", "error"));
        if (pictures != null && hardware) {
            // Frames decoded on the GPU are copied back for the raw output.
            command.addAll(List.of("-hwaccel", "auto"));
        }
        // No probing ahead: the track is described in the stream's header, and
        // `ffmpeg` sets up its output from the first decoded frame; probing would
        // hold back the first picture until twenty packets had gone in.
        command.addAll(List.of("-probesize", "32", "-analyzeduration", "0", "-fpsprobesize", "0"));
        command.addAll(List.of("-f", "matroska", "-i", "pipe:0", "-map", "0:0", "-copyts"));
        command.addAll(List.of("-map_metadata", "-1", "-map_chapters", "-1"));
        if (pictures == null) {
            command.addAll(List.of("-c:a", "pcm_f32le"));
        } else if (pictures == PixelFormat.P010) {
            // Matroska names no 10-bit raw layout, so it is written as a
            // `V_MS/VFW/FOURCC` track, and the bytes are known by their size.
            command.addAll(List.of(
                    "-fps_mode", "passthrough", "-c:v", "rawvideo", "-pix_fmt", "p010le", "-allow_raw_vfw", "1"));
        } else {
            command.addAll(List.of("-fps_mode", "passthrough", "-c:v", "rawvideo", "-pix_fmt", "nv12"));
        }
        // A Cluster per frame, written as soon as it is: over a pipe the muxer
        // holds a Cluster until it closes, by default 32 KB or a second of them.
        command.addAll(List.of("-cluster_size_limit", "1", "-flush_packets", "1", "-f", "matroska", "pipe:1"));
        return List.copyOf(command);
    }

    /// The command line this decoder runs.
    List<String> command() {
        return command;
    }

    @Override
    public boolean send(Packet packet) {
        if (ending) {
            throw new IllegalStateException("a packet after the end");
        }
        var running = running();
        var failure = running.failure();
        if (failure != null) {
            // Said now rather than after the packets it will never decode.
            throw failure;
        }
        if (!running.hasRoom()) {
            return false;
        }
        var data = packet.data();
        if (data.byteSize() == 0) {
            return true;
        }
        var nanos = packet.ptsNanos() == Frame.NO_PTS ? lastNanos : packet.ptsNanos();
        lastNanos = nanos;
        var shifted = nanos + OFFSET_NANOS;
        if (shifted < 0) {
            throw new FfmpegCliException(
                    "a " + codecName + " packet at " + nanos + " ns is further before zero than ffmpeg is fed");
        }
        times.put(micros(shifted), nanos);
        if (times.size() > MAX_TIMES) {
            times.pollFirstEntry();
        }
        running.send(new FfmpegProcess.Input.Frame(data.toArray(JAVA_BYTE), shifted, packet.keyframe()));
        return true;
    }

    @Override
    public void sendEnd() {
        ending = true;
        var running = process;
        if (running != null) {
            running.end();
        }
    }

    @Override
    public Received receive() {
        releaseLent();
        var running = process;
        if (ended || (running == null && ending)) {
            return Received.ENDED;
        }
        if (running == null) {
            return Received.NEEDS_INPUT;
        }
        var deadline = System.nanoTime() + STALL_NANOS;
        while (true) {
            var next = running.poll(0);
            if (next == null && !ending && running.hasRoom()) {
                return Received.NEEDS_INPUT;
            }
            if (next == null) {
                next = running.poll(POLL_NANOS);
            }
            if (next != null) {
                return take(running, next);
            }
            if (System.nanoTime() > deadline) {
                throw new FfmpegCliException("ffmpeg stopped " + (ending ? "draining " : "taking packets of ")
                        + codecName + " for " + TimeUnit.NANOSECONDS.toSeconds(STALL_NANOS) + " s"
                        + said(running));
            }
        }
    }

    @Override
    public void flush() {
        releaseLent();
        var running = process;
        process = null;
        if (running != null) {
            running.close();
        }
        times.clear();
        ending = false;
        ended = false;
    }

    @Override
    public void close() {
        flush();
    }

    /// The program decoding now, started if a flush stopped the last one.
    private FfmpegProcess running() {
        var running = process;
        if (running == null) {
            running = FfmpegProcess.start(command, track, MAX_QUEUED, maxDecoded);
            process = running;
        }
        return running;
    }

    private Received take(FfmpegProcess running, FfmpegProcess.Output output) {
        return switch (output) {
            case FfmpegProcess.Output.Decoded(var packet, var params, var colour) -> {
                lent = packet;
                decoded++;
                yield new Received.Decoded(frame(packet, params, colour));
            }
            case FfmpegProcess.Output.End _ -> {
                if (!ending) {
                    throw new FfmpegCliException("ffmpeg ended before its input did" + said(running));
                }
                if (decoded == 0 && !times.isEmpty()) {
                    throw new FfmpegCliException(
                            "ffmpeg decoded nothing of " + times.size() + " " + codecName + " packets" + said(running));
                }
                ended = true;
                yield Received.ENDED;
            }
            case FfmpegProcess.Output.Failed(var failure) -> throw failure;
        };
    }

    /// The frame `packet` carries, described by its track's `params` and `colour`.
    private Frame frame(Packet packet, TrackParams params, MatroskaContainer.Colour colour) {
        var nanos = time(packet);
        var format = pictures;
        return switch (params) {
            case TrackParams.Video video when format != null -> picture(packet.data(), video, format, colour, nanos);
            case TrackParams.Audio audio when format == null -> sound(packet.data(), audio, nanos);
            default ->
                throw new FfmpegCliException("ffmpeg decoded " + codecName + " to a " + params.type()
                        + " track, not the " + (format == null ? "audio" : "video") + " it was asked for");
        };
    }

    private VideoFrame picture(
            MemorySegment data,
            TrackParams.Video video,
            PixelFormat format,
            MatroskaContainer.Colour colour,
            long nanos) {
        var width = video.width();
        var height = video.height();
        if (width <= 0 || height <= 0) {
            throw new FfmpegCliException("ffmpeg wrote " + codecName + "'s pictures with no size");
        }
        var asked = format == PixelFormat.P010 ? "p010le" : "nv12";
        if (video.pixelFormat().isPresent() && !video.pixelFormat().get().equals(asked)) {
            throw new FfmpegCliException("ffmpeg wrote " + video.pixelFormat().get() + " pictures, not " + asked);
        }
        // Rows as `rawvideo` packs them: no padding, the chroma plane's sides
        // rounded up.
        var lumaStride = format.planeRowBytes(0, width);
        var chromaStride = format.planeRowBytes(1, width);
        var lumaSize = (long) lumaStride * height;
        var chromaSize = (long) chromaStride * format.planeRows(1, height);
        if (data.byteSize() != lumaSize + chromaSize) {
            throw new FfmpegCliException("ffmpeg wrote a picture of " + data.byteSize() + " bytes; a " + width + "×"
                    + height + " " + format + " picture is " + (lumaSize + chromaSize));
        }
        return new VideoFrame(
                format,
                width,
                height,
                List.of(data.asSlice(0, lumaSize), data.asSlice(lumaSize, chromaSize)),
                List.of(lumaStride, chromaStride),
                matrix(colour, signal, height),
                fullRange(colour, signal),
                nanos);
    }

    private AudioFrame sound(MemorySegment data, TrackParams.Audio audio, long nanos) {
        if (!audio.sampleFormat().orElse("").equals("flt")) {
            throw new FfmpegCliException("ffmpeg wrote " + codecName + "'s sound as "
                    + audio.sampleFormat().orElse("an unknown format") + ", not f32");
        }
        var channels = audio.channels();
        var bytesPerFrame = (long) channels * SampleFormat.F32.bytesPerSample();
        if (channels <= 0 || audio.sampleRate() <= 0 || data.byteSize() % bytesPerFrame != 0) {
            throw new FfmpegCliException("ffmpeg wrote " + data.byteSize() + " bytes of " + channels
                    + "-channel sound at " + audio.sampleRate() + " Hz");
        }
        return new AudioFrame(
                SampleFormat.F32,
                audio.sampleRate(),
                channels,
                (int) (data.byteSize() / bytesPerFrame),
                List.of(data),
                nanos);
    }

    /// The time of the frame `packet` carries: the time of the packet that went
    /// in nearest it, within a millisecond, else its own.
    private long time(Packet packet) {
        if (packet.ptsNanos() == Frame.NO_PTS) {
            return Frame.NO_PTS;
        }
        var micros = micros(packet.ptsNanos());
        var below = times.floorEntry(micros);
        var above = times.ceilingEntry(micros);
        var nearest = below == null
                ? above
                : above == null || micros - below.getKey() <= above.getKey() - micros ? below : above;
        if (nearest != null && Math.abs(nearest.getKey() - micros) <= MATCH_MICROS) {
            times.remove(nearest.getKey());
            return nearest.getValue();
        }
        return packet.ptsNanos() - OFFSET_NANOS;
    }

    /// The picture's matrix: `ffmpeg`'s `Colour`, else the stream's signal, else
    /// BT.709 from 720 rows and BT.601 below.
    static VideoFrame.ColorMatrix matrix(MatroskaContainer.Colour colour, ParameterSets.Signal signal, int height) {
        var stated = matrix(colour.matrixCoefficients());
        if (stated == null) {
            stated = matrix(signal.matrixCoefficients());
        }
        if (stated != null) {
            return stated;
        }
        return height >= 720 ? VideoFrame.ColorMatrix.BT709 : VideoFrame.ColorMatrix.BT601;
    }

    private static VideoFrame.@Nullable ColorMatrix matrix(int coefficients) {
        return switch (coefficients) {
            case 1 -> VideoFrame.ColorMatrix.BT709;
            case 5, 6 -> VideoFrame.ColorMatrix.BT601;
            case 9, 10 -> VideoFrame.ColorMatrix.BT2020;
            default -> null;
        };
    }

    /// Whether the picture is full range: `ffmpeg`'s `Colour`, else the stream's
    /// signal, else limited.
    static boolean fullRange(MatroskaContainer.Colour colour, ParameterSets.Signal signal) {
        return switch (colour.range()) {
            case 1 -> false;
            case 2 -> true;
            default -> signal.range() == ParameterSets.Signal.Range.FULL;
        };
    }

    private static long micros(long nanos) {
        return Math.floorDiv(nanos + 500, 1_000);
    }

    private static String said(FfmpegProcess running) {
        var said = running.errorOutput();
        return said.isEmpty() ? "" : ": " + said;
    }

    private void releaseLent() {
        var packet = lent;
        lent = null;
        if (packet != null) {
            packet.close();
        }
    }
}
