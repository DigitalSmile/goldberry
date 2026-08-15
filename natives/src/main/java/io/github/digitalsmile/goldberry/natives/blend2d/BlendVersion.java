package io.github.digitalsmile.goldberry.natives.blend2d;

/// Which Blend2D is linked into `libgoldberry`.
///
/// A build fact rather than a runtime one: Blend2D is statically linked, so
/// there is no system copy to disagree with. That makes this less useful than
/// SDL's version for diagnosing a mismatch and more useful for the opposite
/// question — Blend2D publishes no release tags and is pinned by commit SHA
/// (ADR-0030), so a bug report saying "0.11.0, built by GCC 13.2" is how the
/// version in a jar gets tied back to a commit.
///
/// @param major    major version
/// @param minor    minor version
/// @param patch    patch version
/// @param compiler the compiler Blend2D reports it was built with
public record BlendVersion(int major, int minor, int patch, String compiler) {

    @Override
    public String toString() {
        return major + "." + minor + "." + patch
                + (compiler.isEmpty() ? "" : " (" + compiler + ")");
    }
}
