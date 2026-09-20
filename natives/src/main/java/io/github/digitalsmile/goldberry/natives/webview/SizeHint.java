package io.github.digitalsmile.goldberry.natives.webview;

/// What a size given to [Webview#size] means.
///
/// `webview/webview`'s four constants, named rather than numbered. The values are
/// the upstream header's and are checked against it by the shim rather than
/// trusted here.
public enum SizeHint {

    /// The size to open at, which the user may then change. The ordinary case.
    NONE(0),

    /// A floor. The window may be made larger and not smaller.
    MIN(1),

    /// A ceiling. The window may be made smaller and not larger.
    MAX(2),

    /// Both at once: the window is this size and cannot be resized.
    FIXED(3);

    private final int value;

    SizeHint(int value) {
        this.value = value;
    }

    /// The number `webview_set_size` takes.
    public int value() {
        return value;
    }
}
