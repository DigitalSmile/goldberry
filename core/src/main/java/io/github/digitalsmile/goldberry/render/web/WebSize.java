package io.github.digitalsmile.goldberry.render.web;

/// What a size given to a [BackendWebView] means.
///
/// The toolkit's own word for `:natives`' `SizeHint`, translated at the boundary
/// by an exhaustive switch — the arrangement
/// [io.github.digitalsmile.goldberry.platform.Capability] has with
/// `NativeCapability`, and for its reason: the two are deliberately separate
/// types, and the compiler is what notices when a constant is added to one and
/// not the other.
public enum WebSize {

    /// The size to open at, which the user may then change. The ordinary case.
    INITIAL,

    /// A floor. The window may be made larger and not smaller.
    MINIMUM,

    /// A ceiling. The window may be made smaller and not larger.
    MAXIMUM,

    /// Both at once: the window is this size and cannot be resized.
    FIXED
}
