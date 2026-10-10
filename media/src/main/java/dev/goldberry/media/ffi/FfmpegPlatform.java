package dev.goldberry.media.ffi;

import java.util.Locale;
import java.util.Objects;

/// Which of the four native targets this process runs on, and what FFmpeg's
/// libraries are called there.
///
/// The same four targets `:natives` publishes: `linux-x64`, `linux-aarch64`,
/// `windows-x64` and `macos-aarch64`. The detection repeats `NativePlatform`'s
/// rather than importing it. That class is in `:natives`' unexported root
/// package, which is exported to `:core` and to nobody else on purpose, and
/// exporting it to one more reader to save forty lines would widen that seal.
/// `FfmpegPlatformTest` holds the two to the same classifiers.
///
/// @param os   the operating system
/// @param arch the CPU architecture
public record FfmpegPlatform(Os os, Arch arch) {

    /// What follows each library's name: FFmpeg's `--build-suffix`, which the
    /// media superbuild configures. It keeps these libraries apart
    /// from a distribution's FFmpeg of the same major in one process: GStreamer's
    /// gst-libav, loaded after them, would otherwise link against them by soname.
    /// `FfmpegSuperbuildTest` holds it to the superbuild's.
    public static final String BUILD_SUFFIX = "-goldberry";

    /// The operating systems FFmpeg natives are built for.
    public enum Os {
        LINUX,
        MACOS,
        WINDOWS,
    }

    /// The CPU architectures FFmpeg natives are built for.
    public enum Arch {
        X64,
        AARCH64,
    }

    public FfmpegPlatform {
        Objects.requireNonNull(os, "os");
        Objects.requireNonNull(arch, "arch");
        var published = switch (os) {
            case LINUX -> true;
            case MACOS -> arch == Arch.AARCH64;
            case WINDOWS -> arch == Arch.X64;
        };
        if (!published) {
            throw new UnsupportedOperationException("no FFmpeg natives for " + classifierOf(os, arch)
                    + "; the targets are linux-x64, linux-aarch64, windows-x64 and macos-aarch64");
        }
    }

    /// The platform this JVM runs on.
    ///
    /// @throws UnsupportedOperationException on a platform with no natives
    public static FfmpegPlatform current() {
        return of(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    /// The platform named by `os.name` and `os.arch` values.
    ///
    /// @throws UnsupportedOperationException on a platform with no natives
    public static FfmpegPlatform of(String osName, String osArch) {
        var name = osName.toLowerCase(Locale.ROOT);
        Os os;
        if (name.contains("linux")) {
            os = Os.LINUX;
        } else if (name.contains("mac") || name.contains("darwin")) {
            os = Os.MACOS;
        } else if (name.contains("windows")) {
            os = Os.WINDOWS;
        } else {
            throw new UnsupportedOperationException("no FFmpeg natives for os.name=\"" + osName + "\"");
        }
        var arch = switch (osArch.toLowerCase(Locale.ROOT).trim()) {
            case "amd64", "x86_64", "x64" -> Arch.X64;
            case "aarch64", "arm64" -> Arch.AARCH64;
            default -> throw new UnsupportedOperationException("no FFmpeg natives for os.arch=\"" + osArch + "\"");
        };
        return new FfmpegPlatform(os, arch);
    }

    /// The classifier of this platform's natives jar: `macos-aarch64`.
    public String classifier() {
        return classifierOf(os, arch);
    }

    /// The file a library is installed as, **named by its major**: the name the
    /// other libraries link against, so the one to load.
    ///
    /// `libavcodec-goldberry.so.62`, `libavcodec-goldberry.62.dylib`,
    /// `avcodec-goldberry-62.dll`.
    public String fileName(FfmpegLibrary library) {
        var stem = library.stem() + BUILD_SUFFIX;
        var major = library.pinnedMajor();
        return switch (os) {
            case LINUX -> "lib" + stem + ".so." + major;
            case MACOS -> "lib" + stem + "." + major + ".dylib";
            case WINDOWS -> stem + "-" + major + ".dll";
        };
    }

    /// Where the natives jar keeps this platform's libraries and layout file.
    public String resourceDirectory() {
        return "/dev/goldberry/media/natives/" + classifier() + "/";
    }

    private static String classifierOf(Os os, Arch arch) {
        return os.name().toLowerCase(Locale.ROOT) + "-" + arch.name().toLowerCase(Locale.ROOT);
    }
}
