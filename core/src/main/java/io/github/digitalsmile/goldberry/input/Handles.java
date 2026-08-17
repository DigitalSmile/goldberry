package io.github.digitalsmile.goldberry.input;

import io.github.digitalsmile.goldberry.widget.Widget;

/// A widget that reacts to the pointer.
///
/// Opt-in, like [io.github.digitalsmile.goldberry.widget.Paints]. A widget that
/// does not implement this is not asked, which keeps dispatch proportional to the
/// number of interested nodes rather than to the depth of the tree.
public interface Handles extends Widget {

    /// Called during the capture phase, root-first, before the target sees the
    /// event.
    ///
    /// Where a scroll view or a modal layer intercepts. Consuming here stops the
    /// target ever receiving it — which is the point of having a capture phase
    /// at all.
    default void onPointerCapture(PointerEvent event) {
    }

    /// Called on the target and then on each ancestor, deepest-first.
    ///
    /// The phase ordinary widgets want.
    default void onPointer(PointerEvent event) {
    }

    /// The **part** this widget's [PointerEvent#local()] is measured against,
    /// or null for the widget's own box.
    ///
    /// A control whose hit target is bigger than the thing being pointed *along*
    /// needs these to be two different rectangles. A slider showing its value is
    /// the first: `[ track ──────── ] 40` is one control, and the value is a
    /// position along the **track** — measuring it along the whole control would
    /// make the far end of the track read as 88% rather than 100%, silently, by
    /// exactly the width of the label
    /// ([ADR-0080](../../../../../../book/src/adr/0080-a-value-is-measured-along-a-part.md)).
    ///
    /// Named as a **CSS type**, which is the vocabulary a part already has
    /// ([ADR-0065]): the first descendant whose [Styled#cssType()] matches, in
    /// document order. The router resolves it, because it is the one that holds
    /// the painted rectangles and the widget cannot see its own elements — the
    /// argument already written for `dragX()` and for Tab.
    ///
    /// A part that is not there — not built, or not painted yet — falls back to
    /// this widget's own box rather than to nothing, so a control keeps working
    /// while its scale or its label is absent.
    default String localPart() {
        return null;
    }

    /// Called during the keyboard capture phase, root-first.
    ///
    /// Where a dialog swallows Escape before the thing inside it sees it.
    default void onKeyCapture(KeyEvent event) {
    }

    /// A key went down or came up, on the focused node and then its ancestors.
    default void onKey(KeyEvent event) {
    }

    /// Committed text reached the focused node.
    ///
    /// A widget that wants what the user typed wants this, not [#onKey]: one
    /// character can take several keys (§7.1).
    default void onText(TextEvent event) {
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
    /// ([ADR-0073](../../../../../../book/src/adr/0073-a-composite-is-one-tab-stop.md)).
    ///
    /// The axis is the widget's because only it knows what it means by the other
    /// pair: a vertical menu's `Right` opens a submenu, and a scope that roved on
    /// it would move focus down the list whenever an item had none
    /// ([ADR-0078](../../../../../../book/src/adr/0078-a-focus-scope-has-an-axis.md)).
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
    default void onFocusChanged(boolean focused, boolean fromKeyboard) {
    }
}
