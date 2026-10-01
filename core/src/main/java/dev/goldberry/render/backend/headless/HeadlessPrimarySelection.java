package dev.goldberry.render.backend.headless;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.render.clipboard.PrimarySelection;

/// The primary selection a headless test gets — in memory, and able to refuse
/// ([ADR-0504]).
///
/// **Present by default**, which is the opposite of what a desktop without X11
/// or Wayland reports, and deliberately: the widgets that publish a selection and
/// paste on a middle click are only testable against one that exists. A test of
/// the other case — no primary selection, so no middle-click paste — turns it off
/// on the backend with [HeadlessBackend#primarySelection(boolean)], and this
/// buffer is still here to prove nothing was written to it.
///
/// Counts writes, because what a widget promises about this one is **when** it
/// writes: once per finished selection, not once per pointer move
/// ([PrimarySelection]'s note on cost).
///
/// Confined to the UI thread, like the rest of this backend.
public final class HeadlessPrimarySelection implements PrimarySelection {

    private final HeadlessBackend backend;

    private String text = "";

    private int writes;

    private boolean refusing;

    HeadlessPrimarySelection(HeadlessBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    // --- the test seams ------------------------------------------------------

    /// Makes every write refuse, as [HeadlessClipboard#refuseWrites] does for the
    /// clipboard: a test seam and not a policy.
    ///
    /// @param value whether writes refuse from now on
    /// @return this selection, for a fluent set-up
    public HeadlessPrimarySelection refuseWrites(boolean value) {
        backend.requireUiThread();
        this.refusing = value;
        return this;
    }

    /// How many writes this selection has accepted.
    public int writes() {
        backend.requireUiThread();
        return writes;
    }

    // --- PrimarySelection ----------------------------------------------------

    @Override
    public boolean hasText() {
        backend.requireUiThread();
        return !text.isEmpty();
    }

    @Override
    public String text() {
        backend.requireUiThread();
        return text;
    }

    @Override
    public boolean text(@Nullable String value) {
        backend.requireUiThread();
        if (refusing) {
            return false;
        }
        text = value == null ? "" : value;
        writes++;
        return true;
    }

    @Override
    public String toString() {
        return "PrimarySelection[headless, " + text.length() + " chars" + (refusing ? ", refusing writes]" : "]");
    }
}
