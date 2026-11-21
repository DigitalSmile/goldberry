package dev.goldberry.media.audio;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.audio.SdlAudioStream;

/// Playback through SDL's PulseAudio driver and through its PipeWire driver,
/// side by side on one desktop: what moves for `goldberry-media` when SDL is
/// built with PipeWire and prefers it.
///
/// For each driver, in one process:
///
/// - **A, SDL's pulls.** A bare stream kept about 100 ms ahead; every drop in
///   its queue is one pull by the device thread. Reports their sizes and the
///   time between them.
/// - **B, the sink's clock.** [SdlAudioSink] as a player uses it. The audio
///   clock is what has been written, less what is still queued, less the sink's
///   latency estimate; a straight line fitted against the wall clock gives its
///   drift, the scatter about the line its jitter, and the estimate itself is
///   reported as it stood.
/// - **C, the server's answer.** Midway, `pw-dump` is asked about this
///   process's playback node: the latency it requested and the latency PipeWire
///   reports from its port to the device, saved for a person to read. On a
///   PulseAudio path the node is `pipewire-pulse`'s, standing in for the stream.
///
/// - **D, the loopback.** A [LoopbackRecorder] in a second process records the
///   sink's monitor, always through PulseAudio, while this one plays a click
///   every half second through each driver in turn. Each click's arrival less
///   the moment the sink's clock said it would be heard is the latency the
///   sink does not count, plus the recorder's own, which is the same for both;
///   so the difference between the drivers is what moves for lip sync.
///
/// Run on purpose and read by a person:
///
///     ./gradlew :media:audioDriverProbe [-Pgoldberry.probe.drivers=pulseaudio,pipewire]
///         [-Pgoldberry.probe.monitor=<part of the monitor's name>]
final class AudioDriverProbe {

    private static final int RATE = 48_000;
    private static final int CHANNELS = 2;
    private static final int BLOCK = RATE / 100;
    private static final int AHEAD = RATE / 10;
    private static final Duration RUN = Duration.ofSeconds(12);

    private AudioDriverProbe() {}

    public static void main(String[] args) throws Exception {
        var drivers = args.length > 0 ? List.of(args[0].split(",")) : List.of("pulseaudio", "pipewire");
        System.out.printf(
                Locale.ROOT,
                "playback probe: %d Hz, %d channels, %d ms ahead, %s a run%n",
                RATE,
                CHANNELS,
                AHEAD * 1000 / RATE,
                RUN);
        for (var driver : drivers) {
            Sdl.get().setHint("SDL_AUDIO_DRIVER", driver);
            System.out.printf(Locale.ROOT, "%n== %s ==%n", driver);
            pulls(driver);
            clock();
        }
        Sdl.get().setHint("SDL_AUDIO_DRIVER", "");
        var monitor = System.getProperty("goldberry.probe.monitor");
        if (monitor != null && !monitor.isBlank()) {
            loopback(drivers, monitor);
        }
    }

    /// D: clicks through each driver, heard by a recorder that does not change.
    private static void loopback(List<String> drivers, String monitor) throws Exception {
        var seconds = 8;
        var onsetsFile = Path.of("build", "probe", "loopback-onsets.txt");
        Files.createDirectories(onsetsFile.getParent());
        var java = ProcessHandle.current().info().command().orElse("java");
        var command = new ArrayList<String>(List.of(java, "--enable-native-access=ALL-UNNAMED"));
        System.getProperties().stringPropertyNames().stream()
                .filter(name -> name.startsWith("goldberry."))
                .forEach(name -> command.add("-D" + name + "=" + System.getProperty(name)));
        command.addAll(List.of(
                "-cp",
                System.getProperty("java.class.path"),
                LoopbackRecorder.class.getName(),
                monitor,
                Integer.toString(seconds * drivers.size() + 4),
                onsetsFile.toString()));
        // Three rounds, alternating, the first a warm-up: the recorder's own
        // latency settles over its first seconds, and it sits in the same graph
        // whose quantum each player sets.
        var runs = new ArrayList<String>();
        for (var round = 0; round < 3; round++) {
            runs.addAll(drivers);
        }
        command.set(command.size() - 2, Integer.toString(seconds * runs.size() + 4));
        var recorder = new ProcessBuilder(command).inheritIO().start();
        Thread.sleep(2_000);
        var predicted = new ArrayList<List<Long>>();
        var quanta = new ArrayList<String>();
        for (var driver : runs) {
            Sdl.get().setHint("SDL_AUDIO_DRIVER", driver);
            var quantum = new String[] {"?"};
            predicted.add(clicks(seconds, quantum));
            quanta.add(quantum[0]);
        }
        Sdl.get().setHint("SDL_AUDIO_DRIVER", "");
        recorder.waitFor();
        var onsets = Files.readAllLines(onsetsFile).stream()
                .filter(line -> !line.isBlank())
                .map(Long::parseLong)
                .sorted()
                .toList();
        System.out.printf(
                Locale.ROOT, "%n== loopback through the sink's monitor (%d clicks heard) ==%n", onsets.size());
        for (var index = 0; index < runs.size(); index++) {
            var offsets = new ArrayList<Long>();
            for (var at : predicted.get(index)) {
                onsets.stream()
                        .filter(onset -> onset > at - 200_000_000L && onset < at + 300_000_000L)
                        .findFirst()
                        .ifPresent(onset -> offsets.add(onset - at));
            }
            System.out.printf(
                    Locale.ROOT,
                    "D  %-10s%s graph quantum %s: %d of %d clicks matched; heard after the sink's clock p10/p50/p90 %s ms%n",
                    runs.get(index),
                    index < drivers.size() ? " (warm-up)" : "",
                    quanta.get(index),
                    offsets.size(),
                    predicted.get(index).size(),
                    percentiles(offsets, 1e6, 10, 50, 90));
        }
    }

    /// The quantum the running output device's graph cycles at, from
    /// `pw-top`'s second report, or `?`.
    private static String graphQuantum() {
        try {
            var top = new ProcessBuilder("pw-top", "-b", "-n", "2")
                    .redirectErrorStream(true)
                    .start();
            var lines = new String(top.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList();
            top.waitFor(5, TimeUnit.SECONDS);
            for (var line : lines.reversed()) {
                var fields = line.trim().split("\\s+");
                if (fields.length > 3 && fields[0].equals("R") && line.contains("alsa_output")) {
                    return fields[2];
                }
            }
        } catch (IOException e) {
            return "? (" + e.getMessage() + ")";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "?";
    }

    /// Plays `seconds` of silence with a 2 ms click every half second, and
    /// answers when the sink's clock said each would be heard.
    private static List<Long> clicks(int seconds, String[] quantum) {
        var predicted = new ArrayList<Long>();
        try (var sink = new SdlAudioSink();
                var arena = Arena.ofConfined()) {
            sink.open(new AudioFormat(RATE, CHANNELS));
            var silence = arena.allocate(JAVA_FLOAT, (long) BLOCK * CHANNELS);
            var click = arena.allocate(JAVA_FLOAT, (long) BLOCK * CHANNELS);
            for (var frame = 0; frame < RATE / 500; frame++) {
                for (var channel = 0; channel < CHANNELS; channel++) {
                    click.setAtIndex(JAVA_FLOAT, (long) frame * CHANNELS + channel, frame % 2 == 0 ? 0.8f : -0.8f);
                }
            }
            var blocks = 0L;
            var start = System.nanoTime();
            var end = start + seconds * 1_000_000_000L;
            var asked = false;
            while (System.nanoTime() < end) {
                if (!asked && System.nanoTime() - start > (end - start) / 2) {
                    asked = true;
                    quantum[0] = graphQuantum();
                }
                while (sink.queuedSamples() < AHEAD) {
                    var loud = blocks > 100 && blocks % 50 == 0;
                    if (loud) {
                        predicted.add(
                                System.nanoTime() + sink.queuedSamples() * 1_000_000_000L / RATE + sink.latencyNanos());
                    }
                    sink.write(loud ? click : silence, BLOCK);
                    blocks++;
                }
                LockSupport.parkNanos(500_000);
            }
        }
        return predicted;
    }

    /// A: the pull pattern of a bare stream, and the server's answer midway.
    private static void pulls(String driver) throws IOException, InterruptedException {
        var sizes = new ArrayList<Long>();
        var gaps = new ArrayList<Long>();
        var block = ByteBuffer.allocateDirect(BLOCK * CHANNELS * Float.BYTES).order(ByteOrder.nativeOrder());
        sine(block, 0);
        try (var stream = SdlAudioStream.open(RATE, CHANNELS)) {
            stream.resume();
            var start = System.nanoTime();
            var end = start + RUN.toNanos();
            var asked = false;
            long last = -1;
            long lastPull = -1;
            while (System.nanoTime() < end) {
                // Read before topping up, so a pull is never hidden by a refill.
                var queued = stream.queuedFrames();
                var now = System.nanoTime();
                if (last >= 0 && queued < last) {
                    sizes.add(last - queued);
                    if (lastPull >= 0) {
                        gaps.add(now - lastPull);
                    }
                    lastPull = now;
                }
                while (stream.queuedFrames() < AHEAD) {
                    stream.put(block.duplicate());
                }
                last = stream.queuedFrames();
                if (!asked && now - start > RUN.toNanos() / 2) {
                    asked = true;
                    server(driver);
                }
                LockSupport.parkNanos(100_000);
            }
        }
        System.out.printf(
                Locale.ROOT,
                "A  pulls: %d; size p10/p50/p90 %s frames; interval p50/p90/p99 %s ms%n",
                sizes.size(),
                percentiles(sizes, 1, 10, 50, 90),
                percentiles(gaps, 1e6, 50, 90, 99));
    }

    /// B: the sink's clock against the wall clock.
    private static void clock() {
        var times = new ArrayList<double[]>();
        var estimates = new ArrayList<Long>();
        try (var sink = new SdlAudioSink();
                var arena = Arena.ofConfined()) {
            sink.open(new AudioFormat(RATE, CHANNELS));
            var block = arena.allocate(JAVA_FLOAT, (long) BLOCK * CHANNELS);
            sine(block.asByteBuffer().order(ByteOrder.nativeOrder()), 0);
            long written = 0;
            var start = System.nanoTime();
            var end = start + RUN.toNanos();
            var warm = start + TimeUnit.SECONDS.toNanos(1);
            while (System.nanoTime() < end) {
                while (sink.queuedSamples() < AHEAD) {
                    sink.write(block, BLOCK);
                    written += BLOCK;
                }
                var now = System.nanoTime();
                var latency = sink.latencyNanos();
                var heard = (written - sink.queuedSamples()) * 1e9 / RATE - latency;
                if (now > warm) {
                    times.add(new double[] {(now - start) / 1e9, heard / 1e9});
                    estimates.add(latency);
                }
                LockSupport.parkNanos(500_000);
            }
        }
        var fit = Line.fit(times);
        System.out.printf(
                Locale.ROOT,
                "B  sink: latency estimate p10/p50/p90 %s ms; clock drift %.0f ppm; "
                        + "scatter p50/p99 %.2f/%.2f ms; lead (wall - heard at t=0) %.1f ms%n",
                percentiles(estimates, 1e6, 10, 50, 90),
                (fit.slope() - 1) * 1e6,
                fit.scatter(times, 50) * 1e3,
                fit.scatter(times, 99) * 1e3,
                -fit.intercept() * 1e3);
    }

    /// C: what PipeWire says about the graph while this process plays, saved
    /// for a person to read: `build/probe/pw-dump-<driver>.json`.
    private static void server(String driver) throws IOException, InterruptedException {
        var file = Path.of("build", "probe", "pw-dump-" + driver + ".json");
        Files.createDirectories(file.getParent());
        var dump = new ProcessBuilder("pw-dump").redirectOutput(file.toFile()).start();
        if (!dump.waitFor(5, TimeUnit.SECONDS)) {
            dump.destroy();
        }
        System.out.printf(
                Locale.ROOT,
                "C  pw-dump of pid %d written to %s%n",
                ProcessHandle.current().pid(),
                file);
    }

    private static void sine(ByteBuffer block, int phase) {
        var out = block.duplicate().order(ByteOrder.nativeOrder());
        for (var frame = 0; frame < BLOCK; frame++) {
            var value = (float) (0.05 * Math.sin(2 * Math.PI * 440 * (frame + phase) / RATE));
            for (var channel = 0; channel < CHANNELS; channel++) {
                out.putFloat(value);
            }
        }
    }

    private static String percentiles(List<Long> values, double unit, int... ranks) {
        if (values.isEmpty()) {
            return "-";
        }
        var sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        var out = new StringBuilder();
        for (var rank : ranks) {
            var index = Math.min(sorted.length - 1, (int) Math.round(rank / 100.0 * (sorted.length - 1)));
            out.append(out.isEmpty() ? "" : "/")
                    .append(String.format(Locale.ROOT, unit == 1 ? "%.0f" : "%.2f", sorted[index] / unit));
        }
        return out.toString();
    }

    /// A least-squares line through (wall, heard) seconds.
    private record Line(double slope, double intercept) {

        static Line fit(List<double[]> points) {
            double n = points.size();
            double sx = 0;
            double sy = 0;
            double sxx = 0;
            double sxy = 0;
            for (var p : points) {
                sx += p[0];
                sy += p[1];
                sxx += p[0] * p[0];
                sxy += p[0] * p[1];
            }
            var slope = (n * sxy - sx * sy) / (n * sxx - sx * sx);
            return new Line(slope, (sy - slope * sx) / n);
        }

        double scatter(List<double[]> points, int rank) {
            var residuals = points.stream()
                    .mapToDouble(p -> Math.abs(p[1] - (slope * p[0] + intercept)))
                    .sorted()
                    .toArray();
            return residuals.length == 0
                    ? 0
                    : residuals[Math.min(residuals.length - 1, (int) (rank / 100.0 * residuals.length))];
        }
    }
}
