package dev.goldberry.media.ffmpegcli;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.matroska.MatroskaContainer;
import dev.goldberry.media.container.matroska.MatroskaWriter;
import dev.goldberry.media.io.Source;

/// One run of `ffmpeg` decoding one track: packets in through its standard input
/// as a Matroska stream, decoded frames out through its standard output as
/// another, and what it says on its standard error kept for a failure's message.
///
/// **Three threads**, each blocked on one pipe for most of its life: a writer
/// that takes packets off a bounded queue and writes them, a reader that demuxes
/// the output with [MatroskaContainer] into a bounded queue of frames, and one
/// that keeps the last lines of the error output. Platform threads, daemons,
/// named for a thread dump: a virtual thread blocked in a pipe's read or write
/// holds its carrier all the same (the JDK lends the scheduler another while
/// it does), so one would buy nothing here and cost a carrier per pipe.
///
/// **Ending.** The writer closes `ffmpeg`'s input after [Input.End], `ffmpeg`
/// drains and exits, and the reader queues [Output.End] for an exit status of
/// 0 or [Output.Failed] with the status and the error output for anything
/// else, as it does when the output stops short of a Matroska stream.
///
/// **Closing** kills the program, waits for it, and frees every frame still
/// queued. Whatever the threads were doing ends with the pipes.
final class FfmpegProcess implements AutoCloseable {

    private static final Logger LOG = Logs.of(FfmpegProcess.class);

    /// The source the output is opened as, for messages.
    private static final Source OUTPUT = Source.of(URI.create("pipe:ffmpeg"));

    /// How long `ffmpeg` has to exit once its output has ended, or once it was
    /// killed.
    private static final long EXIT_SECONDS = 5;

    /// The error output's lines kept, the last ones.
    private static final int ERROR_LINES = 12;

    /// What goes to `ffmpeg`.
    sealed interface Input {

        /// One packet, at `nanos` from the stream's zero.
        @SuppressWarnings("ArrayRecordComponent")
        record Frame(byte[] data, long nanos, boolean keyframe) implements Input {}

        /// No more packets: `ffmpeg`'s input is closed.
        record End() implements Input {}
    }

    /// What comes back from `ffmpeg`.
    sealed interface Output {

        /// One decoded frame, as the packet that carried it, which the receiver
        /// closes; and the output track it was read from.
        record Decoded(Packet packet, TrackParams params, MatroskaContainer.Colour colour) implements Output {}

        /// `ffmpeg` exited normally after its last frame.
        record End() implements Output {}

        /// `ffmpeg` failed, or what it wrote could not be read.
        record Failed(FfmpegCliException failure) implements Output {}
    }

    private final Process process;
    private final String program;
    private final BlockingQueue<Input> input;
    private final int maxQueued;
    private final BlockingQueue<Output> output;
    private final Deque<String> errors = new ArrayDeque<>();
    private final Thread writer;
    private final Thread reader;
    private final Thread errorReader;
    private volatile boolean closing;
    private volatile @Nullable FfmpegCliException failure;

    private FfmpegProcess(Process process, String program, MatroskaWriter.Track track, int maxQueued, int maxDecoded) {
        this.process = process;
        this.program = program;
        this.maxQueued = maxQueued;
        // One more than the packets it holds, so that the end always fits.
        this.input = new ArrayBlockingQueue<>(maxQueued + 1);
        // One more than the frames it holds, so that the end or a failure always fits.
        this.output = new ArrayBlockingQueue<>(maxDecoded + 1);
        this.writer = daemon("ffmpeg-cli-in", () -> write(track));
        this.reader = daemon("ffmpeg-cli-out", this::read);
        this.errorReader = daemon("ffmpeg-cli-err", this::readErrors);
        errorReader.start();
        writer.start();
        reader.start();
    }

    /// Starts `command`, which reads a Matroska stream of `track` on its input
    /// and writes one of decoded frames on its output, with at most `maxQueued`
    /// packets waiting to be written and `maxDecoded` frames waiting to be
    /// received.
    ///
    /// @throws FfmpegCliException when the program cannot be started
    static FfmpegProcess start(List<String> command, MatroskaWriter.Track track, int maxQueued, int maxDecoded) {
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new FfmpegCliException("could not start " + command.getFirst() + ": " + e.getMessage(), e);
        }
        LOG.debug("started {}", String.join(" ", command));
        return new FfmpegProcess(process, command.getFirst(), track, maxQueued, maxDecoded);
    }

    /// Whether another packet fits in the queue to `ffmpeg`.
    boolean hasRoom() {
        return input.size() < maxQueued;
    }

    /// Queues `frame` for `ffmpeg`.
    ///
    /// @throws IllegalStateException when there is no room ([#hasRoom])
    void send(Input.Frame frame) {
        if (!hasRoom()) {
            throw new IllegalStateException("the queue to ffmpeg is full");
        }
        input.add(frame);
    }

    /// Closes `ffmpeg`'s input once the packets queued are written.
    void end() {
        input.add(new Input.End());
    }

    /// The next output, or null when there is none within `timeoutNanos`.
    @Nullable
    Output poll(long timeoutNanos) {
        try {
            return timeoutNanos <= 0 ? output.poll() : output.poll(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FfmpegCliException("interrupted waiting for " + program, e);
        }
    }

    /// Why the program failed, once the reader has seen it fail: what an
    /// [Output.Failed] in the queue will say, known before the frames ahead of
    /// it are taken.
    @Nullable
    FfmpegCliException failure() {
        return failure;
    }

    /// The last lines `ffmpeg` wrote to its error output, one per line.
    String errorOutput() {
        synchronized (errors) {
            return String.join("\n", errors);
        }
    }

    @Override
    public void close() {
        closing = true;
        process.destroyForcibly();
        writer.interrupt();
        reader.interrupt();
        try {
            reader.join(TimeUnit.SECONDS.toMillis(EXIT_SECONDS));
            writer.join(TimeUnit.SECONDS.toMillis(EXIT_SECONDS));
            if (!process.waitFor(EXIT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warn("{} did not exit within {} s of being killed", program, EXIT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            drain();
        }
    }

    // ---- the threads -----------------------------------------------------------

    private void write(MatroskaWriter.Track track) {
        try (var stdin = new BufferedOutputStream(process.getOutputStream(), 64 * 1024)) {
            var stream = new MatroskaWriter(stdin);
            stream.writeHeader(track);
            while (true) {
                var next = input.poll();
                if (next == null) {
                    // Nothing more yet: what is written goes now, not when the buffer fills.
                    stdin.flush();
                    next = input.take();
                }
                switch (next) {
                    case Input.Frame frame -> stream.writeFrame(frame.data(), frame.nanos(), frame.keyframe());
                    case Input.End _ -> {
                        return;
                    }
                }
            }
        } catch (IOException e) {
            // ffmpeg stopped reading: it exited, and the reader says why.
            if (!closing) {
                LOG.debug("{} stopped reading its input: {}", program, e.getMessage());
            }
        } catch (InterruptedException e) {
            // Closed.
            Thread.currentThread().interrupt();
        }
    }

    private void read() {
        try (var container = MatroskaContainer.open(OUTPUT, new PipeIO(process.getInputStream()))) {
            var tracks = container.info().tracks();
            if (tracks.size() != 1) {
                fail(new FfmpegCliException(program + " wrote " + tracks.size() + " tracks, not one"));
                return;
            }
            var params = tracks.getFirst().params();
            var colour = container.colour(0);
            while (true) {
                var packet = container.read();
                if (packet == null) {
                    break;
                }
                try {
                    output.put(new Output.Decoded(packet, params, colour));
                } catch (InterruptedException e) {
                    packet.close();
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            ended(null);
        } catch (MediaException | IllegalStateException e) {
            if (!closing) {
                ended(e);
            }
        }
    }

    /// Queues how the program ended once its output has: [Output.End] for a clean
    /// exit after a whole stream, [Output.Failed] otherwise.
    private void ended(@Nullable RuntimeException readFailure) {
        if (closing) {
            return;
        }
        int status;
        try {
            if (!process.waitFor(EXIT_SECONDS, TimeUnit.SECONDS)) {
                fail(new FfmpegCliException(program + " closed its output and did not exit"));
                return;
            }
            status = process.exitValue();
            // The error output's last lines, which the program wrote before it exited.
            errorReader.join(TimeUnit.SECONDS.toMillis(1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (status == 0 && readFailure == null) {
            put(new Output.End());
            return;
        }
        var why = status != 0 ? "exited with status " + status : "wrote output that could not be read";
        var said = errorOutput();
        var message = program + " " + why + (said.isEmpty() ? "" : ": " + said);
        fail(readFailure == null ? new FfmpegCliException(message) : new FfmpegCliException(message, readFailure));
    }

    private void readErrors() {
        try (var lines = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                LOG.debug("ffmpeg: {}", line);
                synchronized (errors) {
                    if (errors.size() == ERROR_LINES) {
                        errors.removeFirst();
                    }
                    errors.addLast(line.length() > 300 ? line.substring(0, 300) + "…" : line);
                }
            }
        } catch (IOException e) {
            // The pipe closed under a killed program: nothing more to keep.
        }
    }

    private void fail(FfmpegCliException failure) {
        this.failure = failure;
        put(new Output.Failed(failure));
    }

    private void put(Output last) {
        try {
            output.put(last);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /// Frees every frame still queued.
    private void drain() {
        Output left;
        while ((left = output.poll()) != null) {
            if (left instanceof Output.Decoded(var packet, var _, var _)) {
                packet.close();
            }
        }
    }

    private static Thread daemon(String name, Runnable work) {
        return Thread.ofPlatform().name(name).daemon().unstarted(Objects.requireNonNull(work));
    }
}
