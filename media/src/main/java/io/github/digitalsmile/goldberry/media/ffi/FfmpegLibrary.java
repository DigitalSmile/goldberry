package io.github.digitalsmile.goldberry.media.ffi;

/// The five FFmpeg libraries the Engine binds, **in load order**, each with the
/// major version it was written against.
///
/// Load order is dependency order: a library is opened only after every library it
/// links against. That is what lets a library find its dependencies by name
/// without an rpath: glibc matches an already-loaded soname, and Windows an
/// already-loaded module name. On macOS the superbuild sets the install name to
/// `@loader_path`, so the order is a formality there.
///
/// The majors are the pin (`gradle/libs.versions.toml`, FFmpeg `n8.1.3`). A
/// struct's layout is stable within a major and not across one, so a library of
/// another major is refused before any struct is touched
/// (`docs/goldberry-media.md` §2, "Startup check").
public enum FfmpegLibrary {
    AVUTIL("avutil", 60),
    SWRESAMPLE("swresample", 6),
    SWSCALE("swscale", 9),
    AVCODEC("avcodec", 62),
    AVFORMAT("avformat", 62);

    private final String stem;
    private final int pinnedMajor;

    FfmpegLibrary(String stem, int pinnedMajor) {
        this.stem = stem;
        this.pinnedMajor = pinnedMajor;
    }

    /// The library's name without prefix, major or extension: `avcodec`.
    public String stem() {
        return stem;
    }

    /// The major this module's bindings and layouts were written against.
    public int pinnedMajor() {
        return pinnedMajor;
    }

    /// The major of a `*_version()` result, which packs
    /// `major << 16 | minor << 8 | micro` into an unsigned int.
    public static int majorOf(int version) {
        return version >>> 16;
    }

    /// `version` as FFmpeg writes it: `62.28.100`.
    public static String describe(int version) {
        return (version >>> 16) + "." + ((version >>> 8) & 0xFF) + "." + (version & 0xFF);
    }
}
