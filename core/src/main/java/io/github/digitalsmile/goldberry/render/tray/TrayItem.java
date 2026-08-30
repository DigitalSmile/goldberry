package io.github.digitalsmile.goldberry.render.tray;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// One row of a tray menu.
///
/// **Not a widget, and the reason is not packaging.** Every other row in the
/// catalog is a description Goldberry lays out, styles and hit-tests; this one is
/// handed to the desktop's shell, which draws it in its own theme with its own
/// font at its own spacing. There is no `ComputedStyle` to give it and no pointer
/// event to route to it — so `tray-icon` joins `toast` as a widget whose value is
/// not a widget (ADR-0177).
///
/// The kinds are an enum rather than SDL's flags mask on purpose: exactly one of
/// command/checkbox/submenu is mandatory down there, and a mask is precisely the
/// shape in which that rule gets broken silently. An enum cannot be none of them.
///
/// @param kind     what the row is
/// @param label    its text; ignored for [Kind#SEPARATOR]
/// @param enabled  false draws it greyed and unselectable
/// @param checked  the initial state of a [Kind#CHECKBOX]; ignored otherwise
/// @param onChosen what runs when the user picks it, or null for a row that does
///                 nothing itself — which every submenu and separator is
/// @param children the submenu's rows; empty for everything but [Kind#SUBMENU]
public record TrayItem(
        Kind kind,
        @Nullable String label,
        boolean enabled,
        boolean checked,
        @Nullable Chosen onChosen,
        List<TrayItem> children) {

    /// What a row is.
    public enum Kind {

        /// A command.
        COMMAND,

        /// A checkable row. The platform toggles it **before** calling back, so
        /// [Chosen] is told the new state rather than being asked to work it out.
        CHECKBOX,

        /// A row that opens a submenu.
        SUBMENU,

        /// A dividing line.
        SEPARATOR
    }

    /// What runs when a row is chosen.
    @FunctionalInterface
    public interface Chosen {

        /// @param checked the row's state after the platform applied the click,
        ///        and always false for a row that is not a checkbox
        void chosen(boolean checked);
    }

    public TrayItem {
        Objects.requireNonNull(kind, "kind");
        children = List.copyOf(children == null ? List.of() : children);
        if (kind != Kind.SEPARATOR) {
            Objects.requireNonNull(label, "label");
        }
        if (kind == Kind.SUBMENU && children.isEmpty()) {
            throw new IllegalArgumentException("a submenu with no rows is a row that opens nothing: " + label);
        }
        if (kind != Kind.SUBMENU && !children.isEmpty()) {
            throw new IllegalArgumentException(
                    "only a submenu has children, and " + kind + " " + label + " has " + children.size());
        }
    }

    /// A command.
    public static TrayItem command(String label, Chosen onChosen) {
        return new TrayItem(Kind.COMMAND, label, true, false, onChosen, List.of());
    }

    /// A checkable row, with its initial state.
    public static TrayItem checkbox(String label, boolean checked, Chosen onChosen) {
        return new TrayItem(Kind.CHECKBOX, label, true, checked, onChosen, List.of());
    }

    /// A row that opens `children`.
    public static TrayItem submenu(String label, List<TrayItem> children) {
        return new TrayItem(Kind.SUBMENU, label, true, false, null, children);
    }

    /// A dividing line.
    public static TrayItem separator() {
        return new TrayItem(Kind.SEPARATOR, null, true, false, null, List.of());
    }

    /// The same row, greyed out.
    public TrayItem disabled() {
        return new TrayItem(kind, label, false, checked, onChosen, children);
    }

    /// This row, and every row under it, running `after` once its own handler
    /// has.
    ///
    /// **What makes a tray row reach the screen.** A row's handler is delivered
    /// from inside the platform's own pump, and it is the only input in the
    /// toolkit that produces no event: no pointer moved, no key arrived, nothing
    /// asked for a frame. A handler that sets a field on a model therefore
    /// changes nothing anybody looks at — the model sweep runs at the top of a
    /// frame, and there is no frame. `Host.tray` wraps every row with the window's
    /// repaint, which is what every other input path gets for free.
    ///
    /// Submenus and separators are left alone: no platform delivers a callback
    /// for either, so a handler on one would be a promise nothing keeps.
    ///
    /// @param after what to run after the row's own handler
    public TrayItem andThen(Runnable after) {
        Objects.requireNonNull(after, "after");
        var wrapped = kind == Kind.SUBMENU || kind == Kind.SEPARATOR
                ? onChosen
                : (Chosen) nowChecked -> {
                    choose(nowChecked);
                    after.run();
                };
        return new TrayItem(
                kind,
                label,
                enabled,
                checked,
                wrapped,
                children.stream().map(child -> child.andThen(after)).toList());
    }

    /// Runs this row's handler, if it has one.
    ///
    /// Here rather than at each call site because both backends need it and one
    /// of them is a test double: a row with no handler is chosen by doing
    /// nothing, which is a fact about the row.
    public void choose(boolean nowChecked) {
        if (onChosen != null) {
            onChosen.chosen(nowChecked);
        }
    }
}
