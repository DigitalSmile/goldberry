package io.github.digitalsmile.goldberry.input;

import io.github.digitalsmile.goldberry.widget.Element;
import java.util.Objects;

/// A key going down or coming up, as a widget sees it.
///
/// Not text. §7.1 keeps the two apart because one character can take several
/// keys — a compose sequence, a dead key, an IME conversion — so anything that
/// wants what the user *typed* wants [TextEvent], and anything that wants what
/// they *pressed* wants this.
public final class KeyEvent {

    public enum Kind {
        PRESSED, RELEASED
    }

    private final Kind kind;
    private final Key key;
    private final Modifiers modifiers;
    private final boolean repeat;
    private final Element target;
    private Extent bounds = Extent.NONE;
    private Extent part = Extent.NONE;
    private boolean consumed;

    public KeyEvent(Kind kind, Key key, Modifiers modifiers, boolean repeat, Element target) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.key = Objects.requireNonNull(key, "key");
        this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
        this.repeat = repeat;
        this.target = target;
    }

    public Kind kind() {
        return kind;
    }

    /// The key, or [Key#UNKNOWN] for one this toolkit does not name — which is
    /// most of them, because the ones that type a character arrive as text.
    public Key key() {
        return key;
    }

    public Modifiers modifiers() {
        return modifiers;
    }

    /// Whether the platform is repeating a held key.
    ///
    /// Worth checking: a handler that moves a cursor wants repeats, and one that
    /// opens a dialog does not.
    public boolean isRepeat() {
        return repeat;
    }

    /// The focused node this was aimed at, or null if nothing had focus.
    /// How big the widget about to handle this was when it was last painted.
    ///
    /// A key event carries no position, and for most controls that is the whole
    /// story — `Space` on a button needs no geometry. A scroll view is where it
    /// stops being true: `PageDown` moves by a viewport and stops at an edge, so
    /// it needs exactly the two rectangles the wheel does while pointing at
    /// nothing ([ADR-0116]).
    public Extent bounds() {
        return bounds;
    }

    /// The part named by [Handles#localPart()], or [#bounds()] for a widget that
    /// names none.
    public Extent part() {
        return part;
    }

    /// Re-pointed per handler by the router, exactly as a pointer event's is.
    public void measuredAs(Extent bounds, Extent part) {
        this.bounds = bounds == null ? Extent.NONE : bounds;
        this.part = part == null ? this.bounds : part;
    }

    public Element target() {
        return target;
    }

    public void consume() {
        consumed = true;
    }

    public boolean isConsumed() {
        return consumed;
    }

    /// Whether this is `key` with no modifier held — the usual test.
    public boolean is(Key candidate) {
        return key == candidate && modifiers.none();
    }

    @Override
    public String toString() {
        return kind + " " + key + (modifiers.none() ? "" : " (" + modifiers + ")")
                + (consumed ? " consumed" : "");
    }
}
