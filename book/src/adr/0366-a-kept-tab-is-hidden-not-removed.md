# 366. A kept tab is hidden, not removed

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "A tab's content is rebuilt when it is
selected again". Adds `Styled.isHidden()` to `:core`.

## Context

§5 asks `tabs` for "lazy content instantiation", and a strip builds only the
selected tab's content. The TODO entry said that is right, and that it means a
scroll position, a caret or a half-typed form in a background tab is gone, with
nowhere to put it but the application's model.

Keeping content alive needs a subtree that stays mounted, with its elements and
their state, while it takes no layout, paint, input or focus. `Widget.nothing()`
removes a subtree's content, and `:disabled` keeps a subtree painted. The toolkit
had nothing in between.

## Decision

**`Styled.isHidden()` keeps a node and its subtree mounted and unused. `tabs`
with `keep-alive` wraps each tab it has shown in a keyed `tab-page` that is
hidden unless selected.**

- `Styled.isHidden()` defaults to false. `WidgetRenderer.render` returns no box
  for a hidden node and does not render under it, so it has no layout, no paint,
  no hit-test region, and no `Measured` or `Located` notifications.
  `PointerRouter.isFocusable` refuses anything under a hidden ancestor, walking up
  as `isDisabled` does, and `refocus` lets go of a focus that has become hidden.
- `Tabs.keepAlive(boolean)` and `keep-alive=#true` in markup. `TabsState` keeps
  the values it has shown and still has, in first-shown order. The panel then
  holds one `TabPage(value, content, selected)` per kept tab, keyed by value so
  the reconciler matches a page to the same elements whichever tab is selected.
- A tab never selected is still not built. A closed tab is dropped from the kept
  set, and its page unmounts.
- Off by default, which is §5's default.

## Consequences

- Hidden content still costs its elements and their state, and a rebuild of a
  hidden subtree still runs when its widgets change; only rendering is skipped.
- `isHidden` is not a stylesheet feature, so §8's subset still has no
  `display: none`.
- The showcase's chapter strip keeps its tabs alive, and each chapter has an
  unbound note field to show it; `gallery-navigation` is re-blessed.
