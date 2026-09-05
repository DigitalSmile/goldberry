package io.github.digitalsmile.goldberry.input.tap;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/// A **tap** of a modifier key: pressed, and released with nothing in between.
///
/// `docs/core-widgets.md` §8 asks a `menubar` for "`Alt`-style keyboard
/// activation", and a bare `Alt` is not an accelerator. An accelerator is a key
/// plus modifiers, and it fires on the *press* of the key — there is no key here,
/// only the modifier, so there is nothing to press and nothing to look up. What a
/// desktop actually recognises is a gesture over two events and a rule about what
/// may happen between them, which is what this holds (ADR-0223).
///
/// ```java
/// taps.bind(ModifierKey.ALT, this::activateFromKeyboard, this);
/// ```
///
/// ## The rule
///
/// A tap fires when **the modifier that went down is the next key to come up**
/// and nothing else happened in between. Disarmed by:
///
/// - another key going down — `Alt+F` is a shortcut, not a tap followed by an `F`;
/// - the same key auto-repeating — `Alt` held open is somebody reaching for a
///   second key, and firing under their fingers is the one failure a user cannot
///   undo;
/// - a second modifier going down — `Alt+Shift` switches keyboard layouts on
///   three platforms and is nobody's menu bar;
/// - a pointer button or a wheel — [#interrupted()], which is what the window
///   calls when the gesture stopped being a keyboard one;
/// - the window losing focus, also [#interrupted()] — the `Alt` that reached
///   another application through the compositor's window switcher must not open a
///   menu here when focus comes back.
///
/// Pointer *motion* is deliberately not in that list. Moving the mouse while
/// tapping a key is not an interruption of anything.
///
/// ## Why this reads raw keycodes
///
/// [io.github.digitalsmile.goldberry.input.key.Key] names no modifier on purpose,
/// so every one of them arrives at
/// [io.github.digitalsmile.goldberry.input.key.Key#UNKNOWN] — as does every letter
/// that becomes text. A detector fed translated keys could not tell `Alt` from
/// `é`. The platform keycode is the only place the distinction survives, which is
/// why this sits at the window, above the backend and below the router.
///
/// ## Ownership
///
/// The same shape [io.github.digitalsmile.goldberry.input.PointerRouter]'s
/// accelerators use, and for the same reason: a widget that binds while it is
/// mounted has to give the binding back, and must not take a later one with it
/// (ADR-0220). Owners are compared by identity and never called.
///
/// One per window, confined to the UI thread like everything else input touches.
public final class ModifierTaps {

    /// One per window. Its state is that window's keyboard gesture in progress.
    public ModifierTaps() {}

    /// An action and the token that may take it back. A record because the pair
    /// is the value, and the owner is never anything but compared.
    private record Binding(Runnable action, @Nullable Object owner) {}

    private final Map<ModifierKey, Binding> bindings = new EnumMap<>(ModifierKey.class);

    /// The modifier that went down with nothing after it yet, or null for "no tap
    /// is in progress" — which is the state almost all of the time.
    private @Nullable ModifierKey armed;

    /// Binds `action` to a tap of `modifier`, remembering who bound it.
    public void bind(ModifierKey modifier, Runnable action, @Nullable Object owner) {
        bindings.put(
                Objects.requireNonNull(modifier, "modifier"),
                new Binding(Objects.requireNonNull(action, "action"), owner));
    }

    /// Binds `action` to a tap of `modifier`, with nobody owning it.
    public void bind(ModifierKey modifier, Runnable action) {
        bind(modifier, action, null);
    }

    /// Unbinds a tap, **whoever** bound it. Harmless when nothing was.
    public void unbind(ModifierKey modifier) {
        bindings.remove(Objects.requireNonNull(modifier, "modifier"));
        if (armed == modifier) {
            armed = null;
        }
    }

    /// Unbinds a tap **only if `owner` still holds it**.
    ///
    /// @return whether anything was removed
    public boolean unbind(ModifierKey modifier, @Nullable Object owner) {
        var bound = bindings.get(Objects.requireNonNull(modifier, "modifier"));
        if (bound == null || bound.owner() != owner) {
            return false;
        }
        unbind(modifier);
        return true;
    }

    /// The modifiers currently bound — for a test, and for a diagnostic.
    public Set<ModifierKey> bound() {
        return Set.copyOf(bindings.keySet());
    }

    /// Whether a tap is in progress: a modifier is down and nothing has spoiled
    /// it yet.
    public boolean isArmed() {
        return armed != null;
    }

    /// A key went down anywhere in the window, by platform keycode.
    ///
    /// Called for **every** key and before anything else looks at it, because the
    /// rule this implements is about what did *not* happen: a detector only told
    /// about the modifiers cannot tell a tap from the start of a shortcut.
    public void keyPressed(int sdlKeycode, boolean repeat) {
        if (bindings.isEmpty()) {
            return;
        }
        if (repeat) {
            // Held, not tapped. The key still comes up afterwards, so this is the
            // only place the difference can be seen.
            armed = null;
            return;
        }
        var modifier = ModifierKey.ofSdl(sdlKeycode).orElse(null);
        // An ordinary key, or a second modifier on top of the first: either way
        // what is being typed is a combination, and there is no tap to arm.
        armed = modifier != null && armed == null && bindings.containsKey(modifier) ? modifier : null;
    }

    /// A key came up anywhere in the window, by platform keycode.
    ///
    /// @return whether this completed a tap and ran its action
    public boolean keyReleased(int sdlKeycode) {
        var pending = armed;
        armed = null;
        if (pending == null || !pending.matches(sdlKeycode)) {
            return false;
        }
        var bound = bindings.get(pending);
        if (bound == null) {
            return false;
        }
        bound.action().run();
        return true;
    }

    /// Something that is not a key happened: a pointer press, a wheel, a focus
    /// change.
    ///
    /// Separate from [#keyPressed] because the window has more ways to interrupt a
    /// gesture than it has keys, and a detector that had to be told which one
    /// would be a detector that has to be updated when a new one appears.
    public void interrupted() {
        armed = null;
    }
}
