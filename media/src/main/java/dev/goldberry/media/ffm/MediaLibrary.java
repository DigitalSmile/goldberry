package dev.goldberry.media.ffm;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import java.util.Objects;

import dev.goldberry.media.ffm.calls.Dav1dCalls;
import dev.goldberry.media.ffm.calls.FlacCalls;
import dev.goldberry.media.ffm.calls.MediaCalls;
import dev.goldberry.media.ffm.calls.Mp3Calls;
import dev.goldberry.media.ffm.calls.OpusCalls;
import dev.goldberry.media.ffm.calls.TheoraCalls;
import dev.goldberry.media.ffm.calls.VorbisCalls;
import dev.goldberry.media.ffm.calls.VpxCalls;
import dev.goldberry.media.ffm.calls.YuvCalls;

/// `libgoldberry-media`, loaded and checked: every bound function by library,
/// the constants the layout file reported, and where the library came from.
///
/// One per process, from [MediaNatives#get()]. The decoders under
/// `…media.decoder` and the converter under `…media.picture` take it; nothing
/// exported names it.
///
/// @param lookup    the loaded library, for the data symbols the holders do not
///                  cover (libyuv's matrices)
/// @param media     the two functions of the library's own
/// @param dav1d     the AV1 decoder
/// @param vpx       the VP9 and VP8 decoders
/// @param opus      the Opus decoder
/// @param vorbis    the Vorbis decoder
/// @param theora    the Theora decoder
/// @param flac      the FLAC decoder
/// @param yuv       the conversions
/// @param mp3       the MP3 decoder
/// @param constants what the layout file reported
/// @param versions  the upstream versions, as `goldberry_media_versions` says them
/// @param directory where the library was loaded from
public record MediaLibrary(
        SymbolLookup lookup,
        MediaCalls media,
        Dav1dCalls dav1d,
        VpxCalls vpx,
        OpusCalls opus,
        VorbisCalls vorbis,
        TheoraCalls theora,
        FlacCalls flac,
        YuvCalls yuv,
        Mp3Calls mp3,
        NativeConstants constants,
        String versions,
        Path directory) {

    /// The ABI these bindings are written against: `goldberry_media_abi()` has to
    /// answer it, and `media_layout.c` writes it as `abi=`.
    public static final int ABI = 3;

    public MediaLibrary {
        Objects.requireNonNull(lookup, "lookup");
        Objects.requireNonNull(media, "media");
        Objects.requireNonNull(dav1d, "dav1d");
        Objects.requireNonNull(vpx, "vpx");
        Objects.requireNonNull(opus, "opus");
        Objects.requireNonNull(vorbis, "vorbis");
        Objects.requireNonNull(theora, "theora");
        Objects.requireNonNull(flac, "flac");
        Objects.requireNonNull(yuv, "yuv");
        Objects.requireNonNull(mp3, "mp3");
        Objects.requireNonNull(constants, "constants");
        Objects.requireNonNull(versions, "versions");
        Objects.requireNonNull(directory, "directory");
    }

    /// The address of a data symbol: one of libyuv's matrices.
    ///
    /// @throws UnsatisfiedLinkError when the library does not export it
    public MemorySegment symbol(String name) {
        return Downcalls.symbol(lookup, name);
    }
}
