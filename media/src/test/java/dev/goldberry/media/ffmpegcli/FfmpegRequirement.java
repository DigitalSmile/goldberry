package dev.goldberry.media.ffmpegcli;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;

/// The `ffmpeg` the tests run: the one `-Dgoldberry.media.ffmpeg` names, else
/// the first on the `PATH`.
///
/// A machine without one skips these tests, always: no CI lane installs
/// `ffmpeg`, and the provider is the user's to turn on, so its absence is never
/// a failure.
final class FfmpegRequirement {

    private FfmpegRequirement() {}

    /// The program, or a skip when there is none.
    static Path ffmpeg() {
        var found = find();
        if (found.isEmpty()) {
            Assumptions.abort("no ffmpeg: neither -D" + FfmpegCliProvider.PROPERTY + " nor the PATH names one");
        }
        var provider = FfmpegCliProvider.of(found.get());
        if (!provider.available()) {
            Assumptions.abort(found.get() + " does not list its codecs");
        }
        return found.get();
    }

    private static Optional<Path> find() {
        var named = System.getProperty(FfmpegCliProvider.PROPERTY, "").strip();
        if (!named.isEmpty()) {
            return Optional.of(Path.of(named)).filter(Files::isExecutable);
        }
        var windows = System.getProperty("os.name", "").startsWith("Windows");
        for (var directory : Pattern.compile(Pattern.quote(File.pathSeparator))
                .splitAsStream(System.getenv().getOrDefault("PATH", ""))
                .toList()) {
            if (directory.isBlank()) {
                continue;
            }
            var candidate = Path.of(directory, windows ? "ffmpeg.exe" : "ffmpeg");
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
