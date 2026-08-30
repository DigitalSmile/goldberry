package io.github.digitalsmile.goldberry.widgets.overlay.dialog;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

import java.util.List;

/// A modal — `docs/core-widgets.md` §7's `dialog`.
///
/// ```kdl
/// dialog id="unsaved" title="Unsaved changes" {
///     text "Your draft has not been saved."
///     action role="dismissive" press="app.stay" "Keep editing"
///     action role="affirmative" press="app.discard" "Discard"
/// }
/// ```
///
/// ```java
/// var open = Dialogs.show(host, new Dialog("Unsaved changes", …));
/// ```
///
/// ## A dialog is a widget, and showing one is not
///
/// [ADR-0106](../../../../../../../../book/src/adr/0106-a-menu-is-a-widget-and-opening-one-is-not.md)'s
/// title, one group later, and the argument is unchanged: a modal needs the
/// **window** — something has to cover it, take its pointer and hold its
/// keyboard — and a widget has no window. So this describes a dialog and
/// [Dialogs#show] puts one on a [io.github.digitalsmile.goldberry.Host].
///
/// It is not put in an application's own tree, either. A dialog written inline
/// would be laid out where it was written, and a modal is not somewhere in a
/// column: it is over everything.
///
/// ## What it is made of
///
/// ```
/// dialog-scrim          fills the window, dims it, and takes every press
/// └── dialog            the panel: sizes to content, min 320 and max 80%
///     ├── dialog-title  absent when there is no title
///     ├── dialog-body   what the author wrote
///     └── dialog-actions the buttons, in role order
/// ```
///
/// The **children are the content and the [DialogAction]s together**, as the
/// document wrote them, and this partitions them — `tabs` and `select` read
/// their children the same way. An action is a description rather than a button
/// so that the bar can decide the order, which is §7's "platform button order …
/// applied by the dialog's action bar automatically".
///
/// ## Two keys, and where they are handled
///
/// §7: "`Esc` = cancel-role button, `Enter` = default-role button". Both are
/// handled on the **bubble** phase rather than on capture, which is a decision
/// and not an accident: a control inside a dialog that means something by a key
/// keeps it by consuming it, so `Enter` in a `text-area` inserts a line and
/// `Esc` in an open `select` closes the list. A dialog that swallowed either on
/// capture would break the control it contains, and the control is the reason
/// the dialog is open.
///
/// A press on the scrim is the same event as `Esc`: both mean "the dismissive
/// one". A dialog with no dismissive action is not dismissible by either, which
/// is what a dialog that must be answered wants.
///
/// @param title      the heading, or null for a panel with no title bar
/// @param children   the content and the actions, as the document wrote them
/// @param attributes the `id` and classes, which land on the panel
@Markup("dialog")
public record Dialog(String title, List<Widget> children, Attributes attributes)
        implements Widget.Stateful, Attributed<Dialog> {

    public Dialog {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        // Blank and absent are the same: a title bar with one space in it is a
        // rule across the top of a panel and nothing above it.
        title = title == null || title.isBlank() ? null : title;
        var affirmative = 0;
        var dismissive = 0;
        for (var child : children) {
            if (child instanceof DialogAction action) {
                affirmative += action.role() == DialogAction.Role.AFFIRMATIVE ? 1 : 0;
                dismissive += action.role() == DialogAction.Role.DISMISSIVE ? 1 : 0;
            }
        }
        // Refused at construction, where every other document error is refused,
        // rather than on the frame a key is pressed. Two default buttons is a
        // dialog where `Enter` is a coin toss, and there is no answer this widget
        // could pick that would not be a guess about what the author meant.
        if (affirmative > 1 || dismissive > 1) {
            throw new IllegalArgumentException(
                    "a dialog has at most one affirmative and one dismissive action, and this"
                            + " one has " + affirmative + " and " + dismissive
                            + ". Enter and Escape each press exactly one button.");
        }
    }

    /// A dialog around some widgets.
    public Dialog(String title, Widget... children) {
        this(title, List.of(children), Attributes.NONE);
    }

    /// The buttons, in the order the document wrote them.
    public List<DialogAction> actions() {
        return children.stream().filter(DialogAction.class::isInstance)
                .map(DialogAction.class::cast).toList();
    }

    /// Everything that is not a button.
    public List<Widget> content() {
        return children.stream().filter(child -> !(child instanceof DialogAction)).toList();
    }

    /// The action a key presses, or null — see the class note.
    DialogAction actionFor(DialogAction.Role role) {
        return actions().stream().filter(action -> action.role() == role).findFirst().orElse(null);
    }

    /// Whether this dialog has a title bar.
    public boolean hasTitle() {
        return title != null;
    }

    @Override
    public Dialog withAttributes(Attributes value) {
        return new Dialog(title, children, value);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new DialogState();
    }

    /// Builds a `dialog` from markup.
    ///
    /// The title is a property rather than the node's argument, for
    /// `group-box`'s reason: the argument position is where a container's
    /// children start, and `dialog "Unsaved changes" { … }` would read as a
    /// dialog containing those words.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Dialog(node.stringProperty("title"), children, Attributes.of(node));
    }
}
