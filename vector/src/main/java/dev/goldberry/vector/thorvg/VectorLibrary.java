package dev.goldberry.vector.thorvg;

import java.nio.file.Path;
import java.util.Objects;

import dev.goldberry.vector.thorvg.calls.CanvasCalls;
import dev.goldberry.vector.thorvg.calls.EngineCalls;
import dev.goldberry.vector.thorvg.calls.PictureCalls;
import dev.goldberry.vector.thorvg.calls.VectorCalls;

/// `libgoldberry-vector`, loaded and checked, with every function this module
/// calls bound.
///
/// @param vector    the library's own two functions
/// @param engine    ThorVG's engine
/// @param canvas    ThorVG's CPU canvas
/// @param picture   ThorVG's paints and pictures
/// @param build     what the superbuild wrote beside the library
/// @param versions  the line the library answers: `ThorVG 1.1.4; cpu; svg png jpg; simd off`
/// @param directory where it was loaded from
public record VectorLibrary(
        VectorCalls vector,
        EngineCalls engine,
        CanvasCalls canvas,
        PictureCalls picture,
        BuildProperties build,
        String versions,
        Path directory) {

    /// The ABI these bindings are written against: `goldberry_vector_abi()` and
    /// `vector.properties` both have to say it.
    public static final int ABI = 1;

    public VectorLibrary {
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(picture, "picture");
        Objects.requireNonNull(build, "build");
        Objects.requireNonNull(versions, "versions");
        Objects.requireNonNull(directory, "directory");
    }
}
