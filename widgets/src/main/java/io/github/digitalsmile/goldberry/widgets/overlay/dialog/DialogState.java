package io.github.digitalsmile.goldberry.widgets.overlay.dialog;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Departure;
import io.github.digitalsmile.goldberry.widgets.core.Phase;

/// A [Dialog]'s opening, its closing, and the one thing it has to ask the window
/// for.
///
/// ## Closing runs before the application is told
///
/// `docs/design-system.md` §1.7: an overlay runs `opening → open → closing →
/// removed`, "the element stays mounted through `closing`, **input is disabled
/// the instant closing starts** (no ghost clicks), removal fires on animation
/// end". That is the whole of this class, and it is the same order
/// [io.github.digitalsmile.goldberry.widgets.overlay.message.MessageState] uses:
/// nothing outside a dialog is holding it, so a dialog that told the application
/// first would be asking to be removed before it had faded.
///
/// So every route out — a button, `Esc`, a press on the scrim — goes through
/// [#close], which starts the exit, stops taking input, and runs the
/// application's handler when the animation ends. The handler is what removes
/// the [io.github.digitalsmile.goldberry.Overlay] the dialog is sitting in.
///
/// ## And the one thing it asks for
///
/// A dialog that opens without the keyboard is a dialog a keyboard user has to
/// Tab into from wherever they were, through the window behind it. So this asks
/// the host to focus it on its first build, which
/// [io.github.digitalsmile.goldberry.Host#focus] resolves to the first focusable
/// thing inside — the panel itself takes no focus.
///
/// The **trap** is not asked for: [io.github.digitalsmile.goldberry.input.handler.Handles#isModal]
/// on the panel is a fact about the tree, and the router reads it
/// (ADR-0176).
final class DialogState extends State<Dialog> {

    /// §1.7's `overlay`, which §3 names for a dialog's entrance: 240ms.
    private static final double ENTER_MILLIS = 240;

    /// §3: "out: **base**, reverse" — 160ms. Shorter than the entrance, because
    /// a dialog that took as long to go as it took to arrive feels stuck.
    private static final double EXIT_MILLIS = 160;

    private final Phase opening = new Phase(Phase.Kind.ENTERING, ENTER_MILLIS);

    /// §1.7's `closing → removed`, and the two flags it needs — see [Departure],
    /// which is where this and `message`'s identical copy of it now live
    /// ([ADR-0234]).
    ///
    /// **Two flags, and they mean different things**, which is the bug the pair
    /// replaced: `hasBegun` means *input is off*, from the instant an answer is
    /// given (§1.7: "no ghost clicks"), and `isOver` means *there is nothing left
    /// to draw*. Using the first for both is why a closing dialog stopped asking
    /// for frames on the frame it started closing, and therefore never faded at
    /// all.
    private final Departure closing = new Departure(EXIT_MILLIS, this::setState);

    /// Captured in `build` for the handlers that run later.
    private @Nullable Host host;

    /// Whether the focus request has been made. Once per mount: asking again on
    /// every build would drag focus back out of whatever the user tabbed to.
    private boolean focusAsked;

    private boolean reducedMotion;

    /// The zero-delay timer that asks for focus — see [#askForFocus].
    ///
    /// Held only so it can be cancelled. A dialog dismissed in the same turn it
    /// opened would otherwise leave one pointing at a tree that is gone; nothing
    /// bad would happen, because the id resolves to nothing, but a timer nobody
    /// can account for is how a leak looks before it is one.
    private EventLoop.@Nullable Timer focusing;

    @Override
    protected void dispose() {
        closing.cancel();
        if (focusing != null) {
            focusing.cancel();
            focusing = null;
        }
        super.dispose();
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var dialog = widget();
        askForFocus();

        var buttons = new ArrayList<Widget>(dialog.actions().size());
        for (var action : ordered(dialog.actions())) {
            buttons.add(button(action));
        }
        var phase = closing.phaseOr(opening);
        var panel = new DialogPanel(
                dialog.title(),
                dialog.content(),
                buttons,
                this::escape,
                this::confirm,
                phase,
                closing.hasBegun(),
                closing.isOver(),
                this::motion,
                dialog.attributes());
        return new DialogScrim(panel, this::escape, phase, closing.hasBegun(), closing.isOver());
    }

    /// §7's canonical order: neutral, then dismissive, then **affirmative last**.
    ///
    /// Affirmative-right is macOS' and Linux' order and is what ships; Windows
    /// puts it first, and a theme flips the whole bar with one declaration —
    /// see [DialogPanel.DialogActions]. A stable sort, so two neutrals stay in
    /// the order the document wrote them.
    private static List<DialogAction> ordered(List<DialogAction> actions) {
        return actions.stream()
                .sorted(java.util.Comparator.comparingInt(action -> switch (action.role()) {
                    case NEUTRAL -> 0;
                    case DISMISSIVE -> 1;
                    case AFFIRMATIVE -> 2;
                }))
                .toList();
    }

    /// One action as a real [Button], with the class its role implies and its
    /// handler wrapped so the panel closes first.
    ///
    /// The class is written **here** rather than by the author, which is the
    /// point of the role being a value: an affirmative button looks like the
    /// affirmative button in every dialog in the application, and nothing has to
    /// remember to say `class="primary"`.
    private Widget button(DialogAction action) {
        var classes = new java.util.LinkedHashSet<>(action.attributes().classes());
        switch (action.role()) {
            case AFFIRMATIVE -> classes.add("primary");
            case NEUTRAL -> classes.add("ghost");
            case DISMISSIVE -> {}
        }
        return new Button(action.label(), () -> close(action.onPress()))
                .withAttributes(action.attributes().classes(classes.toArray(String[]::new)));
    }

    /// `Esc`, and a press on the scrim, which mean the same thing: the
    /// dismissive button. A dialog without one is not dismissible by either,
    /// which is what a question that must be answered wants.
    private void escape() {
        var action = widget().actionFor(DialogAction.Role.DISMISSIVE);
        if (action != null) {
            close(action.onPress());
        }
    }

    /// `Enter`, which presses the affirmative button if there is one.
    private void confirm() {
        var action = widget().actionFor(DialogAction.Role.AFFIRMATIVE);
        if (action != null) {
            close(action.onPress());
        }
    }

    /// Starts the exit and runs `then` when it is over.
    ///
    /// Idempotent: a second press during the closing animation is not a second
    /// answer, which matters more here than anywhere else in the catalog — two
    /// handlers on a save dialog is two saves.
    private void close(Runnable then) {
        // Every rule this used to spell out is [Departure]'s now: idempotent, two
        // flags, stop drawing before telling the application, and gone at once
        // when there is no window or the reader asked for no motion ([ADR-0234]).
        closing.begin(host, reducedMotion, then);
    }

    /// Asks the window to put the keyboard in here, once.
    ///
    /// **Through a zero delay**, because this runs *during* the build that
    /// describes the dialog: the elements it is asking about do not exist yet.
    /// The next turn of the event loop is the first moment they do.
    private void askForFocus() {
        if (focusAsked || host == null) {
            return;
        }
        focusAsked = true;
        var id = widget().attributes().id();
        if (id == null) {
            // Nothing to name. A dialog with no id opens without the keyboard,
            // which is a document's omission rather than a fault to throw over --
            // and `Dialogs.show` gives one to every dialog that arrives without.
            return;
        }
        focusing = host.after(Duration.ZERO, () -> host.focus(id, false));
    }

    private void motion(boolean reduced) {
        reducedMotion = reduced;
    }
}
