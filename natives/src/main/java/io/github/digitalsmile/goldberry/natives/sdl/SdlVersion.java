package io.github.digitalsmile.goldberry.natives.sdl;

/// The version of the SDL3 compiled into `libgoldberry`.
///
/// SDL3 encodes a version as one `int` — `SDL_VERSIONNUM(major, minor, patch)` —
/// rather than SDL2's struct out-parameter, which is why this crosses the
/// boundary as a plain `int` and is decoded here.
public record SdlVersion(int major, int minor, int patch) implements Comparable<SdlVersion> {

    /// The encoding in `SDL3/SDL_version.h`:
    /// `major * 1000000 + minor * 1000 + patch`.
    private static final int MAJOR_SCALE = 1_000_000;
    private static final int MINOR_SCALE = 1_000;

    public SdlVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException(
                    "version components must not be negative: " + major + "." + minor + "." + patch);
        }
    }

    /// Decodes what `SDL_GetVersion()` returns.
    public static SdlVersion decode(int versionNumber) {
        if (versionNumber < 0) {
            throw new IllegalArgumentException(
                    "SDL_GetVersion() returned " + versionNumber + ", which is not a version");
        }
        return new SdlVersion(
                versionNumber / MAJOR_SCALE,
                (versionNumber / MINOR_SCALE) % MINOR_SCALE,
                versionNumber % MINOR_SCALE);
    }

    /// The `SDL_VERSIONNUM` form.
    public int encode() {
        return major * MAJOR_SCALE + minor * MINOR_SCALE + patch;
    }

    /// Whether this is at least `other` — the ordinary "do we have the API we
    /// need" question.
    public boolean isAtLeast(SdlVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(SdlVersion other) {
        return Integer.compare(encode(), other.encode());
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
