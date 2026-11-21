package dev.goldberry.natives.desktop.dragout;

import org.jspecify.annotations.Nullable;

/// What the XDND protocol needs of the X server, so that [XdndSource] can be
/// driven by a test as well as by libX11.
interface XdndTransport {

    /// The pointer, in root coordinates, with whether the primary button is
    /// still held and the modifiers that steer the action.
    record Pointer(int rootX, int rootY, boolean primaryHeld, boolean shift, boolean control) {}

    /// The window under the pointer that a drag speaks to.
    ///
    /// @param window  the XDND-aware window (or its proxy), or one of the
    ///                application's own
    /// @param version the XDND version it announces; 0 for an own window
    /// @param own     whether it is one of the application's own windows
    /// @param x       the pointer in it, physical pixels, for an own window
    /// @param y       the pointer in it, physical pixels, for an own window
    record Target(long window, int version, boolean own, int x, int y) {}

    /// The window the drag is sent from and owns the selection with.
    long sourceWindow();

    /// The atom called `name`, made if it is not yet.
    long atom(String name);

    /// The name of `atom`, or null for none.
    @Nullable
    String atomName(long atom);

    /// Where the pointer is now.
    Pointer pointer();

    /// The target under the root point, or null over nothing that takes drags.
    @Nullable
    Target targetAt(int rootX, int rootY);

    /// Sends a 32-bit `ClientMessage` of `type` to `window`.
    void send(long window, long type, long[] data);

    /// Writes `XdndTypeList` on the source window.
    void typeList(long[] atoms);

    /// Makes the source window own `selection`.
    void ownSelection(long selection);

    /// Writes `data` on `window`'s `property` as `type`, in `format`-bit units.
    void writeProperty(long window, long property, long type, int format, byte[] data);

    /// Tells `requestor` the conversion is done, with `property` 0 for refused.
    void notifySelection(long requestor, long selection, long target, long property, long time);

    /// The largest property this server takes in one request, in bytes.
    long maxPropertyBytes();

    /// The keycode of Escape.
    int escapeKeycode();

    /// Sends what is queued.
    void flush();
}
