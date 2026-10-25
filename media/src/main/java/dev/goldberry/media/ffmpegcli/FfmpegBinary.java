package dev.goldberry.media.ffmpegcli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;

/// An `ffmpeg` program, and the codecs it decodes.
///
/// Asked once per binary and process: `ffmpeg -hide_banner -codecs`, whose
/// lines start with six flags, the first `D` for a codec the binary decodes
/// with any of its decoders, then the codec's name. The codec list rather than
/// `-decoders`, whose names are the decoders' own (`h264_cuvid`, `libdav1d`):
/// the provider asks by codec, and `ffmpeg` picks the decoder.
///
/// @param path    the program
/// @param decodes the names of the codecs it decodes, in `ffmpeg`'s words
record FfmpegBinary(Path path, Set<String> decodes) {

    private static final Logger LOG = Logs.of(FfmpegBinary.class);

    /// How long the program may take to list its codecs.
    private static final long PROBE_SECONDS = 10;

    /// A codec's line: six flags, the first `D` or `.`, then the codec's name.
    private static final Pattern CODEC = Pattern.compile("\\s*([D.]\\S{5})\\s+(\\S+).*");

    private static final Map<Path, Optional<FfmpegBinary>> PROBED = new ConcurrentHashMap<>();

    FfmpegBinary {
        decodes = Set.copyOf(decodes);
    }

    /// The program at `path`, or empty when there is none, it cannot be run, or
    /// what it prints is not an `ffmpeg` codec list. Asked once per path.
    static Optional<FfmpegBinary> of(Path path) {
        return PROBED.computeIfAbsent(path.toAbsolutePath().normalize(), FfmpegBinary::probe);
    }

    /// Whether the program decodes the codec `ffmpegName`.
    boolean decodes(String ffmpegName) {
        return decodes.contains(ffmpegName);
    }

    private static Optional<FfmpegBinary> probe(Path path) {
        if (!Files.isRegularFile(path) || !Files.isExecutable(path)) {
            LOG.debug("no ffmpeg at {}: not an executable file", path);
            return Optional.empty();
        }
        Process process;
        try {
            process = new ProcessBuilder(path.toString(), "-hide_banner", "-codecs")
                    .redirectErrorStream(true)
                    .start();
            // Nothing to say to it: a program that reads its input sees the end.
            process.getOutputStream().close();
        } catch (IOException e) {
            LOG.warn("ffmpeg at {} could not be run: {}", path, e.getMessage());
            return Optional.empty();
        }
        // Read on a thread of its own, so that a program that never ends its output
        // is given up on after the wait below rather than hanging the caller.
        var listing = new AtomicReference<String>("");
        var reader = Thread.ofVirtual().name("ffmpeg-cli-probe").start(() -> {
            try (var in = process.getInputStream()) {
                listing.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        try {
            if (!process.waitFor(PROBE_SECONDS, TimeUnit.SECONDS)) {
                LOG.warn("ffmpeg at {} did not list its codecs within {} s", path, PROBE_SECONDS);
                process.destroyForcibly();
                return Optional.empty();
            }
            reader.join(TimeUnit.SECONDS.toMillis(PROBE_SECONDS));
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        var decodes = decoded(Objects.requireNonNullElse(listing.get(), ""));
        if (process.exitValue() != 0 || decodes.isEmpty()) {
            LOG.warn("{} is not an ffmpeg that lists codecs (exit {})", path, process.exitValue());
            return Optional.empty();
        }
        LOG.debug("ffmpeg at {} decodes {} codecs", path, decodes.size());
        return Optional.of(new FfmpegBinary(path, decodes));
    }

    /// The codecs a `-codecs` listing says are decoded: the lines after the
    /// legend's ` -------` rule whose first flag is `D`.
    static Set<String> decoded(String listing) {
        var codecs = new HashSet<String>();
        var listed = false;
        for (var line : (Iterable<String>) listing.lines()::iterator) {
            if (!listed) {
                listed = line.strip().startsWith("------");
                continue;
            }
            var codec = CODEC.matcher(line);
            if (codec.matches() && codec.group(1).charAt(0) == 'D') {
                codecs.add(codec.group(2));
            }
        }
        return codecs;
    }
}
