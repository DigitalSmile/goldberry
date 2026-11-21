package dev.goldberry.media.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.LockSupport;

import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.audio.SdlAudioDevices;
import dev.goldberry.natives.sdl.audio.SdlRecordingStream;

/// [AudioDriverProbe]'s ear: records the default sink's monitor through SDL's
/// PulseAudio driver, the same driver whatever the player uses, and writes the
/// time each click arrived (`System.nanoTime`, which is `CLOCK_MONOTONIC` and
/// so shared with the player's process) to a file, one a line.
///
/// A frame's arrival is the time of the read that took it, less the frames
/// still waiting behind it in the stream. The recorder's own latency is
/// constant, so it cancels when two players are compared.
///
/// Arguments: `<monitor name contains> <seconds> <out file>`.
final class LoopbackRecorder {

    private static final int RATE = 48_000;
    private static final float THRESHOLD = 0.25f;
    private static final long QUIET_NANOS = 200_000_000L;

    private LoopbackRecorder() {}

    private static void record(SdlRecordingStream stream, int seconds, List<Long> onsets) {
        var buffer = ByteBuffer.allocateDirect(RATE / 50 * Float.BYTES).order(ByteOrder.nativeOrder());
        stream.resume();
        System.err.println("recorder: recording");
        var end = System.nanoTime() + seconds * 1_000_000_000L;
        var lastLoud = Long.MIN_VALUE / 2;
        while (System.nanoTime() < end) {
            buffer.clear();
            var frames = stream.read(buffer);
            if (frames == 0) {
                LockSupport.parkNanos(250_000);
                continue;
            }
            var now = System.nanoTime();
            var behind = stream.availableFrames();
            for (var frame = 0; frame < frames; frame++) {
                var sample = Math.abs(buffer.getFloat(frame * Float.BYTES));
                if (sample < THRESHOLD) {
                    continue;
                }
                var at = now - (behind + frames - 1 - frame) * 1_000_000_000L / RATE;
                if (at - lastLoud > QUIET_NANOS) {
                    onsets.add(at);
                }
                lastLoud = at;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        var wanted = args[0].toLowerCase(Locale.ROOT);
        var seconds = Integer.parseInt(args[1]);
        var out = Path.of(args[2]);
        Sdl.get().setHint("SDL_AUDIO_DRIVER", "pulseaudio");
        Sdl.get().setHint("SDL_AUDIO_INCLUDE_MONITORS", "1");
        var onsets = new ArrayList<Long>();
        // The device list stays open while the stream does: an id lasts only
        // while SDL's audio subsystem is up.
        try (var devices = SdlAudioDevices.open()) {
            var listed = devices.recordingDevices();
            listed.forEach(d -> System.err.println("recorder: device " + d.id() + " " + d.name()));
            var device = listed.stream()
                    .filter(d -> d.name().toLowerCase(Locale.ROOT).contains(wanted))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("no recording device named like " + wanted))
                    .id();
            try (var stream = SdlRecordingStream.open(device, RATE, 1)) {
                record(stream, seconds, onsets);
            }
        }
        var lines = onsets.stream().map(String::valueOf).toList();
        Files.writeString(out, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        System.err.println("recorder: " + onsets.size() + " clicks");
    }
}
