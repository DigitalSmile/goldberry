/// Goldberry's vector module: SVG documents, drawn by ThorVG.
///
/// An optional content module: an application opts in, and nothing in `:core`
/// or `:widgets` knows this module exists. A document is parsed and drawn by
/// ThorVG's SVG loader and CPU rasterizer, linked into one `libgoldberry-vector`
/// per target and called through the Foreign Function and Memory API. There is
/// no JNI.
///
/// **This module binds native code itself**, as `:media` does, and for the same
/// reason: an application that draws no SVG carries none of it. `…vector.thorvg`
/// is not exported, and no ThorVG type appears in anything that is.
///
/// Consumers pass `--enable-native-access=dev.goldberry.vector` (JEP 472).
///
/// Read more: [Images](https://goldberry.dev/docs/components/drawing.html#image).
module dev.goldberry.vector {

    /// Logging. Not `transitive`: no type of it appears in a signature here.
    requires dev.goldberry.common;

    /// Pixels: a document is drawn into an image. `transitive`, because an image
    /// is what this module hands back.
    requires transitive dev.goldberry.core;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    requires static org.jspecify;

    /// What this module can do on this machine: whether its library loaded, and
    /// which ThorVG it holds.
    exports dev.goldberry.vector;
}
