# 444. A page stands aside for a modal

Date: 2026-09-20

## Status

Accepted. Amends [ADR-0442](0442-a-page-is-a-child-window-where-the-window-system-allows-one.md),
which made `web-view` a widget and listed the sharp edges that came with it.
This answers one of them and leaves the rest standing.

## Context

[ADR-0441] and [ADR-0442] together made a page a **child platform window** of
the application's, positioned over the widget's box. That buys layout and costs
compositing: a child X11 window is stacked above its parent's own drawing, and
nothing Goldberry rasterises into the frame can be painted over it. `WebView`'s
javadoc listed the consequences and the showcase's `WebScreen` was built to
demonstrate the worst of them:

> Press it and then `Escape`: the dialog was there the whole time, holding the
> keyboard, exactly where it could not be seen.

A `dialog` is the one item on that list that cannot be left as a caveat, and the
reason is what a modal *is*. A tooltip that is hidden by a page is a tooltip
nobody reads. A modal that is hidden by a page is an application that has
stopped responding: it holds the keyboard and the pointer, it is waiting for an
answer, and the user can neither see the question nor reach the buttons. The
application looks broken, and the only way out is a key the user has no reason
to guess.

`docs/dialog-over-web-view.md` is the survey. Four options were looked at, and
the table that decides it is which platform each one holds on — remembering that
Wayland embeds nothing at all, so the problem exists on X11, and would exist on
Windows and macOS once `goldberry_webview_create_embedded` has branches for
them.

## Decision

**While a modal is in force, the `web-view` widget parks its page.** The child
window is moved off the parent's top-left corner, by more than its own size, so
the parent clips every pixel of it; when the modal goes, it is moved back.

Two things make this cost nothing:

**It moves rather than resizes.** `goldberry_webview_set_bounds` calls
`gtk_window_resize` as well as `XMoveResizeWindow`, deliberately, so that the
WebKit widget inside lays out to the new size. A page parked at 1x1 would
therefore reflow the document to one CSS pixel of width and reflow it back from
there — and the scroll position after that is not something to reason about.
Keeping the size and changing only the origin is not a reflow at all.

**It uses the call that already exists.** No new export, no C, and no ABI bump:
`set_bounds` is already called on every frame the box moves.

The widget needs one new fact, and it is a fact about the **window**: *is a
modal in force*. `Host.isModal()` answers it, from the `modal` element
`PointerRouter` already finds once per frame beside the hit-test regions. It
could not be found by walking up from the page: a filling `Overlay` is a
*sibling* of the content under `WindowRoot`, not an ancestor, so
`BuildContext.findAncestorState` looks straight past it.

**The page visibly disappears while the dialog is up**, and that is the decision
rather than a side effect. A modal is a demand for the whole of the user's
attention; a browser's own modal dims the content behind it and here the content
vanishes instead, which is a difference of degree. The alternative is a modal
nobody can see.

**Modals only.** A `tooltip`, a `popover` and a `toast` over a page are still
invisible where they overlap. Blanking a page to show four words in a corner
would be worse than the problem, and `WebView`'s javadoc still says so.

## Alternatives considered

**Hide the page with `gtk_widget_hide`** (six lines of C, ABI 5). Tells the
engine plainly that it is not visible, which parking does not — a clipped-out
child is still mapped and may keep painting and keep running
`requestAnimationFrame`. Rejected **for now**, not on merit: it costs an export
and an ABI bump, and `goldberry_webview_embed_into` carries a comment saying the
show-then-realize-then-reparent order was got wrong once and is load-bearing.
Whether `gtk_widget_show` restores an already-reparented window into the same X
parent at the same position is exactly that class of question. It is the
fallback, it is designed, and it is what to build if a parked page turns out to
burn a core.

**Give the dialog its own platform window.** A full-owner-size transparent
`SDL_CreatePopupWindow` is a separate top-level and therefore stacks above the
owner's child windows, so the dialog would be genuinely on top. Rejected, and
not merely deferred:

- The stacking argument is an **X11** argument. It says nothing about Windows or
  macOS, where the page is a child HWND or an `NSView` rather than an X child.
- **The scrim could not be translucent on X11.** SDL 3.4's X11 backend selects a
  32-bit visual only for windows carrying `SDL_WINDOW_OPENGL`
  (`SDL_x11window.c:601-651`), and Goldberry's windows do not. A dialog whose
  scrim is opaque is a grey rectangle over the application, which is worse than
  the thing being fixed — on the one platform where the problem exists.
- It still needs a piece of this decision anyway, because the keyboard is
  WebKit's while the page is up.
- It would fork `Dialogs.show` into a popup path that ships and an in-frame path
  that every golden image exercises. That is the wrong way round for the
  most-used overlay in the toolkit, to fix one widget on one platform.

**An offscreen engine**, which dissolves this and every other item on ADR-0442's
list at once, is `docs/servo-web-view-plan.md` and is not started. It is a reason
to keep this small, not a reason to wait: a dialog over a page is wanted now, and
if Servo lands this is deleted along with the embedding path it belongs to.

**Do nothing and document it**, which is what `WebScreen` did. The documentation
was good and the behaviour was still an application that appears to hang.

## Consequences

**A `dialog` over a page works, on every platform that can embed one.** The
showcase's web tab is the demonstration and says what to look for.

**`Host` gains a method**, defaulted to false, so nothing that implements it
breaks and an offscreen render — every golden image — answers correctly without
knowing anything. `PointerRouter.isModal()` is the same answer the focus trap
and the pointer rules already use, so there is one notion of modality rather
than two that can drift.

**One widget knows about this and nothing else does.** `Dialogs.show` is still
`host.fill`, [ADR-0176]'s closing animation is untouched, focus composites are
untouched, and every golden image is still valid.

**It is one frame late**, like `Host.anchor` and for the same reason: the modal
is found from the hit-test snapshot of the frame that was painted. The page
parks on the frame after the dialog appears. At 60 Hz that is 16 ms of a dialog
drawn behind a page, which is not visible and is not worth a second mechanism to
remove.

**A parked page is still mapped**, and whether WebKit keeps painting, animating
or playing video behind the parent's clip is not known. If it does, the cost is
a page's worth of CPU for as long as a dialog is up. That is the measurement
that would promote the `gtk_widget_hide` alternative above, and it has not been
taken.

**The end-to-end behaviour has no automated test, and cannot have one.** No
golden image can contain a page at all, which is the fact `WebView` has always
documented. What is tested is the arithmetic — that parking moves and does not
resize, that the parked origin is fully outside the parent's box and inside an
X11 `INT16` at every scale — and that the router reports modality. That the
widget calls it on the right frame is checked by running the showcase.

**A finding fell out of this and is not fixed here.** If the reading of
`SDL_x11window.c` above is right, `SDL_WINDOW_TRANSPARENT` does nothing for a
non-OpenGL window on X11 — which would mean every Goldberry menu, dropdown and
tooltip has opaque square corners on an X11 session today, `Popup`'s
`frame.fill(0x00000000)` notwithstanding. Wayland honours the flag, which is
consistent with nobody having noticed. It is recorded in
`docs/dialog-over-web-view.md` §7 as U1, it is a screenshot to confirm, and it
is a separate defect from this one.

[ADR-0176]: 0176-a-dialog-is-a-widget-and-showing-one-is-not.md
[ADR-0441]: 0441-a-web-page-is-a-window-not-a-box.md
[ADR-0442]: 0442-a-page-is-a-child-window-where-the-window-system-allows-one.md
