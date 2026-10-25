package dev.goldberry.media.ffmpegcli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;

/// The patent-pool codecs, decoded by an `ffmpeg` program the user installed:
/// the last rung before a track is unsupported.
///
/// Off unless an application names the program. `ServiceLoader` makes one of
/// these with the no-argument constructor, which reads the path from the system
/// property [#PROPERTY], and supports nothing when it is unset or names no
/// program that runs:
///
/// ```
/// java -Dgoldberry.media.ffmpeg=/usr/bin/ffmpeg …
/// ```
///
/// or lists one itself:
///
/// ```java
/// var player = MediaPlayer.builder()
///         .decoderProviders(List.of(FfmpegCliProvider.of(Path.of("/usr/bin/ffmpeg"))))
///         .build();
/// ```
///
/// **What it claims:** H.264, HEVC, MPEG-2, MPEG-4 Part 2, AAC, AC-3 and E-AC-3,
/// and a few codecs only a Matroska file names (ProRes, DTS, TrueHD, ALAC and
/// others), each only where the program decodes it (`ffmpeg -codecs`, asked
/// once per program). Never a codec the published natives decode: those are
/// tested, and preferred. Its [#priority()] is below the system decoders', so
/// it is asked after them, and only the built-in decoders come later.
///
/// **How:** one `ffmpeg` per track, fed the track's packets as a Matroska stream
/// on its standard input, writing the decoded pictures (NV12, or P010 past
/// 8 bits) or samples (f32) back as another on its standard output. A program
/// that fails, or decodes nothing, makes the Engine try the next decoder.
/// [dev.goldberry.media.HardwareDecoding#OFF] keeps `ffmpeg` off the GPU; with
/// anything else it is run with `-hwaccel auto`.
///
/// Nothing of FFmpeg is linked or shipped. Whoever installs the program, and
/// the codecs built into it, answers for their licences.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public final class FfmpegCliProvider implements DecoderProvider {

    private static final Logger LOG = Logs.of(FfmpegCliProvider.class);

    /// This provider's [#name()]: what a player's status reports as the decoder.
    public static final String NAME = "ffmpeg-cli";

    /// The system property the no-argument constructor reads the program's path
    /// from: `goldberry.media.ffmpeg`.
    public static final String PROPERTY = "goldberry.media.ffmpeg";

    /// This provider's [#priority()]: below the system decoders, which answer the
    /// default, 0.
    public static final int PRIORITY = -100;

    private final @Nullable Path ffmpeg;

    /// A provider for the program [#PROPERTY] names, or one that supports nothing
    /// when it names none. What `ServiceLoader` makes.
    public FfmpegCliProvider() {
        this(fromProperty());
    }

    private FfmpegCliProvider(@Nullable Path ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    /// A provider for the program at `ffmpeg`. It supports nothing if there is no
    /// program there that runs and lists its codecs.
    public static FfmpegCliProvider of(Path ffmpeg) {
        return new FfmpegCliProvider(Objects.requireNonNull(ffmpeg, "ffmpeg"));
    }

    /// The program this provider runs, or empty when it was given none.
    public Optional<Path> ffmpeg() {
        return Optional.ofNullable(ffmpeg);
    }

    /// Whether the program is there, runs, and lists the codecs it decodes. Runs
    /// it the first time it is asked.
    public boolean available() {
        return binary().isPresent();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        return claim(request).isPresent();
    }

    @Override
    public Decoder open(DecoderRequest request) {
        var binary = binary().orElseThrow(() -> new IllegalStateException(
                "no ffmpeg to run" + (ffmpeg == null ? "; -D" + PROPERTY + " names none" : " at " + ffmpeg)));
        var claim = claim(request)
                .orElseThrow(
                        () -> new IllegalArgumentException(binary.path() + " does not decode " + request.codecName()));
        return new FfmpegCliDecoder(binary, claim, request);
    }

    private Optional<Claim> claim(DecoderRequest request) {
        var claim = Claim.of(request);
        if (claim.isEmpty()) {
            return claim;
        }
        return binary().flatMap(binary -> claim.filter(c -> binary.decodes(c.ffmpegName())));
    }

    private Optional<FfmpegBinary> binary() {
        return ffmpeg == null ? Optional.empty() : FfmpegBinary.of(ffmpeg);
    }

    private static @Nullable Path fromProperty() {
        var value = System.getProperty(PROPERTY, "").strip();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Path.of(value);
        } catch (InvalidPathException e) {
            LOG.warn("-D{}={} is not a path; ffmpeg-cli decodes nothing", PROPERTY, value);
            return null;
        }
    }
}
