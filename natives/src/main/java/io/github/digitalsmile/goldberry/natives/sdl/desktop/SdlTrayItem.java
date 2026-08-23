package io.github.digitalsmile.goldberry.natives.sdl.desktop;

import java.util.List;
import java.util.Objects;

/// One row of a tray menu, as a value.
///
/// A tray menu is **described** rather than built: the platform's shell draws it,
/// so there is nothing for a widget tree to lay out, style or hit-test. What
/// crosses the boundary is this — a label, a kind, two flags and a handler — and
/// [SdlTray] turns a list of them into `SDL_TrayEntry`s in one pass.
///
/// The kinds are exhaustive on purpose. SDL takes a flags mask in which exactly
/// one of button/checkbox/submenu is mandatory, and a mask is the shape in which
/// that rule is broken silently: an entry claiming to be neither comes back as a
/// null pointer with nothing said. An enum cannot be none of them.
///
/// @param kind     what the entry is
/// @param label    the text, ignored for [Kind#SEPARATOR]
/// @param enabled  false draws it greyed and unselectable
/// @param checked  the initial state of a [Kind#CHECKBOX], ignored otherwise
/// @param onChosen what runs when the user picks it; null for a row that does
///                 nothing itself, which every [Kind#SUBMENU] and
///                 [Kind#SEPARATOR] is
/// @param children the submenu's rows, empty for everything but [Kind#SUBMENU]
public record SdlTrayItem(
        Kind kind,
        String label,
        boolean enabled,
        boolean checked,
        Chosen onChosen,
        List<SdlTrayItem> children) {

    /// What a row is. See the note above on why this is not a flags mask.
    public enum Kind {

        /// A command.
        COMMAND,

        /// A checkable row. **SDL toggles it itself** before calling back, which
        /// is why [Chosen] is handed the new state rather than being expected to
        /// work it out.
        CHECKBOX,

        /// A row that opens a submenu.
        SUBMENU,

        /// A dividing line. SDL spells this as an entry with a null label.
        SEPARATOR
    }

    /// What runs when a row is chosen.
    ///
    /// Called from inside SDL's event pump, so on the thread that pumps — and
    /// anything it throws is caught and logged rather than being allowed to
    /// unwind into native code, which would take the process with it.
    @FunctionalInterface
    public interface Chosen {

        /// @param checked the row's state *after* SDL applied the click, and
        ///        always false for a row that is not a checkbox
        void chosen(boolean checked);
    }

    public SdlTrayItem {
        Objects.requireNonNull(kind, "kind");
        children = List.copyOf(children == null ? List.of() : children);
        if (kind != Kind.SEPARATOR) {
            Objects.requireNonNull(label, "label");
        }
        if (kind == Kind.SUBMENU && children.isEmpty()) {
            throw new IllegalArgumentException(
                    "a submenu with no rows is a row that opens nothing: " + label);
        }
        if (kind != Kind.SUBMENU && !children.isEmpty()) {
            throw new IllegalArgumentException(
                    "only a submenu has children, and " + kind + " " + label + " has "
                            + children.size());
        }
    }

    /// A command.
    public static SdlTrayItem command(String label, Chosen onChosen) {
        return new SdlTrayItem(Kind.COMMAND, label, true, false, onChosen, List.of());
    }

    /// A checkable row, with its initial state.
    public static SdlTrayItem checkbox(String label, boolean checked, Chosen onChosen) {
        return new SdlTrayItem(Kind.CHECKBOX, label, true, checked, onChosen, List.of());
    }

    /// A row that opens `children`.
    public static SdlTrayItem submenu(String label, List<SdlTrayItem> children) {
        return new SdlTrayItem(Kind.SUBMENU, label, true, false, null, children);
    }

    /// A dividing line.
    public static SdlTrayItem separator() {
        return new SdlTrayItem(Kind.SEPARATOR, null, true, false, null, List.of());
    }

    /// The same row, greyed out.
    public SdlTrayItem disabled() {
        return new SdlTrayItem(kind, label, false, checked, onChosen, children);
    }

    /// The flags SDL is given for this row.
    ///
    /// Empty for a separator: SDL takes a null label for one and ignores the
    /// mask, so naming a kind there would be inventing a fact.
    public java.util.EnumSet<SdlTrayEntryFlag> flags() {
        if (kind == Kind.SEPARATOR) {
            return java.util.EnumSet.noneOf(SdlTrayEntryFlag.class);
        }
        var flags = java.util.EnumSet.noneOf(SdlTrayEntryFlag.class);
        switch (kind) {
            case COMMAND -> flags.add(SdlTrayEntryFlag.BUTTON);
            case CHECKBOX -> {
                flags.add(SdlTrayEntryFlag.CHECKBOX);
                if (checked) {
                    flags.add(SdlTrayEntryFlag.CHECKED);
                }
            }
            case SUBMENU -> flags.add(SdlTrayEntryFlag.SUBMENU);
            case SEPARATOR -> throw new AssertionError("handled above");
        }
        if (!enabled) {
            flags.add(SdlTrayEntryFlag.DISABLED);
        }
        return flags;
    }
}
