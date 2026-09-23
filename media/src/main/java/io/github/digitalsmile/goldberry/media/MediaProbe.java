package io.github.digitalsmile.goldberry.media;

import java.io.IOException;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.media.ffi.FfmpegLibraries;
import io.github.digitalsmile.goldberry.media.ffi.FfmpegProbe;
import io.github.digitalsmile.goldberry.media.io.MediaIO;
import io.github.digitalsmile.goldberry.media.io.MediaIOProvider;
import io.github.digitalsmile.goldberry.media.io.MediaIOs;
import io.github.digitalsmile.goldberry.media.io.Source;
import io.github.digitalsmile.goldberry.media.io.UnsupportedSchemeException;

/// Says what a source holds (its tracks, their codecs and its duration) without
/// playing it.
///
/// ```java
/// var info = MediaProbe.probe(Source.of(Path.of("talk.webm")));
/// info.defaultTrack(MediaType.VIDEO).ifPresent(video -> System.out.println(video.params()));
/// ```
///
/// A probe reads the container's header and the first packets of each stream,
/// which for a local file is a few hundred kilobytes at most. It decodes nothing,
/// so a track whose codec has no decoder is still listed. That is how
/// `UNSUPPORTED_CODEC` names what it could not play.
///
/// Blocking. Call it off the UI thread.
public final class MediaProbe {

    private MediaProbe() {}

    /// Opens `source` through [MediaIOs] and probes it.
    ///
    /// @throws MediaException with [MediaError.NativesUnavailable] when FFmpeg is
    ///                        not loaded, [MediaError.UnsupportedScheme] when no
    ///                        protocol opens the source, [MediaError.Io] when it
    ///                        cannot be read, [MediaError.InvalidData] when it is
    ///                        not media, and [MediaError.Aborted] when it was
    ///                        closed during the probe
    public static MediaInfo probe(Source source) {
        return open(source, null);
    }

    /// Probes `source` opened with exactly `providers`, and no service lookup.
    ///
    /// @param providers the protocols to open it with
    /// @throws MediaException as [#probe(Source)] does
    public static MediaInfo probe(Source source, List<? extends MediaIOProvider> providers) {
        return open(source, providers);
    }

    /// Probes an already open `io`, which `source` names. `io` is not closed.
    ///
    /// For a caller that opens its own bytes, such as a test with a faulty stream
    /// or an application with an in-memory clip. Closing `io` from another thread
    /// during the probe aborts it.
    ///
    /// @throws MediaException as [#probe(Source)] does, less the scheme
    public static MediaInfo probe(Source source, MediaIO io) {
        return FfmpegProbe.probe(FfmpegLibraries.get(), source, io);
    }

    private static MediaInfo open(Source source, @Nullable List<? extends MediaIOProvider> providers) {
        // Loaded first, so a missing FFmpeg is reported before a source is opened
        // for nothing.
        var ffmpeg = FfmpegLibraries.get();
        try (MediaIO io = providers == null ? MediaIOs.open(source) : MediaIOs.open(source, providers)) {
            return FfmpegProbe.probe(ffmpeg, source, io);
        } catch (UnsupportedSchemeException e) {
            throw new MediaException(new MediaError.UnsupportedScheme(e.scheme()), e);
        } catch (IOException e) {
            var message = e.getMessage();
            throw new MediaException(new MediaError.Io(message != null ? message : e.toString()), e);
        }
    }
}
