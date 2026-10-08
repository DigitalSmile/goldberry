package dev.goldberry.widget.semantics;

/// What a widget *is*, to something that cannot see it: the closed set of roles a
/// widget's [Semantics#role()] answers with.
///
/// ```java
/// @Override public Role role() { return Role.STATUS; }
/// ```
///
/// The vocabulary is deliberately small and deliberately not ARIA's: these are
/// the roles this toolkit's own catalogue has, and a role nothing implements is a
/// promise to an assistive technology that nothing keeps. It grows when a widget
/// arrives that is genuinely none of these.
///
/// Nothing reads the tree yet: no platform API is touched and nothing is exported
/// to a screen reader. The data is here so that a sweep over the catalogue can
/// fail on a focusable widget with no role and no name, which is the defect an
/// accessibility pass otherwise finds late, and so that a bridge to the platform,
/// when one comes, is an adapter over a role, a name and a set of states.
///
/// Read more:
/// [Semantics: a role and a name](https://goldberry.dev/docs/guide/writing-a-widget.html#semantics-a-role-and-a-name).
public enum Role {

    /// Something you press to make it happen.
    BUTTON,

    /// A two-state box, possibly with a third mixed state.
    CHECKBOX,

    /// One of a set where exactly one is chosen.
    RADIO,

    /// The set itself: a `radio-group` or a `segmented`, which are one Tab stop
    /// with arrow keys inside. The group is what the keyboard reaches, so the
    /// group is what has to have a role.
    RADIO_GROUP,

    /// A two-state switch, which differs from a checkbox in that it takes effect
    /// immediately rather than on submit.
    SWITCH,

    /// A single-line or multi-line editable text field.
    TEXT_FIELD,

    /// A control that picks one value from a range: a slider, a knob.
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

    /// A region with a boundary and no better word: a carousel's viewport.
    GROUP,

    /// A two-dimensional set of cells addressed by row and column: a
    /// `calendar`'s month.
    ///
    /// Distinct from [#GROUP], and the distinction is the arrows: a group is a
    /// boundary with content in it and says nothing about how the content is
    /// reached, where a grid promises that all four arrow keys mean something and
    /// that a cell has a position in two axes. [Semantics] has no per-cell
    /// channel, so a cell's own name is not carried yet.
    GRID,

    /// A window-like layer over the rest: a dialog, a tour stop.
    DIALOG,

    /// A region that reports **what just happened** rather than what is true: a
    /// `toast`.
    ///
    /// Distinct from [#GROUP] and from [#DIALOG], and neither of those would do:
    /// a group is a boundary with content in it, a dialog is something the user
    /// is in until they leave it, and a notification is neither. It is a sentence
    /// that appears, is read, and goes. Paired with [Live#POLITE], which is the
    /// half that says an appearance is worth speaking.
    STATUS,

    /// Words that are read and not operated: a `rich-text` paragraph, named by
    /// what it says.
    ///
    /// Distinct from [#GROUP], which is a boundary with content in it: a styled
    /// paragraph is the content, and its name is the sentence, whatever its runs
    /// look like.
    TEXT,
}
