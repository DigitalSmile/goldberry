package io.github.digitalsmile.goldberry.input.handler;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.event.PreeditEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Widget;

/// A widget that reacts to the pointer.
///
/// Opt-in, like [io.github.digitalsmile.goldberry.widget.style.Paints]. A widget that
/// does not implement this is not asked, which keeps dispatch proportional to the
/// number of interested nodes rather than to the depth of the tree.
public interface Handles extends Widget {

    /// Called during the capture phase, root-first, before the target sees the
    /// event.
    ///
    /// Where a scroll view or a modal layer intercepts. Consuming here stops the
    /// target ever receiving it — which is the point of having a capture phase
    /// at all.
    default void onPointerCapture(PointerEvent event) {}

    /// Called on the target and then on each ancestor, deepest-first.
    ///
    /// The phase ordinary widgets want.
    default void onPointer(PointerEvent event) {}

    /// The **part** this widget's [PointerEvent#local()] is measured against,
    /// or null for the widget's own box.
    ///
    /// A control whose hit target is bigger than the thing being pointed *along*
    /// needs these to be two different rectangles. A slider showing its value is
    /// the first: `[ track ──────── ] 40` is one control, and the value is a
    /// position along the **track** — measuring it along the whole control would
    /// make the far end of the track read as 88% rather than 100%, silently, by
    /// exactly the width of the label
    /// (ADR-0080).
    ///
    /// Named as a **CSS type**, which is the vocabulary a part already has
    /// ([ADR-0065]): the first descendant whose
    /// [io.github.digitalsmile.goldberry.widget.style.Styled#cssType()] matches,
    /// in document order. The router resolves it, because it is the one that holds
    /// the painted rectangles and the widget cannot see its own elements — the
    /// argument already written for `dragX()` and for Tab.
    ///
    /// A part that is not there — not built, or not painted yet — falls back to
    /// this widget's own box rather than to nothing, so a control keeps working
    /// while its scale or its label is absent.
    default @Nullable String localPart() {
        return null;
    }

    /// The value this widget wants remembered for the duration of a drag, or
    /// `NaN` — which is the default and means "I have no gesture state".
    ///
    /// Asked **once, on the press**, and handed back on every event of that
    /// gesture as [PointerEvent#anchor()]. The router asks the pressed element's
    /// chain deepest-first and takes the first answer that is not `NaN`, which is
    /// the same order it dispatches in — so a press that lands on a *part* is
    /// still anchored by the control that will handle it.
    ///
    /// ## Why the router holds it and not the widget
    ///
    /// A control whose drag is a **rate** cannot compute anything from the
    /// current value alone. A knob maps 200 logical pixels of vertical travel
    /// onto its whole range (`docs/design-system.md` §3), so the value under the
    /// pointer is `where it started + how far you have dragged` — and by the
    /// second frame of the drag "where it started" is gone, because the value has
    /// already moved and a widget is an immutable value rebuilt from it.
    ///
    /// A slider needs none of this: its value is a *position*, read fresh from
    /// the pointer against the track on every event, with no history at all
    /// ([ADR-0079]). That difference is the whole of why this exists.
    ///
    /// It is the router's for the reason [PointerEvent#pressX()] is
    /// ([ADR-0075]): the implicit capture already spans exactly one gesture
    /// ([ADR-0058]), so the router is both the only thing that can know and the
    /// thing whose lifetime already matches. A `double` and not an `Object`
    /// because the router must not start holding application values it cannot
    /// reason about, and every gesture that has wanted one so far has wanted a
    /// number ([ADR-0089]).
    default double gestureAnchor() {
        return Double.NaN;
    }

    /// Called during the keyboard capture phase, root-first.
    ///
    /// Where a dialog swallows Escape before the thing inside it sees it.
    default void onKeyCapture(KeyEvent event) {}

    /// A key went down or came up, on the focused node and then its ancestors.
    default void onKey(KeyEvent event) {}

    /// Committed text, on the way **down** to the focused node ([ADR-0246]).
    ///
    /// The mirror of [#onKeyCapture], and it exists for one reason: a `select`
    /// whose list is open has an `option` focused inside a popup, so §3's
    /// typeahead had nothing to intercept the text in — the letters went to a row
    /// that does not know what typing means and stopped there.
    ///
    /// A capture phase is a routing rule, and one was deliberately not invented
    /// on spec: a phase added for no consumer is how a router ends up with two of
    /// them that disagree. This is the consumer.
    default void onTextCapture(TextEvent event) {}

    /// Committed text reached the focused node.
    ///
    /// A widget that wants what the user typed wants this, not [#onKey]: one
    /// character can take several keys (§7.1).
    default void onText(TextEvent event) {}

    /// The composition an input method is assembling, before the user has
    /// accepted it — `docs/gaps.md` G15.
    ///
    /// Offered to the focused node and its ancestors, like [#onText], and with
    /// **no capture phase**: nothing above the field can usefully act on
    /// characters that are about to be replaced.
    ///
    /// A widget that ignores this is exactly as correct as it was before — the
    /// committed text still arrives through [#onText], which is what a Latin
    /// keyboard produces and all an input method's *result* ever was. What it
    /// loses is the underlined string a CJK user watches while choosing, and
    /// without it they type blind until they commit (ADR-0289).
    ///
    /// **Do not insert it.** See [PreeditEvent]: it is drawn beside the document,
    /// not in it.
    default void onPreedit(PreeditEvent event) {}

    /// Where this widget's caret is, in its **own content coordinates** — the
    /// same frame [PointerEvent#content()] is measured in.
    ///
    /// The toolkit passes it to the platform so an input method can put its
    /// candidate window beside the text rather than wherever the compositor
    /// guesses (`docs/gaps.md` G15). Asked after every event that could have
    /// moved a caret, and cheap: a widget that returns an equal rectangle costs
    /// one comparison.
    ///
    /// Empty means "I have no caret", which is the honest answer for everything
    /// that is not being typed into and the default here.
    ///
    /// **The rectangle is the line, not the caret.** An input method uses it to
    /// keep its list clear of the text it would otherwise cover, so a one-pixel
    /// rectangle makes for a list drawn over the words; the caret's position
    /// within it is [#caretOffsetIn].
    default Optional<LogicalRect> caretArea() {
        return Optional.empty();
    }

    /// Where the caret is inside [#caretArea], as an x offset from its left edge.
    ///
    /// Separate from the rectangle because they are two different facts and a
    /// widget that has one usually has the other for free —
    /// [io.github.digitalsmile.goldberry.text.edit.Editor#caret] is the caret and
    /// the line it is on in one object.
    ///
    /// Zero by default, which is the left edge and is where a list goes when
    /// nothing better is known.
    default double caretOffsetIn(LogicalRect area) {
        return 0;
    }

    /// Whether the **platform's** text input should be on while this widget has
    /// the focus.
    ///
    /// Not the same question as [#onText]: every focused widget is offered
    /// committed text, and on a desktop the platform sends it whether or not
    /// anybody asked. What this turns on is `SDL_StartTextInput` — the input
    /// method, the dead keys, the on-screen keyboard on the platforms that have
    /// one — and leaving it on for a board that only wants arrow keys pops a
    /// keyboard over the board (ADR-0285).
    ///
    /// False by default, so a widget that is not typed into costs nothing. The
    /// router asks the focused widget on every focus change and tells the window,
    /// which is the same one-wire arrangement the cursor has (§7.3): the router
    /// knows nothing about the platform and the widget knows nothing about the
    /// window.
    default boolean wantsTextInput() {
        return false;
    }

    /// Whether this widget can take keyboard focus.
    ///
    /// False by default: most nodes are scenery, and a Tab traversal that
    /// stopped on every one of them would be unusable.
    default boolean isFocusable() {
        return false;
    }

    /// Whether this widget's descendants are **one** Tab stop with arrow-key
    /// focus inside — a radio group, a tab list, a menu, a toolbar — and if so,
    /// **which arrows** move between them.
    ///
    /// `docs/design-system.md` §7.2: "composites are one Tab stop with roving
    /// arrow-key focus inside". A group of six radios that took six Tab presses
    /// to cross is the thing this prevents, and it is a property of the *group*
    /// rather than of any radio in it — which is why it is asked here and
    /// answered by the router, exactly as Tab is
    /// (ADR-0073).
    ///
    /// The axis is the widget's because only it knows what it means by the other
    /// pair: a vertical menu's `Right` opens a submenu, and a scope that roved on
    /// it would move focus down the list whenever an item had none
    /// (ADR-0078).
    /// `radio-group` answers [FocusScope#BOTH], because its direction is its
    /// stylesheet's rather than its own.
    ///
    /// Where the traversal **enters** is not remembered: it is the focusable
    /// descendant matching `:checked`, or the first one if none does. So the
    /// selection is the roving position, and there is no second piece of state
    /// that can disagree with it.
    default FocusScope focusScope() {
        return FocusScope.NONE;
    }

    /// Focus arrived at this widget, or left it.
    ///
    /// What "selection follows focus" is spelled with: a radio raises its change
    /// when an arrow key brings focus to it, so the arrow does not move the tick
    /// directly — the application sets the value and the tick follows it back
    /// down ([ADR-0063]).
    ///
    /// @param focused      whether this widget now has focus
    /// @param fromKeyboard whether the move came from the keyboard, which is the
    ///                     same distinction `:focus-visible` draws — a control
    ///                     that acted on a *mouse* focus would fire twice for one
    ///                     click, once here and once on the click itself
    default void onFocusChanged(boolean focused, boolean fromKeyboard) {}

    /// Focus entered this widget's **subtree**, or left it — CSS's
    /// `:focus-within`, as a notification.
    ///
    /// [#onFocusChanged] is about *this* node and is what a control wants. This
    /// is about everything under it, and is what a container wants: a `field`
    /// validating when the user has finished with the control inside it, and a
    /// `carousel` refusing to rotate while somebody is reading a slide they have
    /// tabbed into.
    ///
    /// ## Only on the boundary
    ///
    /// Focus moving from one descendant to another **does not** call this. What
    /// it reports is the subtree as a whole gaining or losing the keyboard, so a
    /// `field` holding one control and a `field` holding three behave the same,
    /// and a container does not have to filter out the moves that stayed inside
    /// it.
    ///
    /// A widget that is itself focused is inside its own subtree, so a control
    /// implementing both is told twice — once about itself and once about the
    /// subtree containing it. That is not a mistake to work around: they are
    /// different questions, and `:focus` and `:focus-within` are both true of a
    /// focused node in CSS for the same reason.
    ///
    /// @param within      whether focus is now somewhere in this subtree
    /// @param fromKeyboard whether the move that caused it came from the
    ///                    keyboard, exactly as [#onFocusChanged]'s does
    default void onFocusWithin(boolean within, boolean fromKeyboard) {}

    /// Whether a press that lands on this widget's scenery should focus the first
    /// focusable thing **inside** it — HTML's `<label for>`, without the `for`.
    ///
    /// A press focuses the nearest focusable *ancestor* of whatever it hit, which
    /// is what makes clicking a button's label press the button. A `field`'s label
    /// is not an ancestor of the control it names, it is its **sibling**, so that
    /// rule cannot reach it and clicking a label does nothing.
    ///
    /// A container that answers true says "the focusable thing here is one of my
    /// children", and the router hands the press down instead of up.
    ///
    /// ## Only when nothing else claimed it
    ///
    /// This is consulted on the way up, so it never overrides a real target: a
    /// press on the control itself finds the control before it reaches the
    /// container, and a press on a `button` inside a field focuses the button.
    /// What it catches is a press on the label, the message, or the gap — the
    /// places where "the user aimed at this field" is the only sensible reading.
    ///
    /// The **first** focusable descendant in document order. A field with two
    /// controls in it hands the click to the first, which is what a label at the
    /// front of a row names.
    default boolean delegatesFocus() {
        return false;
    }

    /// Whether the keyboard is **trapped inside** this widget — `docs/core-widgets.md`
    /// §7's "focus trap", which a `dialog` is the first thing to need.
    ///
    /// A modal widget answers two questions at once and they are the same
    /// question: Tab enumerates its subtree instead of the window's, and focus
    /// that is anywhere else is moved into it. There is no third rule and no
    /// bookkeeping to keep in step — the invariant is simply that while a modal
    /// is mounted, the focused node is inside it.
    ///
    /// **The pointer obeys it too**, and used not to. This said "the pointer is
    /// not this flag's business" and left it to geometry: a dialog is unreachable
    /// by mouse because its scrim covers the window, which is what a filling
    /// [io.github.digitalsmile.goldberry.Overlay] already does for `tour`'s veil.
    /// That is a rule a reviewer has to remember, and a widget that declared
    /// itself modal without one trapped the keyboard and let every click through.
    ///
    /// So it is one flag: while a modal is mounted, the pointer reaches its
    /// **subtree** and its **ancestors**, and nothing else. The ancestors are the
    /// point rather than a loophole — a dialog's scrim is the panel's *parent*,
    /// and a click on it is what closes the dialog ([ADR-0232]).
    ///
    /// The **deepest** modal wins, so a dialog opened from a dialog traps inside
    /// the second one and gives the first back when it closes.
    default boolean isModal() {
        return false;
    }
}
