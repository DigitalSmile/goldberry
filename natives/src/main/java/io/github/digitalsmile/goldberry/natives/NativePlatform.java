package io.github.digitalsmile.goldberry.natives;

import java.util.Locale;
import java.util.Objects;

/// The platform/architecture pair identifying one row of the distribution matrix.
///
/// The [#classifier()] is the contract between three places that must agree: the
/// Gradle target ids in `:natives`, the classifier jars published per
/// `docs/ARCHITECTURE.md` §15, and the resource path [NativeLibrary] searches at
/// runtime.
///
/// **Not every pair is a row.** Four are: `linux-x64`, `linux-aarch64`,
/// `windows-x64` and `macos-aarch64`. Windows on ARM and macOS on Intel are not
/// built, so constructing them is rejected here rather than deferred to an
/// `UnsatisfiedLinkError` at load time (ADR-0041).
public record NativePlatform(OperatingSystem os, Architecture arch) {

    /// The operating systems Goldberry ships a native artifact for.
    public enum OperatingSystem {
        LINUX,
        MACOS,
        WINDOWS,
    }

    /// The architectures Goldberry ships a native artifact for. Both are 64-bit;
    /// there is no 32-bit target and there will not be one.
    public enum Architecture {
        X64,
        AARCH64,
    }

    public NativePlatform {
        Objects.requireNonNull(os, "os");
        Objects.requireNonNull(arch, "arch");
        if (!isPublished(os, arch)) {
            throw new UnsupportedOperationException(
                    "Goldberry publishes no native artifact for " + token(os) + "-" + token(arch)
                            + ". The matrix is linux-x64, linux-aarch64, windows-x64 and"
                            + " macos-aarch64 (ADR-0041).");
        }
    }

    /// Whether an artifact is built for this pair.
    ///
    /// Exhaustive over [OperatingSystem], so adding one is a compile error here
    /// rather than a silent `false`.
    private static boolean isPublished(OperatingSystem os, Architecture arch) {
        return switch (os) {
            case LINUX -> true;
            case MACOS -> arch == Architecture.AARCH64;
            case WINDOWS -> arch == Architecture.X64;
        };
    }

    /// The platform this JVM is running on.
    ///
    /// @throws UnsupportedOperationException if Goldberry has no artifact for it
    public static NativePlatform current() {
        return of(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    /// Resolves a platform from raw `os.name` / `os.arch` values.
    ///
    /// Split out from [#current()] so the mapping is testable without running on
    /// six machines.
    ///
    /// @throws UnsupportedOperationException if either value is unrecognised
    public static NativePlatform of(String osName, String osArch) {
        return new NativePlatform(operatingSystem(osName), architecture(osArch));
    }

    /// The artifact classifier, e.g. `linux-x64`.
    public String classifier() {
        return osToken() + "-" + archToken();
    }

    /// The file name of the shared library for this platform.
    public String libraryFileName() {
        return switch (os) {
            case LINUX -> "libgoldberry.so";
            case MACOS -> "libgoldberry.dylib";
            case WINDOWS -> "goldberry.dll";
        };
    }

    /// Width of a C `long` in bytes on this platform.
    ///
    /// 4 on Win64 and 8 everywhere else. This is the single most common way a
    /// hand-written binding goes wrong, so it is stated once, here, rather than
    /// assumed at each call site — and the layout probe checks it against the
    /// compiled library.
    public int cLongSize() {
        return os == OperatingSystem.WINDOWS ? 4 : 8;
    }

    private String osToken() {
        return token(os);
    }

    private String archToken() {
        return token(arch);
    }

    private static String token(OperatingSystem os) {
        return switch (os) {
            case LINUX -> "linux";
            case MACOS -> "macos";
            case WINDOWS -> "windows";
        };
    }

    private static String token(Architecture arch) {
        return switch (arch) {
            case X64 -> "x64";
            case AARCH64 -> "aarch64";
        };
    }

    private static OperatingSystem operatingSystem(String osName) {
        var normalized = normalize(osName);
        if (normalized.contains("linux")) {
            return OperatingSystem.LINUX;
        }
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return OperatingSystem.MACOS;
        }
        if (normalized.contains("windows")) {
            return OperatingSystem.WINDOWS;
        }
        throw new UnsupportedOperationException(
                "Goldberry has no native artifact for os.name=\"" + osName + "\"");
    }

    private static Architecture architecture(String osArch) {
        return switch (normalize(osArch)) {
            case "amd64", "x86_64", "x64" -> Architecture.X64;
            case "aarch64", "arm64" -> Architecture.AARCH64;
            default -> throw new UnsupportedOperationException(
                    "Goldberry has no native artifact for os.arch=\"" + osArch + "\"");
        };
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").toLowerCase(Locale.ROOT).trim();
    }
}
