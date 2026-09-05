package io.github.digitalsmile.goldberry.widget.semantics;

/// What a widget *is*, to something that cannot see it.
///
/// The vocabulary is deliberately small and deliberately not ARIA's: these are
/// the roles this toolkit's own catalog has, and a role nothing implements is a
/// promise to an assistive technology that nothing keeps. It grows when a widget
/// arrives that is genuinely none of these.
///
/// ## Why this exists before the AccessKit bridge
///
/// Because `docs/testing.md` §1.7 asks for a sweep — "walking the gallery asserts
/// every interactive node exposes role + name" — and that assertion needs
/// somewhere for the answer to live. The bridge is M5 and this is not it: no
/// platform API is touched here, nothing is exported to a screen reader, and
/// `SemanticsSweepTest` is the only consumer. What it buys now is that the
/// catalog cannot grow a focusable widget that has no name, which is the defect
/// an accessibility pass finds late and expensively.
///
/// The shape is what a bridge would need anyway — AccessKit's node is a role, a
/// name and a set of states — so this is the data rather than a rehearsal of it.
public enum Role {

    /// Something you press to make it happen.
    BUTTON,

    /// A two-state box, possibly with a third mixed state.
    CHECKBOX,

    /// One of a set where exactly one is chosen.
    RADIO,

    /// The set itself — a `radio-group` or a `segmented`, which are one Tab stop
    /// with arrow keys inside (ADR-0073). The group is what the keyboard reaches,
    /// so the group is what has to have a role.
    RADIO_GROUP,

    /// A two-state switch, which differs from a checkbox in that it takes effect
    /// immediately rather than on submit.
    SWITCH,

    /// A single-line or multi-line editable text field.
    TEXT_FIELD,

    /// A control that picks one value from a range — a slider, a knob.
    SLIDER,

    /// A collapsed list of choices.
    COMBO_BOX,

    /// One choice inside a list, a menu or a tree.
    OPTION,

    /// A row in a list or a table.
    ROW,

    /// One tab in a strip.
    TAB,

    /// A command in a menu.
    MENU_ITEM,

    /// The heading of a menu on a bar.
    MENU_BUTTON,

    /// A region that scrolls.
    SCROLL_VIEW,

    /// The handle between two panes.
    SEPARATOR,

    /// A heading that opens and closes the section under it.
    DISCLOSURE,

    /// A picture of data, which a reader reaches with the keyboard.
    FIGURE,

    /// A region with a boundary and no better word — a carousel's viewport.
    GROUP,

    /// A window-like layer over the rest: a dialog, a tour stop.
    DIALOG,

    /// A region that reports **what just happened** rather than what is true —
    /// a `toast`.
    ///
    /// Distinct from [#GROUP] and from [#DIALOG], and neither of those would do:
    /// a group is a boundary with content in it, a dialog is something the user
    /// is in until they leave it, and a notification is neither. It is a sentence
    /// that appears, is read, and goes. Paired with [Live#POLITE], which is the
    /// half that says an appearance is worth speaking (ADR-0225).
    STATUS,
}
