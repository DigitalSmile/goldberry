package dev.goldberry.widgets.overlay.dialog;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// A modal: a panel over a dimmed window, with a title, some content and a row
/// of actions, that has to be answered before the window can be used again.
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
/// The same split a `menu` makes, for the same reason: a modal needs the
/// **window** — something has to cover it, take its pointer and hold its
/// keyboard — and a widget has no window. So this describes a dialog and
/// [Dialogs#show] puts one on a [dev.goldberry.Host].
///
/// It is not put in an application's own tree, either. A dialog written inline
/// would be laid out where it was written, and a modal is not somewhere in a
/// column: it is over everything.
///
/// ## What it is made of
///
/// ```
/// dialog-scrim            fills the window, dims it, and takes every press
/// └── dialog              the panel: sizes to content, min 320, max 80% wide
///     │                   and no taller than the window
///     ├── dialog-title    absent when there is no title
///     ├── dialog-body     what the author wrote, scrolling when it is taller
///     │                   than the panel has room for
///     └── dialog-actions  the buttons, in role order
/// ```
///
/// A dialog with a title-bar × ([#dismissible]) puts the title and the × in a
/// `dialog-header` row instead, so the heading's own rule is unchanged.
///
/// ## The height is capped and the body scrolls
///
/// A dialog is never taller than the window less the scrim's margins. When
/// its content would make it taller, the title and the action bar keep their
/// size and the **body** scrolls, which is the part that grew. The body's
/// content sits in a `scroll` with the class `dialog-scroll`.
///
/// The **children are the content and the [DialogAction]s together**, as the
/// document wrote them, and this partitions them — `tabs` and `select` read
/// their children the same way. An action is a description rather than a button
/// so that the bar can decide the order: the platform's button order is applied
/// by the action bar, not by each document.
///
/// ## Two keys, and where they are handled
///
/// `Esc` presses the dismissive button and `Enter` the affirmative one. Both are
/// handled on the **bubble** phase rather than on capture, which is a decision
/// and not an accident: a control inside a dialog that means something by a key
/// keeps it by consuming it, so `Enter` in a `text-area` inserts a line and
/// `Esc` in an open `select` closes the list. A dialog that swallowed either on
/// capture would break the control it contains, and the control is the reason
/// the dialog is open.
///
/// A press on the scrim is the same event as `Esc`: both mean "the dismissive
/// one". A dialog with no dismissive action and no × is not dismissible by
/// either, which is what a dialog that must be answered wants.
///
/// ## A way out in the title bar, when asked for
///
/// `dismiss=` puts a × at the end of the title bar, which is the way out for a
/// dialog whose content already has a button bar of its own — a `wizard`,
/// say, where a dismissive action would be a second Cancel under the first.
/// The × closes the dialog and runs `dismiss`, and in a dialog with a × `Esc`
/// and a press on the scrim do the same: the three are one way out, and the
/// × is the one that can be seen. A dismissive action in the same dialog is
/// then a button like any other. It is opt-in: a dialog that must be answered
/// stays one by default.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html#dialog).
///
/// @param title      the heading, or null for a panel with no title bar
/// @param children   the content and the actions, as the document wrote them
/// @param onDismiss  what the title bar's × does after the dialog closes, or
///                   null for a dialog with no ×
/// @param attributes the `id` and classes, which land on the panel
@Markup("dialog")
public record Dialog(
        @Nullable String title,
        List<Widget> children,
        @Nullable Runnable onDismiss,
        Attributes attributes) implements Widget.Stateful, Attributed<Dialog> {

    /// A dialog with no ×, which is every dialog that does not ask for one.
    public Dialog(@Nullable String title, @Nullable List<Widget> children, @Nullable Attributes attributes) {
        this(title, children, null, attributes);
    }

    /// Written out so that the parameters taking null for a default can say so.
    public Dialog(
            @Nullable String title,
            @Nullable List<Widget> children,
            @Nullable Runnable onDismiss,
            @Nullable Attributes attributes) {
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
        this.title = title;
        this.children = children;
        this.onDismiss = onDismiss;
        this.attributes = attributes;
    }

    /// A dialog around some widgets.
    public Dialog(String title, Widget... children) {
        this(title, List.of(children), Attributes.NONE);
    }

    /// The buttons, in the order the document wrote them.
    public List<DialogAction> actions() {
        return children.stream()
                .filter(DialogAction.class::isInstance)
                .map(DialogAction.class::cast)
                .toList();
    }

    /// Everything that is not a button.
    public List<Widget> content() {
        return children.stream()
                .filter(child -> !(child instanceof DialogAction))
                .toList();
    }

    /// The action a key presses, or null — see the class note.
    @Nullable
    DialogAction actionFor(DialogAction.Role role) {
        return actions().stream()
                .filter(action -> action.role() == role)
                .findFirst()
                .orElse(null);
    }

    /// Whether this dialog has a title bar.
    public boolean hasTitle() {
        return title != null;
    }

    /// This dialog with a × in its title bar that closes it and then runs
    /// `then` — see the class note.
    public Dialog dismissible(Runnable then) {
        return new Dialog(title, children, Objects.requireNonNull(then, "then"), attributes);
    }

    /// Whether this dialog has a × in its title bar.
    public boolean isDismissible() {
        return onDismiss != null;
    }

    @Override
    public Dialog withAttributes(Attributes value) {
        return new Dialog(title, children, onDismiss, value);
    }

    @Override
    public @Nullable Object key() {
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
    ///
    /// `dismiss=` names an action, as `message`'s does, and asks for the ×.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Dialog(node.stringProperty("title"), children, wiring.action(node, "dismiss"), Attributes.of(node));
    }
}
