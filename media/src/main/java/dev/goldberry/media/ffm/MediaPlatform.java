package dev.goldberry.media.ffm;

import java.util.Locale;
import java.util.Objects;

/// Which of the four native targets this process runs on, and what
/// `libgoldberry-media` is called there.
///
/// The same four targets `:natives` publishes: `linux-x64`, `linux-aarch64`,
/// `windows-x64` and `macos-aarch64`. The detection repeats `NativePlatform`'s
/// rather than importing it. That class is in `:natives`' unexported root
/// package, which is exported to `:core` and to nobody else on purpose, and
/// exporting it to one more reader to save forty lines would widen that seal.
/// `MediaPlatformTest` holds the two to the same classifiers.
///
/// @param os   the operating system
/// @param arch the CPU architecture
public record MediaPlatform(Os os, Arch arch) {

    /// The library's stem: what `System.mapLibraryName` decorates into
    /// `libgoldberry-media.so`, `libgoldberry-media.dylib` or `goldberry-media.dll`.
    public static final String LIBRARY_STEM = "goldberry-media";

    /// The operating systems the natives are built for.
    public enum Os {
        LINUX,
        MACOS,
        WINDOWS,
    }

    /// The CPU architectures the natives are built for.
    public enum Arch {
        X64,
        AARCH64,
    }

    public MediaPlatform {
        Objects.requireNonNull(os, "os");
        Objects.requireNonNull(arch, "arch");
        var published = switch (os) {
            case LINUX -> true;
            case MACOS -> arch == Arch.AARCH64;
            case WINDOWS -> arch == Arch.X64;
        };
        if (!published) {
            throw new UnsupportedOperationException("no media natives for " + classifierOf(os, arch)
                    + "; the targets are linux-x64, linux-aarch64, windows-x64 and macos-aarch64");
        }
    }

    /// The platform this JVM runs on.
    ///
    /// @throws UnsupportedOperationException on a platform with no natives
    public static MediaPlatform current() {
        return of(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    /// The platform named by `os.name` and `os.arch` values.
    ///
    /// @throws UnsupportedOperationException on a platform with no natives
    public static MediaPlatform of(String osName, String osArch) {
        var name = osName.toLowerCase(Locale.ROOT);
        Os os;
        if (name.contains("linux")) {
            os = Os.LINUX;
        } else if (name.contains("mac") || name.contains("darwin")) {
            os = Os.MACOS;
        } else if (name.contains("windows")) {
            os = Os.WINDOWS;
        } else {
            throw new UnsupportedOperationException("no media natives for os.name=\"" + osName + "\"");
        }
        var arch = switch (osArch.toLowerCase(Locale.ROOT).trim()) {
            case "amd64", "x86_64", "x64" -> Arch.X64;
            case "aarch64", "arm64" -> Arch.AARCH64;
            default -> throw new UnsupportedOperationException("no media natives for os.arch=\"" + osArch + "\"");
        };
        return new MediaPlatform(os, arch);
    }

    /// The classifier of this platform's natives jar: `natives-macos-aarch64`.
    public String classifier() {
        return "natives-" + target();
    }

    /// The target id: `macos-aarch64`.
    public String target() {
        return classifierOf(os, arch);
    }

    /// The file the library is installed as: `libgoldberry-media.so`,
    /// `libgoldberry-media.dylib`, `goldberry-media.dll`.
    public String fileName() {
        return switch (os) {
            case LINUX -> "lib" + LIBRARY_STEM + ".so";
            case MACOS -> "lib" + LIBRARY_STEM + ".dylib";
            case WINDOWS -> LIBRARY_STEM + ".dll";
        };
    }

    /// Where the natives jar keeps this platform's library and layout file.
    public String resourceDirectory() {
        return "/dev/goldberry/media/natives/" + target() + "/";
    }

    private static String classifierOf(Os os, Arch arch) {
        return os.name().toLowerCase(Locale.ROOT) + "-" + arch.name().toLowerCase(Locale.ROOT);
    }
}
