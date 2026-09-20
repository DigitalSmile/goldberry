# Showing a dialog over an embedded `web-view`

Investigation, 2026-09-20. **Option A2 is now built** — see
[ADR-0444](../book/src/adr/0444-a-page-stands-aside-for-a-modal.md); option B is
**answered**; U1 below was confirmed by reading the SDL source and is a separate,
open defect. The rest of this document is the investigation as it was written,
kept because the reasoning is what the decision rests on.

It exists because
[ADR-0442](../book/src/adr/0442-a-page-is-a-child-window-where-the-window-system-allows-one.md)
accepted a sharp edge — *"nothing painted can cover it"* — as permanent, and the
question worth asking is whether it is permanent for the reason given or only
for the design given. The reason turns out to be real and the design turns out
to have one way around it, on one platform, at a price.

Status legend as in [`todo-sweep.md`](todo-sweep.md): **done** means code, tests
and ADR have landed; **open** means not started; **answered** means decided not
to build, with the reason written down.

Everything below about X11 stacking, SDL's popup windows and SDL's transparency
handling was read off the vendored SDL 3.4.14 source in
`natives/.deps/linux-x64/sdl3-src` and off `goldberry_webview.cc`. **None of it
has been run.** Section 7 lists what that leaves unproven, and it is not a short
list.

## 1. Why nothing can cover a page today

The page is a child *window*, not a layer in the frame, and the whole of the
problem is in eight lines of
[`natives/src/main/cmake/goldberry_webview.cc`](../natives/src/main/cmake/goldberry_webview.cc):

```c
gtk_window_set_decorated(GTK_WINDOW(window), FALSE);
gtk_window_resize(GTK_WINDOW(window), width, height);
gtk_widget_show_all(window);
gtk_widget_realize(window);
...
Window child = gdk_x11_window_get_xid(gdk);
XReparentWindow(display, child, static_cast<Window>(parent), x, y);
XMoveResizeWindow(display, child, x, y, width, height);
XMapWindow(display, child);
```

`goldberry_webview_embed_into` takes the engine's own top-level X11 window and
makes it a child of the Goldberry window, at the widget's box. That is what
makes a `web-view` a widget at all, and it is what makes it uncoverable.

X11's rule is not about compositing or about window managers. A window's
children are drawn **above the window's own contents**, always, and the parent
has no way to draw over a mapped child: it can only draw into its own drawable,
which is below the child in the same subtree. Goldberry paints a frame into the
SDL window's surface through `SDL_GetWindowSurface` and
`SDL_UpdateWindowSurfaceRects`. Every pixel of a dialog, popover, tooltip and
toast goes into that surface. The page's window is above it by construction.

So the statement in
[`WebScreen`](../example/src/main/java/io/github/digitalsmile/goldberry/example/ui/WebScreen.java)
is accurate, and it is the current, deliberate position:

> "Open a dialog over the page" is here to show the **one thing an embedded page
> cannot do**, and to show it rather than assert it. A `dialog` is an ordinary
> widget in the overlay layer, painted into the frame; the page is a platform
> window *above* that frame. So the dialog is drawn, and it is invisible
> wherever the page covers it — its scrim dims the margin around the page and
> nothing else, and its buttons cannot be pressed because the press lands on
> WebKit.
>
> An application that needs a modal over a page has to take the page away first.
> There is no compositing answer, because there is no raster.

The last sentence is the finding this document does not overturn. What it
questions is the word *"take away"*: the two things that can be moved are the
page and the dialog, and only one of them has been tried.

### Where the question even arises

| Session | Is a page ever inside the window? | Can a dialog be over it? |
|---|---|---|
| Linux / X11 and XWayland | yes, reparented | **no — this document** |
| Linux / Wayland | never; the widget opens nothing and says why | not a question |
| Windows | `SetParent`, **not written** | not a question yet |
| macOS | `addSubview:`, **not written** | not a question yet |

`goldberry_webview_create_embedded` returns `nullptr` on everything that is not
Linux+GTK — the `#else` branch is a comment naming the calls nobody has written.
So this is an **X11-only problem today**, and any fix that is X11-only is not
thereby narrower than the feature it fixes. On Wayland the dialog already works
perfectly, because there is no page in the window for it to lose to; ADR-0442's
own note says the Wayland case *"is its own half of the demonstration"*.

## 2. What the toolkit already has

Worth stating before the options, because three of them turn on it:

- `Dialogs.show(host, dialog)` is one line —
  [`host.fill(dialog)`](../widgets/src/main/java/io/github/digitalsmile/goldberry/widgets/overlay/dialog/Dialogs.java).
  A dialog is an ordinary filling `Overlay` in the window's own tree. It has no
  window and never has had one.
- Modality is one flag on the tree, `Handles#isModal`
  ([ADR-0232](../book/src/adr/0232-modality-is-one-flag-and-not-a-scrim.md)),
  read per frame by `PointerRouter` into a private `modal` field. **Nothing
  public asks "is a modal in force"**, which is the one small gap option A has
  to close.
- The backend can already make a platform window that is not the main one:
  `Sdl3Backend.createPopup` over `SDL_CreatePopupWindow`, with
  `SDL_CreatePopupWindow`, `SDL_SetWindowPosition`, `SDL_SetWindowSize`,
  `SDL_GetWindowProperties`, `SDL_GetNumberProperty` and
  `SDL_GetPointerProperty` all already in
  [`goldberry.symbols`](../natives/src/main/cmake/exports/goldberry.symbols).
  Option B needs **no new SDL export**.
- A popup already fills itself with `0x00000000` and relies on the window being
  transparent — `Popup.java`'s `frame.fill(0x00000000)`, *"transparent, not a
  colour"*. Section 4 is about whether that is true on X11.
- **No popup of any kind takes the platform keyboard**: `NOT_FOCUSABLE` is on
  all three kinds in `Sdl3Backend.createPopup`, deliberately
  ([ADR-0189](../book/src/adr/0189-no-popup-holds-the-keyboard.md),
  [ADR-0144](../book/src/adr/0144-a-popup-goes-away-when-the-application-does.md)),
  and the owner window forwards keys to whatever popup is open
  ([ADR-0104](../book/src/adr/0104-a-popup-is-measured-then-placed.md)).
- The shim's ABI is `GOLDBERRY_WEBVIEW_ABI 4`, and Java refuses a library that
  disagrees. Any new export in `goldberry_webview.cc` is an ABI bump and a
  rebuild of the optional native, which is the cost floor for option A.

## 3. Option A — the page yields

**What it changes.** While a modal overlay is mounted, the `WebView` widget
takes its page off the screen; when the modal goes, it puts it back. The dialog
is then an ordinary overlay over an ordinary frame, which is what every other
platform and every other widget already gets.

Two ways to do it, and they are not equally cheap.

### A1 — `goldberry_webview_set_visible`, a new shim export

`gtk_widget_hide` / `gtk_widget_show` on the page's GTK window, which unmaps and
remaps the X child. Roughly six lines of C beside `goldberry_webview_set_bounds`,
plus `visible(boolean)` on `BackendWebView`, plus the plumbing through
`NativeWebView`, `Webview` and `WebviewCalls`, plus **ABI 4 to 5**.

What is *not* obviously cheap is the remap. `embed_into` carries a comment
earned the hard way:

> Shown BEFORE the reparent: `gtk_widget_realize` alone creates the shell's
> window without mapping the WebKit widget inside it, and the result is a
> correctly positioned rectangle with nothing in it — which is exactly what the
> first attempt produced.

GTK is being told to hide a window whose X parent GTK does not know about,
because the reparent was done behind its back with raw Xlib. Whether
`gtk_widget_show` afterwards re-maps into the same parent at the same position,
or resets something, is exactly the class of thing that produced the empty
rectangle the first time. It is cheap to find out and expensive to assume.

### A2 — park it with `set_bounds`, no native change at all

`goldberry_webview_set_bounds` already exists and is already called every frame
the box moves. Moving the page to a coordinate outside the parent's box —
`(-32768, -32768)`, say — leaves it a mapped child of a window it is entirely
clipped out of, which is invisible with no new export and no ABI bump.

**The 1x1 variant of this is wrong**, and the code says why:

```c
// GTK is told as well as X, so the WebKit widget inside lays out to the new
// size rather than staying the size it was mapped at.
gtk_window_resize(GTK_WINDOW(window), width, height);
XMoveResizeWindow(...);
```

A page resized to 1x1 reflows to 1x1 — that is the documented purpose of the
`gtk_window_resize` line. Coming back, it reflows again, at the old size but
from a layout that had one column of pixels. Scroll position after a reflow to
1 CSS pixel of width is not something to reason about; it is something to watch
being lost. **Moving** keeps the size and therefore keeps the layout, and is the
only form of A2 worth trying.

What A2 costs instead is a guess about rendering: a WebKit widget clipped
entirely outside its parent is still mapped, and may or may not keep painting,
keep running `requestAnimationFrame`, keep playing video. A1's unmap at least
tells the engine plainly that it is not visible.

### What A needs from the widget side, either way

`WebView`'s state has to learn that a modal is up. Today it cannot: the flag
lives in `PointerRouter`'s private `modal` field, refreshed in `updateRegions`.
The smallest honest addition is a read on `Host` — *"is a modal in force in this
window"* — answered from the same field the router already maintains, and read
by `WebViewState.sync` on the frame where it already reads `Host.anchor`. That
is one method on an interface that has a hundred, and it is a fact about the
window that other widgets will want.

It is **not** enough to look upward from the element: a filling `Overlay` is a
sibling of the content under `WindowRoot`, not an ancestor of the page, so
`findAncestorState` cannot see it.

### Where A holds

| | Holds? | Why |
|---|---|---|
| X11 / XWayland | **yes, if the remap behaves** | unmapping or moving a child window is not a stacking question at all |
| Wayland | vacuously | no page is embedded, so nothing has to yield |
| Windows | would carry | `ShowWindow(SW_HIDE)` on a child HWND, and a WebView2 in a hidden parent is a documented, supported state — *unverified, and there is no embedding on Windows to verify it against* |
| macOS | would carry | `setHidden:` on the `WKWebView` subview — same caveat |

### What A costs and what it breaks

Nothing in the toolkit changes shape. `Dialogs.show` stays `host.fill`, every
golden image stays valid, the closing animation of
[ADR-0176](../book/src/adr/0176-a-dialog-is-a-widget-and-showing-one-is-not.md)
is untouched, focus composites are untouched, and the `web-view` widget keeps
being the only thing that knows any of this happened.

What it breaks is honesty, a little: **the page disappears while the dialog is
up**. That is a visible, surprising thing, and it is the correct thing — a modal
is a demand for the user's attention and hiding the page behind it is what a
browser's own modal does to the content behind it, except here the page vanishes
rather than dims. The showcase would have to say so, and `WebScreen`'s javadoc —
the current statement that this is impossible — would become the statement of
what happens instead.

It also does nothing for a **tooltip** or a **toast**, which are not modal and
must not blank the page to show four words in a corner. Option A answers
"modal over a page" and leaves "small floating thing over a page" exactly where
it is.

## 4. Option B — the dialog gets its own platform window

**What it changes.** `Dialogs.show` opens a transparent popup window the size of
the owner, containing the scrim and the panel, instead of filling the owner's
overlay layer. A popup is a separate top-level; a reparented page is a child
inside the owner. On X11 those are in different subtrees of the root window, and
the stacking question is answered between the two top-levels rather than inside
one of them.

### Does the stacking argument actually hold on X11?

This is the claim that must not be wrong, so here is exactly what it rests on,
read off SDL 3.4.14:

- `SDL_x11window.c:755` — **every** SDL X11 window, popup or not, is created
  with `X11_XCreateWindow(display, RootWindow(display, screen), ...)`. A popup
  is a child of the **root**, a sibling of the application's own top-level. It
  is not a child of its owner.
- `SDL_x11window.c:653` — `xattr.override_redirect` is `True` when the window
  has `SDL_WINDOW_TOOLTIP` or `SDL_WINDOW_POPUP_MENU`. Goldberry sets one of
  those on every popup (`Sdl3Backend.createPopup`), so every Goldberry popup is
  override-redirect.
- `SDL_x11window.c:871` — `XSetTransientForHint` is set only for a **non-popup**
  window with a parent. An SDL popup is therefore *not* transient-for its owner;
  it is unmanaged and the window manager is not consulted about it at all.
- `SDL_x11window.c:1564` — showing does `X11_XMapRaised`, which puts it at the
  top of the root's stacking order at map time.

From X11's own semantics, stacking among children of the root orders whole
subtrees: a top-level above another top-level obscures that window *and every
child inside it*. The page's window is inside the Goldberry top-level's subtree.
So a popup mapped and raised above the Goldberry window covers the page.

**That is an argument, not an observation.** It is the argument I believe, and
section 7 lists what could still falsify it — chiefly that an override-redirect
window is at the mercy of anything else that raises itself, that a compositing
window manager may treat unmanaged windows in ways not worth predicting from
here, and that nothing has ever mapped a full-owner-sized override-redirect
window in this project.

### The scrim will not be transparent on X11, and that is close to fatal

This is the finding that changes the shape of option B, and it is a fact about
today's code rather than about dialogs:

- `SDL_x11window.c:601-651` — the whole transparent-visual selection is inside
  `#if defined(SDL_VIDEO_OPENGL_GLX) || defined(SDL_VIDEO_OPENGL_EGL)` **and**
  inside the `else if ((window->flags & SDL_WINDOW_OPENGL) && ...)` branch. A
  window without `SDL_WINDOW_OPENGL` falls through to
  `visual = displaydata->visual; depth = displaydata->depth;`.
- `SDL_x11modes.c:147-176` — that display visual comes from `get_visualinfo`,
  which matches at `DefaultDepth(display, screen)`. On an ordinary X server that
  is 24 bits, with **no alpha channel**.
- `Sdl3Backend` never asks for `SdlWindowFlag.OPENGL`, for windows (line 449) or
  for popups (line 530). Goldberry draws through `SDL_GetWindowSurface`.

So `SDL_WINDOW_TRANSPARENT` — which `createPopup` sets on all three kinds — has
**no effect on a non-OpenGL X11 window** in this version of SDL. On Wayland it
does work (`SDL_waylandwindow.c:427` and `:2832` set the surface's opaque region
from the flag), which is consistent with Goldberry's default session being
Wayland and with nobody having noticed.

Two consequences, and the second is worse than the first:

1. A dialog in a popup on X11 would paint its scrim's `rgba(0,0,0,0.4)` onto a
   window with nowhere to put the alpha. What the user sees is either a solid
   rectangle over the whole application or a rectangle of whatever the buffer
   held — either way, not a scrim.
2. **This is not hypothetical and it is not confined to dialogs.** If the
   reading is right, every Goldberry menu, dropdown and tooltip on X11 today has
   square opaque corners where `Popup.java` intends nothing at all. That is a
   bug worth its own investigation whatever happens to this one, and it is
   listed in section 7 as the first thing to look at, because it is visible in a
   screenshot and needs no new code.

Getting real transparency would mean creating the popup on a 32-bit ARGB visual,
which in SDL today means either `SDL_WINDOW_OPENGL` (a renderer Goldberry does
not have) or `SDL_HINT_VIDEO_X11_VISUALID` process-wide (which would change
*every* window, including the main one). Neither is a small change, and neither
is a change to this project's code.

### Keyboard, and the thing that makes B awkward beyond stacking

Popups are `NOT_FOCUSABLE` on purpose, and the owner forwards keys to the
topmost open popup (ADR-0104). That works because the owner has the keyboard.

With an embedded page it may not. ADR-0442 records, as a *feature*:

> The page is a real child window, so the window system delivers its clicks and
> keystrokes to WebKit directly. Nothing forwards events and the pointer router
> never sees them.

If the X input focus is on WebKit's window when the dialog opens, then a
non-focusable popup gets nothing and the owner gets nothing, and the dialog is
visible and dead — the exact inverse of today's failure, where it is invisible
and alive. A focusable popup would fix that and would break the invariant
ADR-0189 and ADR-0144 depend on (`anyWindowFocused` deciding the application is
focused), which is not a trade to make for one widget.

Realistically B needs the page defocused, which means B needs a piece of A.

### What B costs in the toolkit

Much more than A, and in places a dialog has no business touching:

- `Dialogs.show` stops returning an `Overlay` from `host.fill`, or returns one
  that is a lie. Every caller, including `WebScreen` and the showcase, holds an
  `Overlay`.
- **The headless and `dummy` backends have no popups.** `createPopup` answers
  empty, *"the caller falls back to the in-window overlay layer"*
  ([ADR-0102](../book/src/adr/0102-a-popup-is-a-window-the-platform-may-refuse.md)).
  So there would be two dialog implementations, always, and the tested one would
  be the fallback.
- `DialogGoldenTest` renders a dialog in a window's frame. A dialog in a popup
  is not in that frame, so either the goldens go or the golden path is the
  fallback path — which means the golden tests stop testing what ships.
- ADR-0176's closing animation runs in the window's own frame clock; a popup has
  its own paint loop (`Popup.paint`), its own `HitTest.capture`, its own router
  and its own `focusFirst`. A dialog sharing state with the main tree — a form
  it is editing, a `Property` it reads — now spans two element trees.
- Modality (`isModal`) is defined over one tree. A dialog in another window is
  not in the tree the router asks.

That is a large, load-bearing change to the most-used overlay in the toolkit, to
fix one widget on one platform, with a scrim that would not be see-through.

### Where B holds

| | Holds? | Why |
|---|---|---|
| X11 / XWayland | **stacking probably; scrim no** | override-redirect sibling of the top-level, raised on map — but no alpha visual on a non-GL window |
| Wayland | irrelevant, and would work | popups are `xdg_popup` subsurfaces of the toplevel, transparency honoured — and there is no page to cover |
| Windows | argument does **not** carry unchanged | a WebView2 child HWND and an owned popup HWND are both in the owner's z-band; `WS_EX_TOPMOST`/`HWND_TOP` ordering between an owned window and a child of the owner is not something to assert from here |
| macOS | argument does **not** carry unchanged | a `WKWebView` is a **subview**, inside the window's view hierarchy; an `NSPanel` is a separate window in the same level. Ordering an ordinary panel above its parent window is routine, but "above a subview of the parent" is the same statement only because the whole parent window is below — probably fine, entirely unverified |

The honest summary of the last two rows: **B's argument is an X11 argument.** It
may well carry, and neither platform has embedding at all yet, so there is
nothing to test it on.

## 5. Option C — offscreen raster

Already written up in
[`servo-web-view-plan.md`](servo-web-view-plan.md), and it is the only option
that makes the question disappear rather than answering it. A page that is a
buffer is clipped by `scroll`, covered by `dialog`, `popover`, `tooltip` and
`toast`, reached by `opacity` and `transform`, and contained by a golden image —
Phase 4 of that plan lists exactly these, because they are the same list.

It is not the near-term answer for the reason that plan already gives:
`servo_capi` has **no input, resize, scroll, focus or IME**, so Phase 1 is
upstream's review queue, and Phase 0 — the throwaway spike that decides whether
any of it is buildable — **has not started**. Nothing about the dialog question
should wait on it, and nothing about the dialog question should accelerate it:
Phase 0 is worth doing on its own merits and it is an afternoon.

If C lands, A and B both become dead code. That is an argument for making A
small.

## 6. Option D — do nothing

The current position, and it is a defensible one. It is written down in four
places that agree: ADR-0442's *"what an embedded page still cannot do"*,
`WebView`'s javadoc, `BackendWebView`'s javadoc, `core-widgets.md` §9, and
demonstrated on screen by `WebScreen`'s button. Nothing is misleading anybody.

What D leaves is a real hole: **an application cannot ask a question over a
page.** "Discard these changes?", "this link goes to another site", a login
prompt — all of them are modal over content, all of them are ordinary, and the
answer today is "hide the page yourself first", which the application cannot do
either, because `WebView` exposes no handle to the page it opened.

If D is kept, the minimum honest change is to say *that* in `WebView`'s javadoc
rather than only "nothing painted can cover it" — the first is a limitation, the
second is a limitation plus a workaround the reader cannot perform.

## 7. Unknowns

Every claim below is one I could not settle from the repository or from
documented platform behaviour. Each has the experiment that would settle it. The
first two are the ones that decide anything.

| # | Claim in doubt | What would settle it |
|---|---|---|
| U1 | **Does `SDL_WINDOW_TRANSPARENT` do nothing on X11 today?** Read off SDL 3.4.14's visual selection, never observed. If true, every existing menu and tooltip has opaque corners on X11 and this is a live bug. | Run the showcase with `-Dgoldberry.backend.videoDriver=x11`, open a menu over contrasting content, screenshot the corners. No new code. Then `xwininfo -id <popup>` for the visual's depth. |
| U2 | **Does an override-redirect SDL popup reliably stack above a reparented X11 child of the owner, on the window managers people use?** The X semantics say yes; mutter, KWin, i3 and picom have not been asked, and an unmanaged window's position in the stack is not defended against anything that raises itself. | A ~60-line C program: an SDL window, a GTK window reparented into it, an `SDL_CreatePopupWindow` over both. Run under mutter/X11, KWin/X11, i3 and a bare `xterm` session, with and without a compositor. Watch what happens on click, on alt-tab, and on a second application raising itself. |
| U3 | Does `gtk_widget_hide` followed by `gtk_widget_show` restore an already-reparented page into the same X parent at the same position? The comment in `embed_into` says the show/realize order is load-bearing and was got wrong once. | Add `goldberry_webview_set_visible` behind the ABI bump, hide and show it from `:example:run` with the real showcase, and watch whether the page comes back or comes back empty. |
| U4 | Does moving a page's child window fully outside the parent's box (A2) stop WebKit painting/animating, and does it preserve scroll position on return? | Same run: park at `(-32768,-32768)`, scroll the GitHub page first, restore, check the scroll offset and whether a CSS animation kept time. |
| U5 | **Who holds the X input focus while an embedded page is up?** ADR-0442 says the window system delivers keystrokes to WebKit directly. Whether the SDL owner window still receives keys — and therefore whether ADR-0104's key forwarding could reach a dialog in a popup at all — is untested. | `xdotool getwindowfocus` while the showcase's web tab is focused, and press `Escape` with the existing dialog open: today's `WebScreen` javadoc claims Escape closes it, which is itself a claim about who has the keyboard. |
| U6 | Does a WebView2 child HWND stay below an owned popup HWND on Windows? Does a `WKWebView` subview stay below an `NSPanel` on macOS? | Not answerable here, and not worth answering until `goldberry_webview_create_embedded`'s `#else` branch is written at all. Blocked on ADR-0442's own "unverified". |
| U7 | Whether a full-window transparent popup is even accepted where `createPopup` succeeds — SDL clamps popups to display bounds (`X11_ConstrainPopup`), and a popup exactly the owner's size on a maximised window is the edge case. | Part of U2's harness: ask for the owner's exact size at offset `(0,0)` and read back `SDL_GetWindowSize`. |
| U8 | Whether hiding the page is acceptable to anyone. This is a product question and there is no experiment, only a decision: a modal over a page makes the page vanish, and the alternative is a modal nobody can see. | Write it into `WebScreen` and look at it. |

## 8. Recommendation

**Do U1 first, then A2, and treat B as answered.**

U1 is a screenshot and costs nothing, and if it confirms what the SDL source
says then there is a real, shipping, unrelated defect in every popup on X11 that
matters more than this question does. It also removes option B's best argument
before anybody spends a week on it: a dialog window whose scrim cannot be
translucent is not a dialog, it is a grey rectangle over the application.

**Option B is answered, not merely deferred.** Its stacking argument is sound on
X11 and is an X11 argument only; its scrim does not work on the one platform
where the problem exists; it needs a piece of option A anyway to get the
keyboard off WebKit; and it would fork `Dialogs.show` into a popup path that
ships and a fallback path that the golden tests exercise. That is the wrong way
round for the most-used overlay in the toolkit, to fix one widget on one
platform.

**Option A is the answer, and A2 is the version to try first** — parking the
page outside the parent's box with the `goldberry_webview_set_bounds` that
already exists, no new export, no ABI bump, no C to write. If U4 shows that a
clipped-out page keeps burning CPU or comes back scrolled to the top, A1's
`goldberry_webview_set_visible` is six lines of C and ABI 5, and the fallback is
already designed. Either way the toolkit's shape does not change: `Dialogs.show`
stays `host.fill`, the goldens stay valid, ADR-0176's animation is untouched,
and exactly one widget knows anything happened.

**Option C stays the real answer** and is unaffected by any of this. If Servo's
Phase 0 succeeds, A is deleted along with the embedding path it belongs to. That
is a reason to keep A small, not a reason to wait for C — C is not started, and
a `dialog` over a page is wanted now.

### The smallest next step

One experiment and one method, in that order, neither of which is a commitment:

1. Run the showcase under `-Dgoldberry.backend.videoDriver=x11` and photograph a
   menu's corners (U1). Then run `WebScreen`, park the page by calling the
   existing `bounds(-32768, -32768, w, h)` from a scratch branch, open the
   dialog, and look (U4, U8). No repository code needs to change to learn both
   answers.
2. If they come back well: add the one public read that option A is missing —
   *"is a modal in force in this window"* on `Host`, answered from the `modal`
   field `PointerRouter` already refreshes every frame — and have
   `WebViewState.sync` park and restore the page from it. One ADR, one method,
   one widget.
