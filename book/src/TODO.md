# TODO

What is deferred, known-broken, or specified and unbuilt. What *is* built is in
[status.md](status.md).

These are tracked in the decision log and need answers before the milestones they
block can be scheduled honestly. Every entry says what the gap is, why it is one,
and — where it is known — what it would take, with the decision record that
argued it. An entry leaves the top
half when it is answered and moves to [Answered](#answered) rather than being
deleted, because each of those records a trap somebody hit and the reasoning that
got out of it.

**Where the documents disagree with each other** — as opposed to with the code —
is listed in `docs/ARCHITECTURE.md` §17.1. `docs/design-system.md` and
`docs/core-widgets.md` are the authority; the architecture document is a summary
of them and records where it knowingly departs. The seven open ones are the
platform primary modifier for accelerators, whether the catalog is one module or
two, `text style=` against `text class=`, a disabled container disabling its
descendants, and — since `docs/content-widgets.md` joined the plan — where the
emoji font lives and who owes its attribution, whether `goldberry-charts` is an
artifact, and what "zero new natives" costs (see [Content
modules](#content-modules)). **Pixel-precise wheel deltas left this list** —
[ADR-0115](adr/0115-a-wheel-reports-a-fraction-and-a-detent.md) settled it as a
difference rather than an agreement: what §2.4 wanted from "pixel-precise" is
scrolling that does not quantize, and a fractional line delivers that without
the mechanism the sentence named.

## Overlays, popups and windows

- ~~**`menubar` is not built, and it wants a menu that outlives one opening.**~~
  **Both halves ship, and the model that outlives an opening turned out to be the
  one the author already wrote.** What is built and discarded per opening is the
  *popup*; a `Menu` is a value, so a `menubar` holding one holds it for as long as
  the bar is mounted. `Accelerators` walks that description and binds every
  command with a key on it, with no menu on screen and none needed. A bar's
  children are `item`s and a nested `item` is a heading, so no markup was added. —
  [ADR-0163](adr/0163-a-menu-bar-owns-its-menus.md),
  [ADR-0106](adr/0106-a-menu-is-a-widget-and-opening-one-is-not.md)
- **A bare `Alt` tap does not activate the menu bar, and `F10` does.** §8 asks for
  "`Alt`-style keyboard activation". A bare `Alt` is a **modifier released with
  nothing in between**, and a `Shortcut` here is a key plus modifiers — `Key` has
  no `ALT` to name, because a shortcut on a modifier alone can never fire. Doing
  it properly needs key-release tracking with a "nothing happened in between"
  rule, at the window level where the `InputWatcher` already lives. `F10` is the
  companion binding on every platform that has the `Alt` one and is what ships. —
  [ADR-0163](adr/0163-a-menu-bar-owns-its-menus.md)
- **`Left` and `Right` do not move between menus while one is showing.** They walk
  the headings when the bar has focus and nothing is down, which is the horizontal
  focus scope doing its job. Once a menu is open the focus is in a **different
  window**, so the bar never sees the arrow — the same missing item-to-popup
  callback that keeps `Left` from closing a submenu, one entry below. Fixing
  either would probably fix both. —
  [ADR-0163](adr/0163-a-menu-bar-owns-its-menus.md),
  [ADR-0112](adr/0112-a-menu-follows-the-pointer-and-lights-for-the-keyboard.md)
- **An accelerator is unbound by key, so a `menubar` going away can take somebody
  else's binding with it.** The window's shortcut map is keyed by the shortcut and
  not by who bound it, so `Host.removeShortcut(Ctrl+O)` removes whatever is on
  `Ctrl+O` — including a binding the application made afterwards. Two things
  claiming one key is already a conflict where the last registration wins; this is
  that conflict at the other end. Fixing it means the map remembering owners, and
  `menubar` would be the only thing that used it. —
  [ADR-0163](adr/0163-a-menu-bar-owns-its-menus.md)
- ~~**The keyboard menu key does not open a context menu.**~~ **It does, and so
  does `Shift+F10`.** The entry named both pieces correctly: `Key.MENU` is SDL's
  `SDLK_APPLICATION`, and the element-wise anchor turned out to already exist as
  `anchorOf`, which the tooltip path had been using since ADR-0111. `Shift+F10`
  is bound beside it because a Mac keyboard has no menu key; bare `F10` is
  deliberately left to the `menubar` (ADR-0163). The walk up to the widget that
  named the menu is now one method both halves call, because "a right-click on a
  label is a right-click on the button" and "the menu key on a focused button is
  that button's menu" are the same rule. —
  [ADR-0208](adr/0208-a-context-menu-answers-the-keyboard.md),
  [ADR-0108](adr/0108-a-context-menu-is-a-name-on-a-widget.md)
- **A right-click does not select what it is over.** Every file manager selects the
  row you right-click before opening its menu; that is the application's to do in
  its handler today, because the toolkit has no notion of what "select" means for
  an arbitrary widget. —
  [ADR-0108](adr/0108-a-context-menu-is-a-name-on-a-widget.md)
- **A keyboard `Right` into a submenu waits 150ms**, because it goes through the
  same hover-intent path as a pointer. Wrong, and one line to fix once `Item` can
  tell a hover from a keypress. —
  [ADR-0106](adr/0106-a-menu-is-a-widget-and-opening-one-is-not.md),
  [ADR-0112](adr/0112-a-menu-follows-the-pointer-and-lights-for-the-keyboard.md)
- **`Left` does not close a submenu.** The arrow that opens one has no opposite:
  it needs a callback from the item to the *popup it is in*, which is one more
  thing `Menus` would have to wire. —
  [ADR-0112](adr/0112-a-menu-follows-the-pointer-and-lights-for-the-keyboard.md)
- **Nothing marks the row whose submenu is showing.** A row is `:focus-visible`
  when the keyboard is on it and `:hover` when the pointer is, and neither says
  "this is the branch that is open" — which is what a chevron rotating or a row
  staying highlighted would say. —
  [ADR-0113](adr/0113-a-submenu-is-placed-beside-its-menu.md)
- ~~**A `message` cannot go away with a fade.**~~ **It can, by reversing the
  order**: the × fades the banner while it is still described and tells the
  application when the fade is over, so nothing has to outlive the description.
- ~~**The sibling reflow is still not built, and `toast` is now the thing that
  could build it.**~~ **It is built, and which toasts move turned out to be a
  fact about the overlay layer rather than about the widget.** A `toaster` is
  pinned to a corner and `controls.css` puts the newest toast at that end, so the
  column is anchored by its *newest* member: a hole in the middle leaves
  everything between it and the corner alone, and the **older** half travels in
  to close it. The ordinary case therefore moves nothing — a stack that shares a
  timeout loses its oldest first, and the oldest has nothing older to move.
  Neither number was `Host.anchor(id)` in the end: the height comes from
  `Measured`, banked every frame because the toast is gone by the time it is
  wanted, and the gap comes from `toaster { gap }` through the channel ADR-0177
  opened for the frame clock. A column of `message`es still cannot have it, for
  ADR-0175's unchanged reason: a banner has no owner to hold the list. —
  [ADR-0178](adr/0178-a-stack-closes-its-own-hole.md)
- ~~**A toast cannot be dismissed by clicking it**~~ **The plate is the
  affordance now.** What §7's omission of a × meant is that a toast does not need
  a *second* affordance competing with its action for a 360×40 plate — not that a
  persistent one should be undismissable. The click was already being swallowed,
  because the plate is hit-testable and a click on it reached nothing and did
  nothing. The trade-off, stated: a click aimed at the action button that misses
  it dismisses without acting; the button is told first, so a hit is never lost. —
  [ADR-0182](adr/0182-a-select-may-hold-more-than-one.md)
- **A toast is not announced**, which is M5's AccessKit bridge like every other
  widget's semantics — and the one place in the catalog where the absence really
  costs something, because a notification nobody sees is exactly what §7's "live
  region" is for. —
  [ADR-0177](adr/0177-a-toast-is-a-queue-and-the-stack-is-the-widget.md)
- **A closing overlay used not to animate at all**, and every golden passed. A
  golden drives `render` by hand and never asks whether the frame loop would
  have, so a widget that answered `isAnimating` with `false` while it was fading
  produced perfect pictures of an animation that never ran. `dialog` had it and
  it is fixed; the general lesson has nowhere to live except here, because the
  corpus cannot catch this class of bug **by construction** — an assertion on
  `isAnimating` is the only thing that can. —
  [ADR-0176](adr/0176-a-dialog-is-a-widget-and-showing-one-is-not.md)
- ~~**A tooltip is plain text and has no maximum width of its own**~~ **It has one
  now: 320, and the number is a judgement rather than a specification.** §2's
  metrics row gives a tooltip a padding, a radius and two delays and no width, so
  this is `Toaster.DEFAULT_MAXIMUM`'s kind of decision — without it a sentence of
  help text is a ribbon across the window that is harder to read than no tooltip.
  The other three "consumers" of `max-width` turned out not to be: **`toast` keeps
  its width** on the design argument its own note already made — the same 360 on
  every toast is what makes a stack read as a stack, and a maximum would give the
  ragged pile back; **`popover`'s `minimumWidth` is a runtime measurement**
  (`field.size().width()`) that no declaration can express (ADR-0145); and
  **`text-area`'s max rows is built** and is a row count rather than a length. —
  [ADR-0181](adr/0181-a-box-may-say-how-small-and-how-large.md)
- **A `message` takes no `bind=`, so a banner whose text comes from a model has
  to be described away rather than emptied.** A bound banner would be *present
  and empty* when the value was blank — a bordered box with 12px of padding
  saying nothing — and §8's subset has no `display`, so no widget can take itself
  out of a layout. Whatever describes it can, which is why
  `Message.summary` returns an `Optional`, and why the showcase's **Notifications**
  screen is Java where its neighbours are documents. What would close this
  properly is a way for a widget to describe *nothing*, which the element tree has
  no word for and which `collapse`, `group-box` and `field-message` have each
  worked around differently. —
  [ADR-0175](adr/0175-a-banner-says-its-kind-twice.md)
- **`--gb-*-line` has one consumer, and the widgets that should be next have not
  been looked at.** The third rank exists because §7's banner draws a hue as a
  glyph and a border; a `field`'s `:invalid` edge and a `badge`'s border are the
  same thing and still read the hue directly. `ContrastTest`'s 3:1 sweep covers
  the *tokens*, not every widget that draws one, so this is a survey somebody has
  to do rather than a failure waiting to happen. —
  [ADR-0175](adr/0175-a-banner-says-its-kind-twice.md)
- **Every clock-driven arrival costs one wasted frame.** The renderer asks
  whether a node is animating *before* it draws it, so the frame that finishes an
  arrival still reports one more and the frame after it is the one that goes
  quiet. Harmless and worth writing down: it is why a golden of an arrival has to
  render three times, and it is the shape of every `Phase` in the catalog rather
  than anything about banners. —
  [ADR-0175](adr/0175-a-banner-says-its-kind-twice.md)
- **`collapse` and `carousel` never stop asking for frames.** Both decide at
  *build* time whether their part is animating — `showing ? this::visibility :
  null` — so an open section reports `isAnimating` for as long as it is open,
  and only a rebuild takes it back out of the loop. §1.7's "the frame loop is
  fully idle when no animation is active" is therefore false for any window with
  an open `collapse` on it. `message` asks its `Phase` instead and does not have
  the bug; the fix for the other two is the same three lines. —
  [ADR-0175](adr/0175-a-banner-says-its-kind-twice.md),
  [ADR-0166](adr/0166-a-raised-thing-is-told-apart-by-its-edge.md)
- **A tooltip is plain text, has no maximum width of its own and does not follow
  the pointer.** All three as `docs/core-widgets.md` §7 specifies for v1, and all
  three are what "rich content" would change. The 500ms delay is not configurable
  either: §7 says "after delay" and does not say how long, so a
  `--gb-tooltip-delay` token is a design-system question rather than an
  implementation one. —
  [ADR-0105](adr/0105-a-tooltip-is-an-attribute-not-a-widget.md)
- **`PointerRouter` has one listener slot, not a list.** A second consumer of
  "the hovered or focused node moved" needs a real listener list *and* a decision
  about what it means for two things to react to one hover. —
  [ADR-0105](adr/0105-a-tooltip-is-an-attribute-not-a-widget.md)
- ~~**A menu caps itself by estimate, not by measurement.**~~ **It measures now,
  and so does `select`.** The popup facility takes a `Host.Fit` — a callback
  handed what the content measured and the room it has, between the measure and
  the place — so the guess and the second copy of `--gb-menu-item-height` are both
  gone. A twenty-row menu measures 667px where the estimate said 696, which is 29px
  of menu needlessly wrapped on a short display and nothing at all on a tall one.
  Returning the content unchanged costs nothing; returning something else costs a
  second element tree, which is the right way round because nearly every popup
  fits. —
  [ADR-0179](adr/0179-a-popup-says-what-it-measured.md),
  [ADR-0118](adr/0118-a-popup-that-does-not-fit-scrolls.md)
- **`Placement` still clamps, and now two callers have stopped asking it to.** A
  popup taller than the work area is clamped to the near edge exactly as before;
  what changed is that `menu` and `select` cap their own content first, from a
  measurement rather than a guess. Any other caller that opens an oversized popup
  and offers no `Host.Fit` gets the old behaviour, which is right for a facility
  that cannot know what its content means — a tooltip that scrolled would be a
  tooltip that should have been a dialog. —
  [ADR-0179](adr/0179-a-popup-says-what-it-measured.md),
  [ADR-0104](adr/0104-a-popup-is-measured-then-placed.md),
  [ADR-0118](adr/0118-a-popup-that-does-not-fit-scrolls.md)
- **Nothing re-places an open popup.** Move or resize the window with a menu open
  and the menu stays where it was put; `Popup.move` exists and nothing calls it. A
  `popover` that follows a scrolling anchor is the case that needs it, and it
  needs to know the anchor moved, which today nothing reports. —
  [ADR-0104](adr/0104-a-popup-is-measured-then-placed.md)
- **A popup may not give the *platform's* keyboard focus back, and the widget
  layer has nothing to do with it** — **the second half of this entry was wrong
  and has been measured.** A popup gets its own tree and its own router, and
  nothing in the open or close path touches the owner's: a probe through the real
  launcher, with a widget logging every focus change, saw a menu open and close
  over a focused control without that control losing focus once. So there is
  nothing to remember and nothing to restore at the router level. What is left is
  the platform's own window focus — SDL gives a `POPUP_MENU` window focus on some
  drivers and not on others — which the headless backend cannot show and which no
  test here can currently reach. —
  [ADR-0180](adr/0180-the-keyboard-goes-back-where-it-was.md),
  [ADR-0104](adr/0104-a-popup-is-measured-then-placed.md)
- **Two popups do not know about each other.** A submenu chain — opening one closes its
  siblings but not its parent — is `menu`'s to arrange; the launcher's light dismissal
  closes all of them at once, which is right for one popup and wrong for a chain. —
  [ADR-0103](adr/0103-a-popup-is-a-second-tree-in-a-second-window.md)
- **A popup's contents inherit nothing from the widget that opened them.** They are the
  root of a second element tree, so no `color`, no `font-size` and no descendant
  selector reaches in. Right for a menu, whose items are a list rather than part of a
  button's subtree; a limitation for `tooltip`, which wants the styling of the thing it
  describes, and the answer there is to pass the anchor's resolved style in rather than
  to reparent anything. —
  [ADR-0103](adr/0103-a-popup-is-a-second-tree-in-a-second-window.md)
- **Nothing hit-tests an overlay by rule.** The pointer router tests against the painted
  frame ([ADR-0054](adr/0054-hit-testing-runs-against-the-painted-frame.md)) and an
  overlay is in that frame, so a button inside one is reachable today by the accident of
  paint order rather than by anything anyone wrote down. A modal `dialog` needs the rule
  stated — the topmost overlay takes the pointer first, and a modal one takes it
  exclusively — and that belongs with the widget that needs it rather than with the
  layer. — [ADR-0100](adr/0100-a-window-has-a-layer-above-its-application.md)
- **A widget can reach its window, and the in-window overlay layer is still the
  application's.** `BuildContext.host()` exists now, because `select` was the
  second consumer and one is not enough to design an interface against — so a
  control that must open something for itself can. What that answers is the
  *popup* half; `Host.overlay` is still the door a `toast` raised from a handler
  deep in the tree would want, and nothing wraps it in the
  `Overlay.of(context)`-shaped call that would put a toast up from there without
  the application's help. Smaller than it was, and the same shape. —
  [ADR-0100](adr/0100-a-window-has-a-layer-above-its-application.md),
  [ADR-0140](adr/0140-a-widget-may-reach-its-window.md)
- **Overlays do not animate in or out, and `stack` is still owed.** §1.7's overlay curve
  wants a toast to arrive rather than appear, which is a transition on the widget and
  not on the layer. And `docs/core-widgets.md` §1's `stack` — "z-order layering;
  children positioned by alignment or absolute insets" — is unbuilt; it is the *layout*
  widget where this is a *window* facility, and neither builds the other. —
  [ADR-0100](adr/0100-a-window-has-a-layer-above-its-application.md)
- **Nothing reports a dropped frame.** The ring behind `hud` records frames that were
  painted, so a frame the platform refused *after* it was painted is in the mean and a
  frame the loop never reached is not. "3 late" needs the pacer's view as well as the
  painter's, and the pacer belongs to the `sdl3` backend. —
  [ADR-0101](adr/0101-a-diagnostic-must-not-be-the-thing-it-measures.md)
- **The overlay enter/exit lifecycle is a specification without a subject, and the
  imperative `AnimationController` has now lost all three of its own.**
  `opening → open → closing → removed` applies to menus, popovers, tooltips, dialogs
  and toasts, which now exist — so this is a survey of five built widgets that each
  arrive and depart their own way, rather than a mechanism nobody could write. The
  controller was to drive indeterminate progress, the spinner and toast reflow. The
  first two ship as **functions of the frame clock with no state at all**, because a
  loop that never ends has nothing to remember and a controller would be a per-element
  copy of the time that puts two spinners permanently out of phase. **The third ships
  without one too**: `Phase` was already the start and the end, and the interruption —
  a second toast going while the first reflow is still running — turned out to be three
  lines of arithmetic on the distance that was left. What is left for the controller is
  the overlay sequence alone, which is now the whole of its case. —
  [ADR-0178](adr/0178-a-stack-closes-its-own-hole.md),
  [ADR-0081](adr/0081-a-perpetual-loop-has-no-state.md),
  [ADR-0067](adr/0067-motion-is-an-overlay-on-a-frame-clock.md)

## Input, focus and the pointer

- **"Pixel-precise wheel deltas" is not reachable through SDL.** §7.1 asked for them
  with a line-based fallback; SDL reports only detents, as floats. Wayland and macOS
  both have a pixel axis underneath and SDL does not surface it. What ships is lines
  with the touchpad's fractions preserved, which is honest but is not what the
  architecture document originally promised — reaching the real thing means going around
  SDL to the platform. — [ADR-0056](adr/0056-the-wheel-is-lines-and-the-sign-is-ours.md)
- **A knob inside a scroll view is still untested, though both now exist.** The
  bubble path is built and `scroll` chains at its edge, so the two cases the
  entry below has been waiting for — a knob turning without scrolling the list,
  and a knob at its maximum letting the scroll through — are now writable. The
  second one will fail: `Knob.wheel` consumes unconditionally. —
  [ADR-0089](adr/0089-a-knobs-gesture-is-a-rate.md),
  [ADR-0116](adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)
- **`Kind.WHEEL` had exactly one consumer for a long time, and it showed.**
  The wheel route has been live and covered since ADR-0061 — a fabricated
  `SDL_MouseWheelEvent` pushed onto SDL's own queue, through the real `translate` and
  the real sink — and until `knob` nothing in the toolkit *handled* one. What that means
  is that the whole bubble path for a wheel is still unexercised: a knob inside a scroll
  view should turn without scrolling the list, and a knob at its maximum should let the
  scroll through, and neither can be tested until `scroll` exists in M3. `Knob.wheel`
  consumes unconditionally, which is the safe half of that pair and the wrong half if
  the other one turns out to matter. — [ADR-0089](adr/0089-a-knobs-gesture-is-a-rate.md)
- **Every pointer event now costs an `SDL_GetModState`.** Polled per event rather than
  carried on it, because SDL's mouse events have no `mod` field. On a 120 Hz trackpad
  that is a few thousand calls a second into a statically linked function that reads a
  global. Not measured — named here so it can be if a profile ever points at it. —
  [ADR-0089](adr/0089-a-knobs-gesture-is-a-rate.md)
- **Nothing recomputes the cursor when the tree changes under a still pointer.** A
  widget that becomes disabled without the pointer moving keeps the shape it had. The
  fix is re-running `cursorAt` after each paint against the last known position; it is
  worth doing when something can actually change that way. —
  [ADR-0057](adr/0057-the-cursor-rides-on-the-painted-box.md)

## The catalog: specified and unbuilt

### `text-input`, and what §4 still owes

- **IME preedit is not drawn, and committed text already works.** The platform
  hands over *finished* characters and a field takes them like any others, so an
  IME is usable today; what is missing is the underlined in-progress string,
  which needs a second text the field draws and does not hold, and
  `SDL_SetTextInputArea` so the candidate window lands under the caret rather
  than in the corner of the screen. M5, as ARCHITECTURE §17 says. —
  [ADR-0167](adr/0167-a-field-owns-its-caret-and-the-model-is-told.md)
- **A field refuses right-to-left text outright.** `Paragraph.of` throws on it
  ([ADR-0036]) and nothing catches that, so a field a user pastes Arabic into
  takes the window down. The fix is `java.text.Bidi` run splitting, which is the
  same missing work the paragraph documents — but the *field* needs a decision
  the paragraph does not: refusing the paste is not acceptable and neither is
  crashing, so the interim behaviour has to be chosen. —
  [ADR-0167](adr/0167-a-field-owns-its-caret-and-the-model-is-told.md)
- **An absolutely positioned child is placed against the border box, and the
  clip is the padding box.** `text-input` allows for it by adding its own padding
  to every child's `left`, which works and is a workaround: the next widget that
  places a child absolutely inside a padded box will hit the same thing and will
  not know to. Whether Yoga or the painter is the one disagreeing with CSS has
  not been established. —
  [ADR-0167](adr/0167-a-field-owns-its-caret-and-the-model-is-told.md)
- **A field's scroll offset uses the previous frame's width.** ADR-0116 already
  decided that is what a viewport does, and it is wrong for one frame after a
  resize — invisible, because a resize is followed immediately by another frame.
  Worth writing down because it is the second widget to need the measurement
  `render` cannot have, and a third would be an argument for handing the width to
  `render` rather than to `Measured`. —
  [ADR-0167](adr/0167-a-field-owns-its-caret-and-the-model-is-told.md)
- **Nothing re-places the caret when the font changes under it.** A restyle that
  changes `font-size` reshapes the paragraph and the caret follows, because both
  are computed in the same `render`. A *density* change does the same. Neither is
  broken; what is untested is a font-family fallback swapping mid-edit, which no
  test can currently provoke. —
  [ADR-0167](adr/0167-a-field-owns-its-caret-and-the-model-is-told.md)
- ~~**Markup cannot hand a controller to a widget.**~~ **It can, through a
  fourth registry — and the first attempt was refused by the codebase itself.**
  This entry guessed the answer was "a registry beside `actions` and `bindings`",
  and it was; what it did not guess was that the *binding* registry would settle
  the question. A `@Bind` field holding a controller is refused with "a value that
  cannot change is not something to subscribe to", which is exactly what a
  controller is. `Named` is the registry for objects that are neither methods,
  resources, nor values that change. **`scroll` still has the gap** — a
  `ScrollController` could be named the same way and nothing has done it. —
  [ADR-0170](adr/0170-a-document-names-an-object-and-a-label-hands-focus-down.md)
- ~~**Nothing can ask for focus, so `field` has no click-to-focus.**~~ **A
  container can hand focus down now.** `Handles.delegatesFocus()` turns the
  router's walk round: a press that finds no focusable ancestor takes the first
  focusable *descendant* of a container that claims one. It has **one consumer**,
  which is one fewer than a mechanism should have — `group-box` and `card` are
  candidates and neither has asked. —
  [ADR-0170](adr/0170-a-document-names-an-object-and-a-label-hands-focus-down.md)
- ~~**`Host.focus` still does not exist.**~~ **It does**, and `dialog` is what
  needed it: `host.focus(id, fromKeyboard)`, by **id** for `Host.anchor`'s reason
  — a widget has no element and never will. The rule that makes it useful was not
  the obvious one: a node that cannot take focus resolves to **the first
  focusable thing inside it**, so "focus this dialog" and "focus this form" mean
  what a caller intends. It is refused for anything outside an open modal. **A
  form jumping to its first error is now two lines an application writes**, and
  nothing in the toolkit writes them. —
  [ADR-0176](adr/0176-a-dialog-is-a-widget-and-showing-one-is-not.md),
  [ADR-0170](adr/0170-a-document-names-an-object-and-a-label-hands-focus-down.md)
- ~~**Nothing restores focus when a modal closes.**~~ **It does, and the entry
  understated the problem.** "The keyboard lands nowhere in particular" was the
  visible half; `Element.unmount` tells the element tree and nothing else, so the
  router went on **holding** the element that had left it — an unmounted node
  receiving key events and keeping its dead subtree reachable. So the fix is two
  rules: the router never holds an element that is not in the tree (right for a
  switched tab and a shortened list as much as for a dialog), and if there is
  somewhere to put the keyboard back, it goes there. The remembered element is
  indeed the first state the trap has held, kept to one slot, written at exactly
  one moment, and allowed to go stale on purpose. —
  [ADR-0180](adr/0180-the-keyboard-goes-back-where-it-was.md)
- ~~**`min-width` and `max-width` are not in the CSS subset**~~ **All four are,
  and `dialog` has the two numbers §2 asks it for.** One value rather than four
  components — the four are only meaningful together, and they are the same
  question asked four ways, so a caller that handled three would have a bug
  nobody would find. The trick for "80% of the *window*" was the scrim: a
  percentage resolves against the containing block, so the scrim's padding across
  had to go or the maximum would have been 80% of the window less 48px — measured
  at 330 in a window where §2 permits 339. **The four consumers this entry named
  are all resolved, and only two of them by being built.** `tooltip` has a
  `max-width` of 320 — a judgement rather than a specified number, because §2's
  metrics row gives it no width at all, and 320 so a label cannot reach a
  `dialog`'s minimum. `text-area`'s max rows shipped with the widget
  ([ADR-0171](adr/0171-a-column-is-an-x-and-a-width-arrives-late.md)). `toast`
  **stays a width**: giving every toast the same width is what makes a stack of
  three read as a stack, and a maximum would size each one to its own string,
  which is the ragged pile. And `popover`'s `minimumWidth` is not a `min-width`
  consumer at all — it is "at least as wide as the control this dropped from"
  ([ADR-0145](adr/0145-a-dropdown-is-as-wide-as-what-it-drops-from.md)), a
  runtime measurement of a *different node*, which no stylesheet can state. —
  [ADR-0181](adr/0181-a-box-may-say-how-small-and-how-large.md)
- **`isModal` has one consumer**, which is one fewer than a mechanism should
  have. A `wizard` step and a `sheet` are the plausible seconds; it is tested in
  `:core` against bare widgets rather than through `dialog`, so the second one
  finds a mechanism rather than a dialog-shaped hole. —
  [ADR-0176](adr/0176-a-dialog-is-a-widget-and-showing-one-is-not.md)
- ~~**A `field`'s error summary is a list and not a widget.**~~ **`message` is
  built and `Message.summary(errors)` is the summary** — one `danger` banner with
  a line per failure, and **empty** when nothing is wrong, because a summary of no
  errors is not an empty banner. It is a factory rather than a child `form` adds,
  for two reasons that were not obvious until the widget existed: a form does not
  know where its summary belongs (above the fields is the convention, below is
  what a long form wants, a dialog's header is what a dialog wants), and a form
  that drew one would have to rebuild whenever any field's message changed —
  which is a notification from `Validated` to `FormAccess` that nothing else
  needs. **What is still open is a `form summary=#true`** that does exactly that,
  and it is waiting on that notification rather than on the banner. —
  [ADR-0175](adr/0175-a-banner-says-its-kind-twice.md),
  [ADR-0169](adr/0169-a-field-is-silent-until-you-leave-it.md)
- **A `Validator` is over a `String`, and `date-picker` will want otherwise.**
  What a user typed is text until something parses it, which is right for
  `text-input` and stops being right for a control whose value is a `LocalDate`.
  That is a second seam — a field that validates a *parsed* value — rather than a
  change to this one, and it is the picker's to open. —
  [ADR-0169](adr/0169-a-field-is-silent-until-you-leave-it.md)
- **A `text-area` has no visible scrollbar.** §4 asks for "scrollbar beyond" the
  maximum rows; it scrolls with the wheel and to keep the caret in view, and
  draws no bar. `scroll`'s bars belong to a *viewport* rather than to a control,
  so the choice is a `scroll` around the text — which would fight the auto-grow,
  since both want to decide the height — or a second bar implementation. Neither
  is obviously right. —
  [ADR-0171](adr/0171-a-column-is-an-x-and-a-width-arrives-late.md)
- ~~**A golden of a `text-area` is a golden of its first frame.**~~ **Both halves
  are fixed now.** The first was the gallery rendering twice and asserting on the
  second, 200ms in, which §7's `message` forced. The second — feeding the
  hit-test regions back *between* those frames — stayed open because nothing
  needed it badly enough, and `masonry` did: a layout that reads last frame
  cannot be photographed at all without it
  ([ADR-0196](adr/0196-a-masonry-is-a-layout-that-reads-last-frame.md)).
  `Measured` is delivered by the **router**, from the rectangles a laid-out frame
  produced, so a harness that only rendered gave every self-measuring widget a
  first-frame answer for ever. The harness runs render → lay out → hand the
  router the regions, twice, which is what a window does — and the Forms image is
  now the one the running application shows, with its `text-area` wrapped at the
  width it actually has. —
  [ADR-0175](adr/0175-a-banner-says-its-kind-twice.md),
  [ADR-0171](adr/0171-a-column-is-an-x-and-a-width-arrives-late.md)
- **A guard at the top of `onPointer` is a guard on every pointer kind, and the
  kinds do not carry the same fields.** `text-input` tested
  `button() == PRIMARY` there and silently lost every drag, because
  `PointerRouter.pointerMoved` builds its event with a null button — a motion is
  not a button event (ADR-0168). `Slider` asks per kind and reads as a style
  choice until this happens. Nothing warns; a `PointerEvent` accessor that is
  meaningless for the kind in hand answers with a default rather than refusing,
  which is right for `dragX`'s `NaN` and quietly wrong for a null `button`. —
  [ADR-0168](adr/0168-a-field-is-a-well-and-a-drag-is-a-selection.md)
- **`--gb-surface-2` has now been mistaken for an elevation three times** — by
  `card` (ADR-0166), by `text-input` and by `select` (ADR-0168). It means "the
  second surface" and promises no direction, and each consumer that assumed
  otherwise was wrong on one theme only. The two replacements,
  `--gb-surface-raised` and `--gb-surface-sunken`, say which way they go; what is
  unresolved is whether `--gb-surface-2` should keep existing at all, and that
  needs a look at what still reads it. —
  [ADR-0168](adr/0168-a-field-is-a-well-and-a-drag-is-a-selection.md)
- **`--gb-caret-width` is not a token and the caret is one logical pixel.** The
  width is set in the same call that sets the caret's position, so a stylesheet
  that disagreed would move it rather than resize it. A theme that wants a fat
  caret is a design-system decision and a token, which is Principle 3's order. —
  [ADR-0167](adr/0167-a-field-owns-its-caret-and-the-model-is-told.md)


- **There is no third text rank, and one was invented and taken back out.** A
  tour's step counter wanted something quieter than `--gb-text-muted`;
  `--gb-text-subtle` was added, resolved to `nord3`, and produced a counter
  nobody could read on `nord1` — §1.2's contrast floor applies to metadata as
  much as to prose, and the Nord palette has nothing between muted and the border
  colour. The size carries the demotion instead. A real third rank would need a
  colour the palette does not contain. —
  [ADR-0121](adr/0121-a-tour-is-a-veil-and-a-sequence.md)
- **Nothing warns that a `var()` resolved to nothing — it logs, per node, per
  frame.** One missing token is a stream rather than a message, which is how two
  of them survived long enough to reach a user. `TokenClosureTest` and
  `ShowcaseTokensTest` now fail the build instead, so the log is no longer the
  only line of defence; the log itself is still noise. Saying it once per property
  per stylesheet would make it a diagnostic. —
  [ADR-0121](adr/0121-a-tour-is-a-veil-and-a-sequence.md)
- **`flex-grow` means nothing inside a `scroll`, and nothing says so.** A scroll
  view's content column is as tall as its content by construction, so a child
  asking to fill the remaining height gets none — correct, and completely silent.
  The showcase had it on five screens where it did nothing and on one where it
  was load-bearing, which is exactly how long it takes for a dead declaration to
  look like a live one. The growth belongs on the `scroll` box; a diagnostic
  would have to know that a `grow` resolved against an unbounded main axis, which
  Yoga knows and does not report. —
  [ADR-0116](adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)

- **A `tour` cannot find the viewport its target is in.** §5 asks it to scroll a
  target into view, and `Stop` takes a `ScrollController` the application
  supplies. Discovering it means walking from an element to its nearest scrolling
  ancestor, which is a `:core`-to-`:widgets` dependency the toolkit does not have.
  ADR-0120 avoided the same wall by turning the question around; here there is
  nothing to turn around, because the tour is not the thing being revealed. —
  [ADR-0121](adr/0121-a-tour-is-a-veil-and-a-sequence.md)
- **A tour card's height is estimated, not measured.** It decides whether it fits
  below its target from a constant. Measuring needs the measure-then-place
  machinery ADR-0104 built, which works on *windows* rather than on boxes. Being
  wrong puts a card above its target when it would have fitted below. —
  [ADR-0121](adr/0121-a-tour-is-a-veil-and-a-sequence.md)
- **A tour has no arrival or exit.** §1.7's overlay curve wants one to arrive
  rather than appear, and stops change instantly — §5's row asks for the veil
  cut-out to `translate` and resize between stops. That is `TabPhase` again: the
  enter/exit lifecycle built for one widget, wanted by a third. —
  [ADR-0121](adr/0121-a-tour-is-a-veil-and-a-sequence.md),
  [ADR-0109](adr/0109-a-tab-arrives-and-departs-on-the-frame-clock.md)

- **The gallery goldens cannot see typography at all.** `GalleryGoldenTest` builds
  its renderer with the single-font constructor — which ignores `font-family`,
  `font-size` and `font-weight` by design, so that a golden image is not a test of
  whichever Inter is on the machine — so every screenshot draws prose, headings
  and button labels at one size. A screen with no typographic hierarchy looks
  exactly like a screen with one, which is how a screen title and the paragraph
  under it stayed the same 13px with nothing catching it. `ShowcaseTypographyTest`
  asserts sizes through the cascade instead; what is still missing is any image
  that would show a *layout* wrong because of a font size — text that clips at
  150% scale is §1.4's explicit gallery-enforced requirement and nothing enforces
  it. — [ADR-0118](adr/0118-a-popup-that-does-not-fit-scrolls.md)

- **The "always show scroll bars" reserved gutter is not built.** §2.4 wants the
  overlay bar to swap for a classic 12px gutter — "layout, not overlay" — as an
  app or user setting, and §13 lists it among the accessibility switches. It is a
  second drawing rather than a flag on the first, and it waits on a settings
  mechanism that does not exist for reduced motion or density either. The overlay
  bar itself is built. —
  [ADR-0117](adr/0117-a-widget-may-be-told-what-it-measured.md)
- **A scrollbar's thumb stops being proportional on a very long document.** It is
  floored at 24px, so past about four screens the thumb no longer says how much
  is visible — only that there is a lot. The trade every scrollbar makes, named
  here because it is a place the widget knowingly stops telling the truth. —
  [ADR-0117](adr/0117-a-widget-may-be-told-what-it-measured.md)
- **`Measured` has a consumer whose reason is a *sibling's* geometry**, which is new:
  a toast stack banks how tall each toast came out so that it can move the survivors
  by the height of the hole when one goes ([ADR-0178](adr/0178-a-stack-closes-its-own-hole.md)).
  Every other consumer reads its own box. It obeys the third rule by construction for
  the same reason the scrollbar does — a reflow is a `transform`, so the box it moves
  is laid out where it always was.
- **`Measured` is a door every widget can now open and almost none should.** A
  widget that sizes itself from last frame's measurement lags its own content,
  and one that does so in a way that changes the measurement never settles.
  Nothing enforces the rule that keeps it safe — read geometry to interpret an
  input or to draw something that cannot affect layout, never to decide a size —
  and the scroll view obeys it by construction rather than by check. —
  [ADR-0117](adr/0117-a-widget-may-be-told-what-it-measured.md)
- **A reveal moves both axes at once.** A wide table asked to show a cell scrolls
  the minimum on each axis independently, which is right and occasionally moves a
  view further than a person would have. Nobody has asked for one axis to take
  priority. —
  [ADR-0120](adr/0120-a-widget-scrolls-itself-into-view.md)
- **The "always show scroll bars" gutter has nothing to switch it.** §2.4 wants a
  reserved 12px gutter as an app or user setting, and §13 lists it among the
  accessibility switches. There is no settings mechanism at all — the same
  absence as reduced motion and density, both of which an application sets
  directly — so this waits on scrollbars existing rather than on the setting. —
  [ADR-0116](adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)
- **Nested same-axis scrollers are banned in the canon and nothing enforces it.**
  §2.4 says so outright. Chaining means a nested pair behaves reasonably rather
  than badly, so the ban costs nothing today; what is missing is the diagnostic
  that would tell an author they wrote something the design system rules out. —
  [ADR-0116](adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)
- **A widget cannot read a resolved custom property, so `scroll`'s line height is
  a constant.** §3 says metrics ship as component-token defaults an application
  may override; `StyleResolver` computes custom properties for `var()`
  substitution and `ComputedStyle` does not carry them, so a widget has no way to
  ask. `ScrollViewport.LINE` is 20 logical pixels and a `--gb-scroll-line` was
  deliberately *not* shipped, because a token no widget can read is a number an
  author sets and nothing honours. This is the same door
  `--gb-list-row-height` is waiting behind from the other side, and one change
  opens both. —
  [ADR-0116](adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md),
  [ADR-0080](adr/0080-a-value-is-measured-along-a-part.md)
- **A pinned `affix` is not pushed out by the next one.** A sticky header
  conventionally gives way when the following section's header reaches it; this
  one stays pinned until its own subtree has scrolled away entirely, so two
  headers overlap for the length of the shorter section. Doing better needs an
  affix to know about its sibling, which is a relationship nothing in the widget
  tree expresses. —
  [ADR-0119](adr/0119-a-widget-may-be-told-where-it-is.md)
- **An `affix` pins on one axis.** All four `edge=` values work and no affix can
  be pinned to two at once — a header that is both sticky at the top and held
  against the left of a horizontally scrolling table is the case, and it needs
  two shifts and a rule about which wins. Nobody has asked. —
  [ADR-0119](adr/0119-a-widget-may-be-told-where-it-is.md)

- **A tab strip scrolls, and has no chevrons at either end.** The headers are in
  a horizontal viewport now, so a strip wider than its window can be reached —
  by wheel or by dragging its thumb. A tab bar conventionally also has an arrow
  at each end that pages the strip, and that is a different affordance from the
  viewport: it needs to know it is at an edge, which is `scrollIntoView`'s
  missing question again. —
  [ADR-0118](adr/0118-a-popup-that-does-not-fit-scrolls.md)
- **A revealed row lands rather than glides.** §3.1 gives `scroll`
  "`scrollIntoView` / programmatic: overlay duration", and a reveal jumps: the
  offset is state and nothing interpolates it. It is a transition on a value the
  cascade cannot see — the same shape `TabPhase` solved for one widget, and the
  second consumer that would justify promoting it. —
  [ADR-0120](adr/0120-a-widget-scrolls-itself-into-view.md)
- **A tab's content is rebuilt when it is selected again.** That is the cost of
  §5's "lazy content instantiation" and is right — but it means a scroll position,
  a caret or a half-typed form in a background tab is gone, and the toolkit offers
  nowhere to put it except the application's model. `collapse` will have the same
  trade. —
  [ADR-0107](adr/0107-a-tab-strip-is-a-model-a-header-and-a-panel.md)
- **Nothing reorders tabs**, and a reorder would need a different animation from
  an arrival: a tab that moves has two positions and nothing to interpolate between
  them, which is ADR-0097's missing geometry again. §5 does not ask for
  drag-to-reorder; the model shape would take it without a change, since the strip
  draws the list it is given. —
  [ADR-0107](adr/0107-a-tab-strip-is-a-model-a-header-and-a-panel.md),
  [ADR-0109](adr/0109-a-tab-arrives-and-departs-on-the-frame-clock.md)
- **The enter/exit lifecycle is a tab's own, not the toolkit's.** `TabPhase` is what
  §1.7's "overlay enter/exit lifecycle" asks for, built for one widget: `toast`,
  `dialog` and `popover` all want the same thing, and promoting it should wait for
  the second consumer rather than be guessed at from the first. —
  [ADR-0109](adr/0109-a-tab-arrives-and-departs-on-the-frame-clock.md)
- **`margin` is not in §8's subset**, which `tab-new` found after `border-bottom`
  and `currentColor`. Three properties a widget reached for and did not find, all
  silently ignored — the subset is right to be small, and nothing warns when a
  declaration is dropped. —
  [ADR-0109](adr/0109-a-tab-arrives-and-departs-on-the-frame-clock.md)
- **The catalog's specified surface roughly tripled, and none of it is built.**
  `docs/core-widgets.md` gained twenty-one widgets and four options in one pass —
  `link`, `affix`, `segmented`, `date-picker`, `time-picker`, `color-picker`,
  `code-input`, autocomplete on both `text-input` and `select`, tree-select, `collapse`,
  `carousel`, `statistic`, `skeleton`, `breadcrumbs`, `steps`, `wizard`, `message`,
  `tour`, `tree`, `calendar`, `timeline`, and `button`'s `outlined` / `square` /
  `circle` / `float` options — each with a `design-system.md` §3 metrics row and, where
  it moves, a §3.1 row. §5 requires a spec **and** a metrics row **and** gallery
  coverage before code, in that order: these have passed two gates of three, and the
  third is what "built" means. **One of them is built now** — `segmented`, which was in
  this list and is out of it — and the way it went is the argument for writing them down
  first, read from the other end: two of its five specified metrics and both of its
  specified transitions turned out to be undrawable in §8's subset, and that was found
  by implementing it rather than by writing it. The other twenty are still only written
  down. The point of writing them down first is that the arguments are cheap now and
  expensive later — `message` against `toast`, `segmented` against `radio-group`,
  `code-input` against a styled `text-input` are all decisions that would otherwise be
  made by whoever happened to need one.
- ~~**`tree` is built in a first cut, and §3 asks for more.**~~ **All five of the
  leftovers are built, and one of them was never actually blocked.** The keyboard
  three — `Home`/`End`, `*`, type-to-select — needed rows the focused one cannot
  see, which is why they waited and why each is a callback the tree hands down
  ([ADR-0209](adr/0209-a-tree-finishes-its-keyboard.md)). The other two are
  `checkable=` and the selection models
  ([ADR-0210](adr/0210-a-tree-checks-and-selects-two-different-things.md)).
  **Multi-selection was recorded here as blocked on `list` and that reading was
  too strict**: `tree` defined the *node* model itself for the same reason, and
  wrote down that `list` will have to agree — the selection models are the shape
  every desktop list has, which makes it a small promise to make on `list`'s
  behalf. **`list` is built now and the promise was kept**: `Selection` moved to
  it and `tree` imports it, and nothing about the shape changed on the way
  ([ADR-0212](adr/0212-a-list-owns-the-models-a-tree-borrowed.md)). The checkbox needed a different question answered first, and it was in
  the design document rather than in the code: §3 spends the word `checkable`
  twice, on which rows are an *answer* and on whether rows carry a *box*. Both
  ship, under two names, and the disagreement is now in `ARCHITECTURE.md` §17.1.
  **§2's chevron `rotate` is two marks instead**, because §8's subset has no
  `transform` on a mark — the wall `select`'s chevron hit — so a closed row draws
  `>` and an open one `v`, and the cost is the animation. —
  [ADR-0210](adr/0210-a-tree-checks-and-selects-two-different-things.md),
  [ADR-0209](adr/0209-a-tree-finishes-its-keyboard.md),
  [ADR-0184](adr/0184-a-tree-is-a-list-that-remembers-what-is-open.md)
- ~~**`list` renders every row, and `table` is still waiting on the recycler
  neither has.**~~ **Both are built, and the promise held.** The item-factory
  did survive contact: `virtualized(rowHeight)` calls the same function with the
  same items and nothing about the API moved
  ([ADR-0213](adr/0213-a-virtual-list-is-two-spacers-and-a-window.md)). `table`
  turned out not to be waiting for the recycler at all but for `list` — a
  table's rows *are* a list's rows with more than one thing in them, so it
  composes one ([ADR-0214](adr/0214-a-table-is-a-list-with-columns.md)).
  **What is still out is rows of varying height**, which the arithmetic rules
  out rather than merely lacks: `index × height` is only a position if every row
  is that height, and the usual way round it — an estimate corrected as rows are
  measured — makes the scrollbar drift under the reader's thumb.
- **A `list` is Java, like `canvas` and like autocomplete.** An item-factory is a
  function and §8's documents have no way to write one, so no markup builds a
  list — the same wall, reached for the third time, and the third different
  widget that would want the answer `Named` gave for controllers
  ([ADR-0170](adr/0170-a-document-names-an-object-and-a-label-hands-focus-down.md)).
  A named item-factory is the shape; nothing has needed it badly enough to
  design what a document would say about a row. —
  [ADR-0212](adr/0212-a-list-owns-the-models-a-tree-borrowed.md)
- **A virtual list can have its focused row scrolled out of existence.** Wheel
  far from the focus ring and the focused row leaves the window, is unmounted,
  and the router drops it — which is ADR-0180's rule doing exactly what it should
  to an element that has left the tree. Arrow keys are unaffected, because the
  ring asks the viewport to follow it; only pointer-scrolling away and then
  pressing one loses the place. Every recycling list has this unless it pins the
  focused index, and pinning it would keep a row nobody is looking at built for
  ever. —
  [ADR-0213](adr/0213-a-virtual-list-is-two-spacers-and-a-window.md)
- **A row height that disagrees with the stylesheet is a silent layout error.**
  Nothing checks that the number handed to `virtualized` matches what the rows
  actually measure; the symptom is rows drifting out of step with the scrollbar,
  and it gets worse the further down the model you are. A `Measured` assertion on
  the first built row would catch it and is not built. This is the cost of the
  widget not being able to read `--gb-list-row-height`, which is the same door
  `scroll`'s line height is waiting behind. —
  [ADR-0213](adr/0213-a-virtual-list-is-two-spacers-and-a-window.md)
- **A `table` has no column resizing and no sticky header.** §3's metrics row
  allows for the first — "column resize: 1:1, like `split-pane`'s drag" — and
  nothing has asked; the divider would be a `split-pane` between the headers,
  which is a widget that already exists. The second is `affix`'s job and was left
  alone deliberately: a pinned affix is not pushed out by the next one, so two
  tables on one screen would overlap their headers, which is the entry above this
  one being a blocker for something. —
  [ADR-0214](adr/0214-a-table-is-a-list-with-columns.md)
- **A `table` focuses rows and not cells.** Right for §10's grid semantics and
  wrong for a spreadsheet, which is a different widget rather than an option on
  this one. Horizontal virtualization is absent for the same reason: it is a
  different arithmetic, and it is worth it past about fifty columns, which is
  past where a table is the right thing to be looking at. —
  [ADR-0214](adr/0214-a-table-is-a-list-with-columns.md)
- **A row's focus name still collides between two unnamed lists.** `host.focus`
  takes a name global to the window, and `list` scopes its rows by the list's own
  `id` — which settles it wherever an application named one, and leaves the case
  of two lists, both unnamed, holding an item with the same identity. `tree` has
  the unscoped version of the same thing. What would close it properly is a focus
  name that is relative to a subtree, which the router has no notion of. —
  [ADR-0212](adr/0212-a-list-owns-the-models-a-tree-borrowed.md)
- **`tree` moved from deferred to specified**, which changes what M5 owes. ARCHITECTURE
  §17 defers "tables/trees"; `table` still is, because it waits on virtualization, but
  `tree` reuses `list`'s model and item-factory and does not — and `select tree=#true`
  needs it, so the two arrived together.
- **`text` has no `style="body"` attribute.** `docs/core-widgets.md` §2 asks for one;
  what ships is `class="body"`, which is the same thing spelled the way CSS already
  spells it. Whether a second spelling earns its keep is a question for when `field` and
  `form` need labels. — [ADR-0066](adr/0066-a-weight-is-a-face-and-color-inherits.md)
- **A segmented control fills its parent when nothing gives it a width**, which is new
  and is a real loss of convenience: in a toolbar beside other widgets it takes the
  whole row until an author writes `width`. It buys the travelling indicator, and there
  is no third option under flexbox — content-sized cells cannot be travelled between,
  and a zero basis collapses the bar entirely.
- **A segment's label overflows its cell when it is longer than 1/n of the bar**,
  because the cells are equal and nothing clips. Reached by a different road than the
  indeterminate progress sweep documents, and stopped by the same missing feature.
- **An icon-only segment has no accessible name, and neither does an icon-only button.**
  §3 requires `name=` for both and the attribute does not exist anywhere; §13's
  semantics are M5's. `Option` refuses a segment with neither a label nor an icon, which
  is the half that can be enforced today, and the other half is a gap the whole catalog
  shares rather than one this control invented.
- **A `select`'s typeahead works closed and not open.** §3 asks for typeahead
  and a `TextEvent` goes to the *focused* node, which in an open list is an
  `option` — so the list has nothing to intercept it in. `Handles` has an
  `onKeyCapture` and no `onTextCapture`, and adding one is the whole fix; it was
  not added on spec, because a capture phase is a routing rule and inventing one
  for a single consumer is how a router grows two. —
  [ADR-0141](adr/0141-a-select-is-a-closed-control-and-a-list.md)
- **A `select`'s *field* is as wide as its current value.** The list is now at
  least as wide as the field (ADR-0145), which was the half that showed; the
  field itself still tracks its value, so it moves when the value does. No
  selector can measure a set of options — the same wall `segmented` hit, where
  the answer was that the control writes the width itself (ADR-0099) — and doing
  it here means measuring text outside a layout pass, which only `Paragraph` can
  do and only for a style that has been resolved. An application gives it a
  width, which is what a form does anyway. —
  [ADR-0141](adr/0141-a-select-is-a-closed-control-and-a-list.md),
  [ADR-0145](adr/0145-a-dropdown-is-as-wide-as-what-it-drops-from.md)
- ~~**A `select`'s list is clamped rather than scrolled when it is taller than the
  screen.**~~ **It scrolls.** The popup facility reports what it measured, so
  neither caller has to guess, and both give the same answer from the same helper
  — `Fitted`, which wraps content taller than the room in a viewport of the room's
  height and leaves everything else alone. —
  [ADR-0179](adr/0179-a-popup-says-what-it-measured.md),
  [ADR-0141](adr/0141-a-select-is-a-closed-control-and-a-list.md)
- ~~**`select multiple=` … is not built**~~ **It is.** The selection is a set,
  `change` is a **toggle** in that mode — the set is the application's, so asking
  for a value it already holds can only mean taking it out — and the list stays
  open while values are picked, which needed a popup whose content can change
  while it is open (`Popup.content`, new). §4's **free-text autocomplete** is
  built too: `TextInput.suggesting(options)` offers a `SelectList` under the
  field, the rows commit on `Enter` rather than following the focus, and the
  field's text is never rewritten without the user choosing.
  **`select autocomplete=#true` is built too** ([ADR-0183](adr/0183-a-combobox-is-a-select-you-can-type-in.md)):
  the closed control holds a real `text-input`, so the editing model, the undo
  history, the clipboard and the caret stay where their rules already are; the
  field stops being a Tab stop and delegates focus, so a combobox is one stop;
  `Esc` restores and a free-typed value is refused unless `free`, both off one
  nullable string of offered text that `TextInputState.follow` already knew how
  to honour. **`tree=#true` is built too** ([ADR-0184](adr/0184-a-tree-is-a-list-that-remembers-what-is-open.md)),
  and so is a first cut of `tree` itself, which `list` had to agree with since §3
  says the two share an item-factory — and does, now that `list` is built and the
  selection models have moved to it (ADR-0212). —
  [ADR-0182](adr/0182-a-select-may-hold-more-than-one.md),
  [ADR-0141](adr/0141-a-select-is-a-closed-control-and-a-list.md)
- **Autocomplete is Java-only, and `SelectList` is in the wrong package.** §4 says
  the application supplies the list, and it arrives by rebuilding the widget in
  answer to `change` — a channel a document does not have, so a document may write
  the field and it simply offers nothing under it; giving markup a named
  suggestion source is a decision about `Wiring`. And `SelectList` now has two
  callers, which is what moved `Option` into a package of its own; it stayed put
  because the CSS type it carries is `select-list`, so moving it renames a type in
  every stylesheet and every golden rather than editing one file. —
  [ADR-0182](adr/0182-a-select-may-hold-more-than-one.md)
- ~~**A `select` opened from the keyboard does not give focus back to the
  field.**~~ **The field never loses it.** Measured rather than reasoned about:
  the owner window's router is not touched by a popup opening or closing, so the
  field keeps both its focus and its ring for as long as the list is up. What
  remains is the platform-level question above, which is not a control's problem
  and not this control's in particular. —
  [ADR-0180](adr/0180-the-keyboard-goes-back-where-it-was.md),
  [ADR-0104](adr/0104-a-popup-is-measured-then-placed.md)
- **The circular drag is not built, and §3 offers it.** "Rotary: vertical-drag primary
  (**circular-drag optional**)". The vertical drag ships; the circular one needs an
  answer for the pointer crossing the 90° gap at the bottom, and every answer is either
  a jump or a wrap that depends on which way round the user went — which needs the
  accumulated angle, a *second* piece of gesture state, for a gesture that is nobody's
  first choice. — [ADR-0089](adr/0089-a-knobs-gesture-is-a-rate.md)
- **`--gb-list-row-height` has no consumer.** It ships with the density because the
  density a `list` will have to honour is decided there rather than in the widget, and
  an application building its own rows today has the token it would otherwise hard-code
  — the argument ADR-0037 made for `ParagraphCache`. `list` is M3. —
  [ADR-0074](adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md)
- **A slider maps the pointer over the track's full width**, so at the extremes the
  thumb's centre is up to 8px from the finger. Mapping over the *travel* needs the
  thumb's width, which is the stylesheet's and not the widget's. The mapping is
  monotonic and reaches both ends exactly; closing the gap means a widget being told a
  resolved metric, which is a bigger door than this is worth. The **tick marks do not
  have this problem**: their inset is half a thumb, written in the stylesheet beside the
  thumb's own width, so a mark and the thumb agree exactly while the finger is the thing
  that is up to 8px out. — [ADR-0080](adr/0080-a-value-is-measured-along-a-part.md),
  [ADR-0079](adr/0079-a-continuous-value-is-placed-by-ratio.md)
- **A slider's value label is left-aligned in its box**, because §8's subset has no
  `text-align` — `docs/ARCHITECTURE.md` §8.1 lists it among the properties `Box` cannot
  express, so it resolves into nothing and has no test that could mean anything. A
  right-aligned readout is what the column of numbers beside a row of faders wants. It
  arrives with whatever else needs `Box` to place text inside a box rather than at its
  origin. — [ADR-0080](adr/0080-a-value-is-measured-along-a-part.md)
- **A toggle's thumb does not follow the pointer during the drag — and the design system
  says it should not.** Left open as a defect after ADR-0075 and closed by reading
  rather than by building: §1.7's first principle names the controls that track 1:1 —
  "drags (**slider, knob, fader, splitter, scroll**) track the pointer 1:1" — and
  `toggle` is not among them, while §3.1's `toggle` row asks for the opposite, "thumb
  `translate` **base**". A switch here is a control with two positions that animates
  between them, and tracking the finger would be a third behaviour neither document asks
  for. It would also cost the mechanism the entry named: transient per-element state for
  a value that is neither the model's nor the stylesheet's, which nothing else in the
  catalog wants. Reopened only if the design system changes its mind, in writing. —
  [ADR-0075](adr/0075-a-gestures-origin-is-the-routers.md), `docs/design-system.md`
  §1.7, §3.1
- **The toggle does not shrink with a compact density**, read off §3 rather than
  decided: the rows with a compact value carry it in parentheses and the `toggle` row
  does not, so the pill stays 36×20 while the row around it takes `--gb-toggle-height`.
  Whether a 28-tall row holding a 20-tall pill is what §1.3 intends is a question for
  whoever writes the compact screenshots. —
  [ADR-0075](adr/0075-a-gestures-origin-is-the-routers.md),
  [ADR-0074](adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md)
- **Nothing detects the density the user wants**, exactly as with reduced motion: an
  application that knows sets it, and SDL exposes no query for either. Density is more
  often the application's own preference than an OS setting, so this one may never need
  detecting. —
  [ADR-0074](adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md),
  [ADR-0067](adr/0067-motion-is-an-overlay-on-a-frame-clock.md)
- **Nothing clips, so an indeterminate bar turns where it should run off the edge.**
  §3.1's sweep is conventionally drawn running off one end and back in at the other,
  which needs `overflow: hidden` — absent, along with the scroll view and the ellipsis
  that the same missing feature blocks. What ships reverses at the ends instead, which
  needs no clipping and has no wrap to hide. The reduced-motion **opacity pulse** §3.1
  asks for is missing for the same class of reason: a pulse is a loop between two
  opacities and §8 has no `@keyframes`, so a reduced-motion bar holds still rather than
  breathing. — [ADR-0081](adr/0081-a-perpetual-loop-has-no-state.md)
- **A `static` `@Action` is still unsupported, and now for a second reason.** The
  accessible path generates `target::method`, which does not compile for a static
  method, and the private path writes `findVirtual`. Nothing refuses one explicitly; it
  was broken before ADR-0098 and remains so, because no model has ever wanted one.

## The shell: the tray, and what it cannot say

§9's `tray-icon` ships ([ADR-0191](adr/0191-a-tray-is-a-menu-somebody-else-draws.md)).
What follows is what it does not do, and in three cases what no platform lets it
do — recorded here rather than left to be rediscovered by an author whose
description had no effect.

- **§9's "activate event" is not built, and SDL has no callback for it.** The
  sentence asks for icon, tooltip, menu **and an activate event**; SDL3's tray API
  registers a callback per *entry* and none for the icon itself. So a click on the
  icon opens the menu and nothing else can be attached to it. Doing this properly
  means going around SDL to three platform APIs, which is the move
  [ADR-0056](adr/0056-the-wheel-is-lines-and-the-sign-is-ours.md) declined when the
  wheel wanted it; the workable answer is a first row that means "open the window",
  which is what most tray applications ship anyway.
- **A checkbox's tick can end up disagreeing with the application.** The shell
  toggles it *before* the handler runs and an `Item`'s command takes no argument,
  so a handler that declines leaves the platform showing a tick nobody believes
  in. `SDL_SetTrayEntryChecked` is deliberately unbound — correcting one row would
  be the only mutation in an otherwise rebuilt-from-a-description menu — so the
  way to say no is to close the tray and show it again.
- **The menu cannot change while the icon is up.** `BackendTray` sets the icon and
  the tooltip and nothing else: its rows are platform objects the shell may have
  open, and replacing one would mean re-inserting entries underneath a user. A
  declarative caller closes and reopens, which is correct and is also a flicker
  in the notification area on some shells.
- **Nothing paints a tray icon for you, and nothing swaps it on a theme switch.**
  The mechanism is there — `TrayIcon.icon(pixels)` and `BackendTray.icon` — and
  §9's "theme-aware light/dark variants" is an application's two `PixelBuffer`s
  and a `restyle` handler it has to write. The toolkit ships no default mark of
  its own, so a tray with no icon is whatever the desktop draws for an
  application that supplied none.
- **An accelerator on a tray row is dropped**, with a warning. A shortcut is bound
  to a window and a tray has none. The same `Item` in a `menubar` still registers
  one, which makes this the first place in the catalog where one value means two
  different things depending on who draws it.
- **The deprecation warning on Linux is SDL's to fix.** Loading a tray prints
  `libayatana-appindicator is deprecated. Please use libayatana-appindicator-glib`,
  from the distribution's library as SDL opens it. SDL's `appindicator_names` list
  holds `libayatana-appindicator3.so.1` and `libappindicator3.so.1` and not the
  successor, so the only ways out are a patched SDL or a newer pinned one —
  neither worth doing for a line on stderr that no user of an application ever
  sees.
- **Windows and macOS are unverified.** The Linux path ran for real — in
  `SdlTrayTest` against this machine's session and in the showcase — and the other
  two are SDL's code, compiled and never looked at. A tray is the one widget CI
  cannot cover: there is no notification area on a runner and no golden image of a
  GTK popup.
- **A tray callback arriving off the UI thread is logged, not handled.** Every
  platform dispatches it from inside the pump the UI thread is already in, so the
  warning in `SdlTray.invoke` is the only evidence there would be if one ever does
  not. Doing better means posting to the loop, which is a second delivery path
  for one hypothetical.

## `canvas`, and what a document cannot say

- ~~**A gradient fill needs a symbol the export list does not have.**~~ **It has
  six now, and the entry was right about which commit came first.** The widening
  is the whole of the interesting part: `bl_gradient_init_as`, `_destroy` and
  `_add_stop_rgba32` build one, `bl_context_set_fill_style` and its `_rgba32`
  companion put it on the context and take it off, and
  `bl_context_fill_path_d` — the plain fill, with no `_rgba32` suffix — is the
  only styleless drawing call on the list and the only way a ramp reaches a path.
  **The OKLCH in the original wording turned out to be vacuous**: a fade between
  two alphas of one hue is the same curve in every perceptual space, and what
  makes it correct is premultiplied interpolation plus repeating the colour at
  the far stop, because `0x00000000` is transparent *black* and a green fading
  to it goes through grey. `goldberry-html` and `goldberry-vector` both start one
  commit further along. —
  [ADR-0207](adr/0207-a-fill-may-be-a-ramp.md)
- **Markup cannot name a painter.** A `canvas` node inflates to a styled, sized
  surface that draws nothing; the drawing is Java. `icon` solved the same problem
  with a registry the application owns
  ([ADR-0043](adr/0043-icons-are-stroked-paths.md)) and `action` with another, so
  the shape is known — what is not known is whether a painter is a *value* a
  registry holds or a *method* on a model, which is the same question `@Action`
  answered for commands and would have to answer again here. Nothing has needed
  it: every consumer so far is a chart widget written in Java.
- **A canvas has no intrinsic size**, so one in a `row` with nothing else to size
  it is zero wide and silently invisible. A measure function that guessed would be
  a number the toolkit invented and the application drew into; a diagnostic when a
  canvas is laid out to nothing would be noise in the collapsed-split-pane case,
  which is legitimate. Left as a documented sharp edge.
- **A painter is called on every paint of its box**, not only when it says
  something changed. That is what immediate-mode means and it is right for a
  chart whose data changed; it is wasteful for a canvas whose drawing is static
  and expensive. The seam for fixing it exists — a canvas that wants caching is a
  repaint boundary with a `Layer`
  ([ADR-0071](adr/0071-a-layer-is-a-subtrees-raster.md)) — and nothing has
  measured a case that needs it.

## Content modules

`docs/content-widgets.md` specifies eleven optional modules; **none of them
exists**, and none is scheduled while M3 still owes tray, client-side
decorations, charts and the rest of §4. The shape they share is
[ADR-0190](adr/0190-a-content-module-brings-its-own-natives.md) and the summary
is `docs/ARCHITECTURE.md` §11.1. What follows is what each is actually waiting
on, which in four cases is the same thing.

- **The export list has no paint surface wide enough for a native
  `document_container`.** `goldberry-html` puts litehtml's C++ container inside
  its own native library because FFM cannot implement a virtual class, and that
  container draws through `libgoldberry`'s exported C symbols. There are twenty
  `bl_context_*` entries and they are the ones the toolkit's own painter needs:
  no gradient, no rounded geometry, and no `bl_context_save` — the symbol file
  says why in its own comment, that there is only ever one clip depth here.
  `content-widgets.md` §1.5 promises linear and radial gradients and
  `border-radius`, and CSS state nests. So the first commit of `goldberry-html`
  is a widening of the toolkit's own native surface, reviewable on its own, and
  it is *shared* work: `goldberry-vector` and `goldberry-terminal` want the same
  surface. Statically linking a second Blend2D into the module is the way out
  that does not work — two runtimes in one process, and a `BLContext` handed
  across them is undefined behaviour. —
  [ADR-0190](adr/0190-a-content-module-brings-its-own-natives.md),
  [ADR-0007](adr/0007-jpms-modules-enforce-the-native-boundary.md)
- **No SDL audio or camera symbol is exported**, so `goldberry-camera`,
  `goldberry-mic` and the core `Sound` API that `content-widgets.md` §8 hands to
  SDL audio for UI effect sounds all begin at the same file. This is no longer a
  guess about what that costs: `tray-icon` began there too and paid eleven
  symbols, two binding classes and five probe constants for it
  ([ADR-0191](adr/0191-a-tray-is-a-menu-somebody-else-draws.md)). Of the 59
  `SDL_*` entries now on the list, every one is video, window, event, clipboard
  or tray. The modules' "zero new natives" claim is true of the binary and not of
  the surface.
- **The backend SPI has no PTY.** `goldberry-terminal` needs
  `Optional<Pty> openPty(cmd, env, size)` — `forkpty`/`openpty` on Linux and
  macOS, **ConPTY** on Windows — which is the same optional-capability shape as
  `gpuSurface()` and is the real platform work in that module. libvterm itself is
  a state machine and a cell grid, which is the part the text stack is already
  good at.
- **`goldberry-media` breaks the one-library assumption.** LGPL relinkability
  means libVLC stays a separate shared object with its plugin tree beside it, and
  every packaging rule in `:natives` — one static library, hidden visibility, one
  export list — assumes the opposite. It also needs a codec/patent note written
  before it gets code, which `content-widgets.md` §8 says and this list repeats
  because it is a gate rather than a caveat.
- **`goldberry-pdf` is the only module that vendors a prebuilt.** PDFium's own
  build wants gn/depot_tools, so `:natives-pdf` consumes pinned,
  checksum-verified community binaries — which is a different supply-chain
  posture from every other native in the toolkit, where the superbuild compiles
  from a pinned commit ([ADR-0030](adr/0030-pin-blend2d-and-asmjit-by-commit-sha.md)).
  Worth an ADR of its own before the first jar.
- **`goldberry-code` has a consumer before it has a widget.** md4c's code fences
  want a highlighter, so `goldberry-html` either depends on it optionally or
  renders fences plain. The optional-dependency mechanic — a module that improves
  when another is on the module path — does not exist in the toolkit yet, and
  `goldberry-vector` needs the same thing for `image/svg+xml`. One mechanism, two
  callers, and JPMS services are the obvious shape.
- **`goldberry-plot`'s colormaps are data with a provenance.** viridis-class
  tables are public domain, which is a claim the licence tooling has never had to
  check for something that is neither a font nor a library.
  `./gradlew checkLicenses` knows about artifacts.
- **Text selection in `html-view` is deferred**, and it is the same
  character-quad work as text-editing depth (`ARCHITECTURE.md` §17) and as
  `pdf-view`'s selection.
  Three widgets waiting on one mechanism is an argument for building it once, in
  core, rather than in whichever module lands first.
- **`goldberry-web` is parked, not deferred.** libservo is Rust-only against a
  deliberately unstable API, so the module would own a `cdylib` shim and its
  breakage. Revisit when libservo ships semver guarantees or Verso-style
  embedding stabilizes; CEF-OSR stays the documented escape hatch until then.

## Layout

- ~~**`split-pane` is not built.**~~ ~~**`carousel` is not built.**~~ **Both
  ship, and §5 is complete.** The divider turned out to want `knob`'s gesture
  anchor rather than `slider`'s position — the pointer is somewhere inside a
  six-point bar, and reading its position would snap the divider under the finger
  on every press — and the carousel's rotation is one one-shot timer rescheduled
  after each slide, so that a pause is a timer not scheduled rather than one
  suspended. What did **not** ship is one of the carousel's three brakes; see the
  entry below. —
  [ADR-0165](adr/0165-a-divider-translates-and-a-rotation-has-three-brakes.md)
- ~~**A `carousel` does not pause when focus lands inside a slide.**~~ **The
  third brake ships, and it cost one line because something else needed the same
  thing.** This entry guessed the price wrong in an instructive direction: it said
  closing the gap meant "`:focus-within` in the selector engine, the matcher and
  the router's focus bookkeeping". None of that was needed. A carousel does not
  want to *style* itself on focus-within, it wants to be **told** — and so does a
  `field`, which validates when the keyboard leaves it. So what shipped is
  `Handles.onFocusWithin`, a notification rather than a selector, reporting only
  the moves that cross a subtree's boundary. The selector-engine version is still
  unbuilt and now has no consumer asking for it. —
  [ADR-0169](adr/0169-a-field-is-silent-until-you-leave-it.md),
  [ADR-0165](adr/0165-a-divider-translates-and-a-rotation-has-three-brakes.md)
- **`statistic`'s sparkline waits on `canvas`.** §5 asks for an "optional
  `sparkline` from a `canvas`", and `canvas` is §12's and not in the catalog.
  Nothing in `statistic` is shaped around its absence: a sparkline is one more
  child at the end of the column. —
  [ADR-0164](adr/0164-elevation-is-an-edge-and-a-closed-section-is-absent.md)
- ~~**`collapse`'s `accordion=` is not built.**~~ **It ships, as a widget rather
  than as a flag on `column`.** The flag belongs on the container — "one open at a
  time" is a rule about siblings — but honouring it needs state, and statefulness
  is a property of the *type*: putting it on `column` would give every column in
  every document a `State` it never uses. `column accordion=#true` inflates to an
  `Accordion` that reports `column` as its own CSS type, so the document writes
  what §5 says and an ordinary column pays nothing. —
  [ADR-0166](adr/0166-a-raised-thing-is-told-apart-by-its-edge.md)

- **`flex-basis` is still the only layout property §8 names and nothing resolves.** It
  was implemented for `segmented` and taken back out rather than left as a property with
  no consumer — `flex-basis: 0` makes Yoga compute a track's content size as *zero*, so
  an unconstrained bar collapses. It is the last of §8's `flex-grow`/`shrink`/`basis`
  still unimplemented, and `:core`'s five primitives all still shrink — right for
  containers, and unexamined for `spacer`, which presumably wants to keep a fixed size.
  — [ADR-0076](adr/0076-a-glyph-does-not-negotiate.md),
  [ADR-0099](adr/0099-an-indicator-travels-on-a-grid.md)
- **Nothing has a minimum size, so overflow is silent.** `flex-shrink: 0` stops a
  control being squashed and does not stop it being *clipped*: a window narrower than
  its content now overflows rather than deforming, which is CSS's behaviour and is what
  a scroll view or an ellipsis is for. Neither exists yet. M3's problem, named here
  because ADR-0076 is what makes it visible. **`badge` is the second consumer, and it
  wants the other half**: §8's subset has no `min-width` at all, so a one-digit chip is
  a stadium rather than the circle a badge usually is. `badge-digits.png` is the record
  of it. — [ADR-0076](adr/0076-a-glyph-does-not-negotiate.md),
  [ADR-0087](adr/0087-a-semantic-fill-brings-its-own-foreground.md)

## Style, colour and motion

- **A dropped declaration is reported once, and `align-items: start` is why.** The
  Panels screen filled the console while it scrolled: `start` is CSS's alias for
  `flex-start` and Yoga has only the second, so the declaration was dropped —
  correctly — and reported *per element per style resolution*, which on a moving
  screen is sixty times a second. The typo is fixed and the report is now
  deduplicated by property and value, because a stylesheet is static and a value
  that is not one cannot become one on the next frame. **What is still true**: the
  toolkit accepts Yoga's spelling of these keywords and not CSS's aliases, so
  `start`, `end` and `space-between`-style names have exactly one correct form
  each and a document that uses the other gets a warning rather than a mapping.
  Whether to accept the aliases is open; accepting them means a second table to
  keep in step with Yoga's enum.


- **`align-self` is not in §8's subset**, which a tab strip's `+` found: a child
  shorter than its row sits at the top of it and there is no per-child way to say
  otherwise. It is the companion of `align-items`, which *is* in the subset, and
  Yoga's `setAlignSelf` is already bound — what it costs is a component in
  `ComputedStyle` and one in `Box`, both of which are records whose every wither
  would have to be revisited. —
  [ADR-0111](adr/0111-a-text-box-is-painted-inside-its-padding.md)
- **Nothing warns when a declaration is dropped for being unsupported.** A
  property the subset does not have is logged at DEBUG and ignored, so
  `border-bottom`, `currentColor`, `margin` and `max-width` were each written,
  silently discarded, and found by looking at a picture. A stylesheet is data and
  should not be fatal — but the four of them cost more to find than a warning
  would have cost to read. —
  [ADR-0109](adr/0109-a-tab-arrives-and-departs-on-the-frame-clock.md),
  [ADR-0111](adr/0111-a-text-box-is-painted-inside-its-padding.md)
- **A popup's transparent corners need a compositor**, and are unverified on
  Windows and macOS. Without one the flag is ignored and the corners are whatever
  the platform leaves there. The fallback that always works — filling the frame
  with the panel's own colour, for square corners — is kept in reserve. —
  [ADR-0111](adr/0111-a-text-box-is-painted-inside-its-padding.md)
- **`Styled.restyle` is an escape hatch, and the honest risk is what goes into it.**
  What a widget writes there is unthemeable and unoverridable — right for a number
  nobody else can compute, wrong for anything else. It has one caller in the toolkit and
  one rule ("only what a stylesheet could not have written"); a second caller that is
  *not* a count is the signal to look at it again.
- **Per-corner radii do not exist, and `segmented` is the second control that wanted
  one.** ARCHITECTURE §8 resolves "`border-radius` … (one radius, not per-side)"
  deliberately, and `button.square` was the first thing to ask — §3 gives it radius 0
  "where buttons butt against each other", which is the joined drawing seen from the
  other side. `segmented` is the second, and it went round the outside: the bar keeps
  the radius and the segment is inset. `SegmentedTest` pins both numbers so a third
  asking is a decision that gets revisited rather than one that quietly outlives its
  reason (ADR-0097).
- **A `tabs` indicator still cannot travel, though `segmented`'s does.** §3.1 gives the
  two the same effect and the same controller.
  [ADR-0099](adr/0099-an-indicator-travels-on-a-grid.md) got `segmented` moving by
  making its cells a **grid** — every segment exactly `1/n`, so the distance to segment
  *k* is `k` times the indicator's own width and needs no measurement — and that is
  exactly what `tabs` cannot do: its tabs are as wide as their labels. What it would
  need is *where the next item was laid out*, and geometry exists after a paint where
  only the router can see it (ADR-0080), so a widget that moved something by the width
  of its sibling would need a read-back path and would stop being a pure function of its
  model. `Host.anchor` is the first half of that path. —
  [ADR-0097](adr/0097-a-selection-that-travels-needs-a-geometry.md),
  [ADR-0099](adr/0099-an-indicator-travels-on-a-grid.md)
- **A segment's focus ring lands exactly on the bar's edge.** §2.2's ring is 2px at a
  2px offset and the bar's inset is 2, so the two coincide — legible in
  `segmented-focus.png`, and an accident of two numbers derived separately rather than a
  thing anyone chose. If either moves, look at the image.
- **Non-text contrast is not checked at all.** `ContrastTest` measures text against the
  fill under it. §1.2's other half — 3:1 for anything that is not text — reaches a
  checked checkbox's mark, a slider's thumb against its groove, a spinner's ring, a
  border against the surface it separates, and the focus ring against whatever is behind
  it. None of them is measured, and ADR-0088's argument that the accent ramp did not
  need to move rests on exactly that unenforced number. The arithmetic is already
  written; what is missing is deciding what counts as the "background" of a mark drawn
  *onto* its own box. —
  [ADR-0088](adr/0088-a-fill-that-carries-text-moves-away-from-it.md)
- **`button.ghost` has no contrast ratio, and is therefore not checked.** Its fill is
  `transparent` and its hover is a `#ffffff14` wash, so what a user reads depends on the
  surface underneath — there is no single pair to measure. It is left out of
  `ContrastTest` rather than measured against black, which is what ignoring alpha would
  silently do and would score it as passing. The same is true of `--gb-selection`. A
  backdrop-aware check would need the painted frame rather than the cascade, which is a
  different kind of test. —
  [ADR-0087](adr/0087-a-semantic-fill-brings-its-own-foreground.md)
- **Nothing validates an application's own theme.** §10 lets an application swap the
  alias tokens, and `ContrastTest` runs over the two themes the toolkit ships. A
  third-party theme that pairs `--gb-badge-warning-bg` with an unreadable
  `--gb-badge-warning-text` is a legibility bug the toolkit will not notice — the
  arithmetic is nine lines and is not exposed as anything an application can call. —
  [ADR-0087](adr/0087-a-semantic-fill-brings-its-own-foreground.md)
- **`em` and `rem` do not resolve against the node's own `font-size`.** They use
  `CssLength.Context`'s fixed numbers, so `font-size: 1.2em` means 1.2 × 16 and not 1.2
  × the parent's size. Nothing in the toolkit's own stylesheets uses `em`, so it has no
  effect today — but it is wrong, and the typography scale is what makes it reachable. —
  [ADR-0066](adr/0066-a-weight-is-a-face-and-color-inherits.md)
- **A bare `text` with no ancestor setting `color` renders black**, which is ADR-0066's
  deliberate `INITIAL` and a trap all the same: the showcase's new gain label was
  unreadable on the dark theme. A control gets away with saying nothing because
  `controls.css` sets `color` on `checkbox`, `radio`, `toggle` and `slider` themselves;
  a primitive does not. The showcase now sets `color: var(--gb-text)` on its root, which
  is what an application should do — but nothing warns one that has not. —
  [ADR-0066](adr/0066-a-weight-is-a-face-and-color-inherits.md)
- **Reduced motion is obeyed but not detected.** `renderer.reducedMotion(true)`
  collapses every transition; nothing reads the OS setting, because SDL exposes no query
  for it. An application that knows sets it. —
  [ADR-0067](adr/0067-motion-is-an-overlay-on-a-frame-clock.md)
- **A generated registry can fail at class-init time now, and only for private
  members.** A `VarHandle` lookup that cannot find its field throws
  `ExceptionInInitializerError` where a direct field reference would have thrown
  `NoSuchFieldError` at link time — the same class of failure with a different
  exception, and both are impossible within one compilation, which is how a registry and
  its model are always built. Recorded because it is the one thing ADR-0098 moved later
  rather than earlier.

## Rendering and performance

- **What is still asserted only at 1x, now that the goldens are not.** Every one of
  the 106 golden images is drawn again at 2x and 1.5x and checked for being the same
  picture, and `ClipTest`, `TransformPaintTest` and `IconPaintTest` do the same
  without a golden behind them — so the whole widget catalog, text included, is now
  covered against the logical-against-physical family ADR-0157 found. **Four classes
  of direct pixel assertion are not**, and two of them are deliberate:
  `BoxPainterTest` and `TextPaintTest` each already carry their own scale cases and
  would gain little; `DamageTest` is **excluded on purpose**, because a damage
  rectangle is in physical pixels by design and legitimately differs between scales,
  so an invariance check there would assert something false; and `ThreadedPaintTest`
  is about two worker counts agreeing, which is orthogonal. What no test at any scale
  covers is a **fractional scale other than 1.5** — 1.25 and 1.75 are ordinary
  Windows settings and neither is exercised. —
  [ADR-0162](adr/0162-a-golden-is-checked-at-every-scale.md),
  [ADR-0157](adr/0157-a-layer-is-blitted-into-its-own-size.md)
- **The scale-invariance thresholds are calibrated on one CPU.** The worst honest
  disagreement measured over the corpus is 0.332% of pixels against a 1.2% limit, and
  Blend2D JITs its antialiasing for the CPU it finds (ADR-0030) — so the margin on
  AVX-512, on Apple Silicon and under MSVC is answered by the next CI run rather than
  by argument, exactly as the goldens' own tolerance is.
  `-Dgoldberry.golden.scales.report=true` prints what every check measured, which is
  how a runner pressing against the limit would say so in numbers. —
  [ADR-0162](adr/0162-a-golden-is-checked-at-every-scale.md)

- **How damage is computed, and the bug a resize found in it.** Each render object
  remembers where it was, and a node that changed damages the union of where it **was**
  and where it **is** — both, because damaging only the new position leaves the old
  drawing on screen. It reads the node's *own* changed flag rather than its subtree's,
  or a parent whose child moved would report the whole window. **A resize broke it in
  the field**: a remembered rectangle belongs to the previous frame, so the union fits
  neither when a window is dragged a pixel narrower, and the backend refused the frame
  mid-drag. Damage is now clamped on the way out rather than only where each rectangle
  is computed — and the regression test resizes by **one pixel**, because that is what a
  drag produces and a test that jumped by fifty would have passed against a fix that
  only handled large changes. Every damage test had used a single frame size, which is
  the natural thing to write and the one case that cannot fail. —
  [ADR-0071](adr/0071-a-layer-is-a-subtrees-raster.md),
  [ADR-0072](adr/0072-a-partial-repaint-needs-a-promise.md)
- **The rounded corners and the transforms have only been rasterized on linux-x64.**
  Blend2D JITs its pipelines per CPU, so the four cubics and the eleventh golden's
  rotations and skews on AVX-512, on Apple Silicon's NEON path and under MSVC are
  answered by the next CI run rather than by argument — which is what the golden images'
  per-channel *and* area tolerance is for. The transform half also rests on `BLMatrix2D`
  being six consecutive doubles in the order `matrix(a, b, c, d, e, f)` writes them,
  which the layout probe now checks against the compiled library on every target because
  the operand crosses as `void*` and a reordered union would produce a skewed frame and
  `BL_SUCCESS`. — [ADR-0064](adr/0064-a-rounded-rectangle-is-four-cubics.md),
  [ADR-0068](adr/0068-the-transform-stack-is-java-side.md),
  [ADR-0050](adr/0050-golden-images-have-a-tolerance.md)
- **The units between the two text libraries are a convention, not a checked fact.**
  HarfBuzz reports positions in whatever scale its font was set to; Blend2D multiplies
  them by `size / units-per-em`. Both are right, and applying a size on both sides
  applies it twice — 128&times; for Inter at 16 points — which draws text off the edge
  of the window and returns `BL_SUCCESS`. The layout table cannot catch this: it is an
  agreement *between* two libraries, not a fact about either. What holds it is `Font`
  owning both objects and never scaling the shaper, plus a test that compares the inked
  span against the measured width. Anything that builds a `ShapedFont` and a `BlendFont`
  by hand can still get it wrong. —
  [ADR-0034](adr/0034-one-size-and-the-design-unit-crossing.md)
- **A line boundary keeps a kern it should drop.** Each line is a slice of the whole
  paragraph's single shaping, so the kern between the last character of one line and the
  first of the next is included where a per-line shaping would drop it. A fraction of a
  pixel at the end of a line, in exchange for wrapping that costs no shaping at all.
  Re-shaping only the final lines, and only for painting, is the fix if it ever shows. —
  [ADR-0036](adr/0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md)
- **`customPropertiesFor` still walks to the root**, re-running the whole cascade at
  every ancestor, so it is *O(depth × rules)* where it could be *O(rules)*. The style
  cache amortises it almost to nothing, but a first frame and every invalidated subtree
  still pay it. Worth doing when a deep tree makes a first frame visible. —
  [ADR-0070](adr/0070-the-cascade-resolves-invalidated-nodes.md)
- **Present costs 6.6 ms with no compositor to wait for.** The question ADR-0045 opened
  while closing another. [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)
  measured present at ~10 ms and concluded "most of it is waiting on the compositor
  rather than copying". Under SDL's `dummy` video driver — no compositor, no display, no
  surface to hand anyone — present still measures **6.6 ms**, essentially the same as
  under Wayland. Whatever that time is, the explanation on record is wrong, and present
  is the largest single term in a frame. —
  [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md),
  [ADR-0045](adr/0045-a-frame-is-not-a-benchmark-iteration.md)
- **AsmJit's W^X handling on Apple Silicon is now reachable.**
  [ADR-0002](adr/0002-cpu-rasterization-with-blend2d.md) flagged that Blend2D
  JIT-compiles its pipelines and that macOS needs `MAP_JIT` and
  `pthread_jit_write_protect_np`. Nothing triggered it until now, because nothing
  created a rendering context. The first frame the macOS build paints is the test. —
  [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)

## Platform, compositor and CI

- **Layout verification has not yet passed in CI.** The first run's verify jobs failed
  without running a test, and the fix — verify the downloaded artifact, and fail rather
  than skip when it is absent — has been tested locally against every path but has not
  itself been through CI. —
  [ADR-0016](adr/0016-verify-the-artifact-and-never-skip-the-check.md)
- **The Wayland preference is evidence from one compositor.** SDL chooses X11 on a
  Wayland session unless the compositor advertises `wp_fifo_manager_v1`, which GNOME's
  Mutter does not; Goldberry asks for `wayland,x11` instead, because XWayland resizes
  visibly worse. Confirmed on GNOME only — KDE, Sway and the rest are untried, and the
  driver is logged at start-up so a report can say which one it got. —
  [ADR-0027](adr/0027-prefer-wayland-fall-back-to-x11.md)
- **The macOS window opens, and the CI leg still would not have caught it.** `gradlew
  run` failed with "No available video device", which points at the superbuild and was
  not the superbuild: macOS drives AppKit from the process's first thread and the java
  launcher does not put `main` there. The showcase passes `-XstartOnFirstThread` on
  macOS and `Sdl3Backend` appends the explanation after SDL says no — as a diagnosis
  rather than a precondition, since a JVM embedded on the real main thread would lack
  the launcher's environment variable and would work anyway. The hole that hid it is
  still open: the macOS leg links the library and runs the `:natives` tests, and never
  opens a window. — [ADR-0039](adr/0039-macos-needs-the-first-thread.md)
- **"Starts in milliseconds" is still unproven.** The timeline exists and the first
  numbers are in ADR-0028 — `SDL_Init(VIDEO)` is ~99ms and dominates, while mapping
  `libgoldberry` is under 2ms — but they were measured under `gradle run`, which adds a
  launcher and its own JVM. The headline claim needs the example launched directly. —
  [ADR-0028](adr/0028-the-start-up-timeline.md)
- **The compositor still dies, and shutting down cleanly did not stop it — the core dump
  says whose bug it is.** The entry below concluded that exiting with a live Wayland
  surface was the trigger and that `Goldberry.shutdown()` was the fix. The showcase has
  called `shutdown()` ever since, and GNOME Shell crashed twice more on 2026-08-17.
  `/var/crash` had the core, and it names the frame: ```text wl_event_loop_dispatch
  libwayland-server → wl_client_destroy libwayland-server → <destroy listener>
  libmutter-14 → g_signal_handler_disconnect libgobject → g_type_check_instance ←
  SIGSEGV ``` Mutter, tearing down a departing client, disconnects a signal handler on a
  GObject that `g_type_check_instance` rejects — an instance already finalized. **That
  is unambiguously a compositor bug**: `wl_client_destroy` runs whenever *any* client
  goes away, for any reason, and surviving it is the one thing a compositor cannot be
  excused from. Our own process exits 0 with no JVM crash log, having destroyed its
  window and called `SDL_Quit` first. The nearest exported symbol below the faulting
  frame is `meta_xwayland_signal`, 2.2 KB back, so the crashing function is a static one
  in Mutter's Xwayland area — suggestive, not conclusive, and not enough to file
  upstream on its own. What is left for this repository is **not** a fix but a defence:
  nothing should be able to open a real surface by accident. See the entry below on the
  two unreliable ways to ask for a headless run. Reproducing this deliberately costs the
  developer their session, so it is not something to iterate on casually. gnome-shell
  46.0-0ubuntu6~24.04.14, Ubuntu 24.04, under VirtualBox/vmwgfx.
- **Both ways to run the showcase headlessly were broken, and one of them cost a desktop
  session.** `goldberry.backend.videoDriver` existed and was *not* in `:example`'s
  forwarded-property list, so `-Dgoldberry.backend.videoDriver=dummy` reached the Gradle
  daemon and stopped there — the exact failure the comment beside that list already
  described for `goldberry.log.level`. The obvious fallback, `SDL_VIDEODRIVER=dummy` in
  the environment, does not work either: a `JavaExec` fork inherits the **daemon's**
  environment rather than the one `gradlew` was invoked with, so it applies or does not
  depending on how the daemon happened to be started — which reads as flaky rather than
  as broken. A run intended to be headless therefore opened a real Wayland surface and
  took GNOME Shell down with it. The property is now forwarded, and `./gradlew run
  -Pgoldberry.backend.videoDriver=dummy` is the checked way to drive the showcase
  without a compositor.
- **The toolkit never shut SDL down, and a compositor died of it.**
  `Sdl3Backend.close()` destroys every window and calls `SDL_Quit`; nothing called it.
  `Goldberry.run()` returning does not shut the runtime down — its contract says so —
  and `Goldberry.stop()` ends the loop with the window still open, so the showcase
  exited with a live Wayland surface and let the socket close. GNOME 46's Mutter then
  crashed unwinding the connection, in `wl_client_destroy` → its destroy listener →
  `g_signal_handler_disconnect`, on a GObject already freed. **That is a compositor
  bug** — every killed process disconnects abruptly and a compositor has to survive it —
  but disconnecting properly is right regardless, and the showcase now calls
  `Goldberry.shutdown()`. Open: whether `run()` should shut down on return, which would
  change a documented contract. Seen once, on GNOME 46.0 under VirtualBox/vmwgfx, after
  SDL3 moved from `release-3.2.0` to `release-3.4.14` in the same session. —
  [ADR-0022](adr/0022-window-is-the-front-door.md)
- **What does the release container actually compile into its Wayland driver?** Two
  dependencies decide it and `linux.yml` installs neither. `egl` is one of the five
  specs in SDL's single `CheckWayland` `pkg_check_modules` — lose any one and the entire
  Wayland driver is dropped *silently*, and the container has no `mesa-libEGL-devel`.
  `libdecor-0` decides whether a Wayland window that does get built has a titlebar and a
  resize edge, and whether `libdecor-devel` even exists in AlmaLinux 8's repositories is
  unverified. The manylinux leg runs CMake directly with no JDK, so `checkToolchain`
  never gets to ask either question, and the drift guard deliberately holds that
  workflow only to the packages SDL refuses to configure without. Answering it means
  reading `SDL_VIDEO_DRIVER_WAYLAND` and `HAVE_LIBDECOR_H` out of a container build's
  `SDL_build_config.h` — not another look at the table. —
  [ADR-0082](adr/0082-a-preflight-check-that-cannot-fail-is-not-a-check.md),
  [ADR-0083](adr/0083-on-gnome-wayland-libdecor-is-not-a-fallback.md)
- **No CI leg exercises Wayland.** `example.yml` and `showcase.yml` both run under
  `xvfb-run`, which is X11, where the window manager decorates the window and libdecor
  is never reached — which is why two consecutive decoration bugs shipped without a
  single red tick. A Wayland leg needs a headless compositor in CI (`weston
  --backend=headless` or `sway --headless`), which is a job nobody has written yet. —
  [ADR-0084](adr/0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)
- ~~**`WaylandDecorationsTest` asserted `/proc` exists.**~~ **It asserts the
  platform's own half of the contract now.** One test read the real
  `/proc/thread-self` and asserted `Optional.of(false)` unconditionally — true on
  Linux and false everywhere else, in a suite all three OS legs run
  (`macos.yml` and `windows.yml` both run `:core:test` unfiltered). The fix is
  not a skip: where `/proc` can answer it is still the live check that the
  parsing works against a real symlink, and where it cannot the assertion is that
  the answer is **empty** — which is `onInitialThread`'s documented contract,
  "a machine that cannot say must produce silence rather than a guess", and the
  branch macOS and Windows actually take. Gating with `@EnabledOnOs(LINUX)` would
  have left two of the three platforms asserting nothing about the call that runs
  on them. —
  [ADR-0084](adr/0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)
- **Native decorations on Wayland need a launcher that embeds the VM.** The GTK plugin
  is the only thing that draws decorations matching the desktop, and its one requirement
  is `getpid() == gettid()`. The stock `java` launcher runs `main` on a thread it
  creates and so fails it; a launcher whose own `main` calls `JNI_CreateJavaVM` and then
  the Java `main` runs Java on the primordial thread, and the plugin loads there —
  demonstrated with a throwaway C launcher against the real showcase. `jpackage` does
  not help; it goes through the same `ContinueInNewThread`. Shipping one is a
  distribution change (a native binary per platform, VM argument handling, and a story
  for `./gradlew run` and `java -jar`), so it is recorded as the answer and not yet
  taken. Two things bound how much to invest in it: upstream is building an
  out-of-process GTK plugin (libdecor MR 176) that dissolves the thread restriction
  entirely when it ships, and the ecosystem's own answer on GNOME/Wayland is that every
  non-GTK toolkit — Qt, Firefox, Chromium — draws its own decorations in-process, which
  is the `SdlWindowFlag.BORDERLESS` design Goldberry has reserved but not built. —
  [ADR-0084](adr/0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)
- **A window on GNOME/Wayland needs two packages from two different phases.**
  `libdecor-0-dev` at build time, or SDL compiles no libdecor support at all
  ([ADR-0083](adr/0083-on-gnome-wayland-libdecor-is-not-a-fallback.md)), and
  `libdecor-0-plugin-1-cairo` at run time, because the GTK plugin that libdecor pulls in
  by default refuses to start off the process's initial thread and a JVM is never on it
  ([ADR-0084](adr/0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)). Installing
  either alone leaves the window bare. Whether Goldberry should carry its own
  decorations instead — `SdlWindowFlag.BORDERLESS` already describes the design — is the
  standing question behind both records.

## The native build and its bindings

- **The layout registry is now mostly constants, not layouts.** Seven struct layouts and
  61 constant rows, 48 of them Yoga enumerators. The struct half has a known limit —
  `YGSize` is identical on all six targets, so its row proves nothing the round trip in
  [ADR-0017](adr/0017-proving-the-struct-by-value-upcall.md) does not — but the constant
  half is where the value is: `YGAlignCenter` is 2 and `YGJustifyCenter` is 1, and a
  Java constant that drifts from either produces a layout that is wrong on every
  platform at once and never an error. —
  [ADR-0010](adr/0010-hand-written-ffm-bindings.md),
  [ADR-0029](adr/0029-yogas-node-api-and-who-owns-a-node.md)
- **The export machinery has now caught the same class of bug three times.**
  `--exclude-libs,ALL` forced static-archive symbols local, so `SDL_Init` linked in
  without being exported; removing the flag fixed it, because a version script cannot
  promote a symbol already marked hidden. Blend2D then hit the identical wall from the
  other side: a static build defines `BL_STATIC`, which makes `BL_API` expand to
  nothing, so the superbuild's global `hidden` visibility applied to every Blend2D
  function. All 13 linked in and arrived **local** — `nm -D` showed none of them while
  `nm` showed them all as `t`. HarfBuzz then did it a third time and more bluntly:
  `HB_EXTERN` is defined as bare `extern`, with no visibility attribute at all, so all
  24 of its symbols went local too. Fixed by giving both targets default visibility; the
  version script's `local: *` still gates the output. The fix is a loop rather than two
  blocks, because the next static upstream will probably need it as well. The equivalent
  question on the MSVC `.def` and Mach-O `-exported_symbols_list` branches is still
  answered by the next CI run rather than by argument — and the Mach-O branch has the
  *same* dependency on visibility that this fix addresses. —
  [ADR-0018](adr/0018-sdl-conventions-stop-at-the-boundary.md),
  [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)

## Build, artifacts and release

- **A build with no network cannot produce a usable `goldberry-core`.** The bundled
  fonts and icons are fetched from upstream releases and cached, so this bites once per
  checkout rather than once per build — but a jar assembled without the asset step
  contains a toolkit that cannot render text. The build already needed network for the
  native superbuild, so no new constraint; it is written down because the failure is far
  from its cause. —
  [ADR-0033](adr/0033-assets-are-fetched-and-compiled-not-committed.md)
- **No licence text is vendored yet.** Every file in `licenses/` is a placeholder.
  `./gradlew checkLicenses -Pgoldberry.releaseCheck=true` fails until they are copied
  verbatim from the pinned upstream revisions. —
  [ADR-0015](adr/0015-licensing-and-third-party-disclosure.md)
- **Nothing is publishable yet: there are no publications.** §15 says the four
  classifier jars and `goldberry-common`, `-core`, `-widgets`, `-gpu` go to Maven Central under
  `io.github.digitalsmile`. The half that exists is the artifact half — `release.yml`
  reuses the three per-OS workflows in one run, so all four libraries are built and
  downloaded into one job, and `:natives:nativeJars` packages them into classifier jars
  from `-Pgoldberry.artifactsDir`. The half that does not exist is publishing: **no
  subproject applies `maven-publish`**, so there is no publication, no POM, no javadoc
  or sources jar, no signing, no Central credentials and no `publish` task. The two
  `PublishToMavenRepository` lines in `assets` and `example` disable something that was
  never configured. `release.yml` therefore ends at `upload-artifact`, and it has still
  never run. — `docs/ARCHITECTURE.md` §15,
  [ADR-0009](adr/0009-publish-under-io-github-digitalsmile.md)

---

- **A style that really changes still re-resolves its whole subtree, and only
  the inherited properties can matter.** ADR-0142 stopped a node handing down a
  new instance for an unchanged value; what it did not do is narrow the
  comparison to the properties a child could actually inherit. So scrolling —
  which moves a transform, and a transform inherits nothing — re-resolves every
  node inside the viewport on every frame of the gesture. The fix needs a notion
  of which properties inherit, which the cascade has and `ComputedStyle` does
  not. —
  [ADR-0142](adr/0142-a-style-handed-down-keeps-its-identity.md)
- **An icon larger than its slot overflows it.** An `Icon` is a path built at a
  size and cannot be rescaled at paint time (ADR-0043), so a 20px glyph in a menu's
  16px leading column is 20px — centred now rather than parked in the corner,
  which is the difference between "large" and "misaligned", but still larger than
  the column. An application that wants them to fit builds them at 16, and
  nothing says so at the door. —
  [ADR-0143](adr/0143-a-strip-keeps-its-height-and-an-icon-its-centre.md)
- **60 ms is how long a focus-lost is disbelieved for.** Long enough to cover the
  focus-lost/focus-gained pair that opening a popup produces, short enough that
  nobody sees a menu over another application. It is one number covering every
  driver, and the first driver that delivers that pair more slowly will look like
  a menu that closes as it opens. —
  [ADR-0144](adr/0144-a-popup-goes-away-when-the-application-does.md)

- **A menu row overflows rather than ellipsising.** ADR-0148 stopped a squeezed
  row wrapping to two lines, which was the visible defect; what it leaves is a
  label running off the edge of a menu too narrow for it. §8's subset has no
  `text-overflow` and nothing in this toolkit clips, so there is no third
  behaviour to choose. `option` has carried the same gap since ADR-0099. —
  [ADR-0148](adr/0148-a-menu-row-does-not-wrap.md)

- **`Element.update` still invalidates a subtree wholesale.** ADR-0149 narrowed
  the *state* path; a rebuilt widget still throws away everything below it,
  because what changed there is the node's identity to the cascade — its classes
  and its id — rather than one bit of its state. Narrowing that needs the same
  index keyed on classes as well as types, and nothing has measured it as a
  problem. —
  [ADR-0149](adr/0149-a-state-invalidates-what-it-can-reach.md)

- **A HUD costs about three shaped paragraphs a frame, and reports the cost as
  its own.** Its readings are strings that change every frame, so no cache keyed
  on the string can hold them. The caption says so rather than hiding it, and the
  ways out are all worse than the disclosure: refreshing the text at 10 Hz would
  need per-frame state a widget cannot have, and excluding the overlay subtree
  from the timings would report a frame the window did not paint. —
  [ADR-0152](adr/0152-the-cascade-looks-at-rules-that-could-match.md)
- **The rule buckets are only as good as the stylesheet.** A sheet written
  entirely in classes puts every rule in the untyped bucket and gets none of
  ADR-0152's saving. The toolkit's own sheets are type-first and nothing enforces
  that they stay so. —
  [ADR-0152](adr/0152-the-cascade-looks-at-rules-that-could-match.md)

- **A widget's CSS classes share a namespace with the design system's.** A `hud`
  reading named `display` picked up §1.4's `.display` type rank and rendered at
  28px. Renamed, and nothing prevents the next one: there is no prefix
  convention, no check, and the two sets of names are written in different files
  by different people. —
  [ADR-0153](adr/0153-a-rate-is-counted-a-refresh-is-asked-for.md)

- ~~**A popup hangs on screen when the application loses focus to another
  window**~~ **No popup of any kind holds the platform keyboard now**, so
  `anyWindowFocused` means what it says: the application is focused exactly when
  one of its own real windows is. A popup never relied on focus anyway — the owner
  has forwarded keys to whatever popup is open since ADR-0104, precisely because
  SDL focuses `POPUP_MENU` windows on some drivers and not others. —
  [ADR-0189](adr/0189-no-popup-holds-the-keyboard.md)
- **(was)** —
  **still open, and one candidate is eliminated.** `anyWindowFocused()` counts
  popup windows, so a popup holding platform focus keeps the whole check true. A
  `MENU`-kind popup is focusable and is the likely culprit; the suggestion panels
  are `TOOLTIP`-kind and `NOT_FOCUSABLE` since
  [ADR-0186](adr/0186-a-panel-that-hangs-off-a-field-is-not-a-menu.md), so they
  can no longer be it. The next step is a real window and a log of `FocusChanged`
  per window id, which the headless backend cannot produce.
- ~~**`flex-wrap` is not in §8's subset.**~~ **It is, and it took the shape this
  entry predicted** — one component on `Box`, one on `ComputedStyle`, one line in
  the render tree, and 48 positional reconstructions. What it did not predict is
  the half that mattered: putting the property on the *field* wraps the chevron
  onto a second line under the chips, so the chips needed a box of their own. A
  golden image is what said so; nothing in the CSS looked wrong. —
  [ADR-0192](adr/0192-a-row-of-chips-wraps-and-the-chevron-does-not.md)
- **`align-content` is still absent**, which is what decides how wrapped *lines*
  share the cross axis. It does not matter to a chip row, whose height is its
  content, and it would to a wrapped row in a box with a fixed height — where
  Yoga spreads the lines and CSS's default packs them. Nothing in the catalog
  wants one yet, which is the rule §8's subset has grown by all along. —
  [ADR-0192](adr/0192-a-row-of-chips-wraps-and-the-chevron-does-not.md)
  The window hides and the popup stays where it was. The mechanism
  [ADR-0144](adr/0144-a-popup-goes-away-when-the-application-does.md) describes is
  wired — the launcher watches `FocusChanged` and calls `dismissPopups` after a
  settle delay if no window of the application is focused — so this is a fault
  *inside* it rather than a missing feature. Two candidates and no evidence yet:
  a popup window still reporting focused, so `anyWindowFocused` never goes false;
  or the platform not sending `FocusChanged` at all when the owner is hidden
  rather than deactivated. Diagnosing it needs a real window and a real
  compositor. —
  [ADR-0185](adr/0185-a-list-that-hangs-off-a-field-does-not-take-the-keyboard.md)
- ~~**Nothing drives §3's select family through the real loop.**~~ **`SelectLoopTest`
  does**, and found a seventh defect on its first run: a click opened the list and
  closed it again in the same gesture, because the press focused the editor (which
  opens it) and the click then toggled from a stale `open` flag. One signal opens
  an editable control now, and the signal is focus. What the harness still cannot
  reach is the platform's window flags — reverting `NOT_FOCUSABLE` fails nothing,
  because the headless backend has none. —
  [ADR-0188](adr/0188-a-control-opens-on-one-signal.md)
- **(was) Nothing drives §3's select family through the real loop.** All three defects
  ADR-0185 fixed were found by running the application and were green in CI,
  because every test drives the widget by hand and each fault lives in the seam
  between the widget and a running window — the application's rebuild, the
  platform's focus, the pointer. `MenusTest` does drive the real launcher against
  the headless backend and is the shape that would have caught them. Closing this
  is worth more than the three bugs were. —
  [ADR-0185](adr/0185-a-list-that-hangs-off-a-field-does-not-take-the-keyboard.md)

## Answered

Kept rather than deleted: each is a trap somebody hit, and the reasoning that got
out of it is usually worth more than the fact that it is fixed.

- ~~**The HUD's budgets assume a 60 Hz display.**~~ **They are shares of the
  display's own frame now.** `SDL_GetCurrentDisplayMode`'s refresh rate was
  already bound for the frame pacer; it reaches a `hud` through
  `FrameStats.displayHertz()`, and a platform that will not say falls back to 60
  with the reading showing dashes rather than a number it does not have. —
  [ADR-0153](adr/0153-a-rate-is-counted-a-refresh-is-asked-for.md)
- ~~**A click costs one node's style.**~~ **It cost the whole tree's, and the
  HUD is what found it.** Hover and active apply to the ancestor chain, and every
  node in that chain invalidated its entire subtree in case a descendant
  combinator read the state — 74 of 78 elements per click on the showcase. This
  was never on this list because nothing could see it until the frame had a
  breakdown. —
  [ADR-0149](adr/0149-a-state-invalidates-what-it-can-reach.md)
- ~~**The style cache makes a settled frame free.**~~ **It did not, and had not
  since `scroll` shipped.** ADR-0070 measured style resolution as the largest
  term in a frame and cached it; the cache was keyed on the parent's style *by
  identity*, and the style a parent hands down is not the one it caches —
  `restyle` runs afterwards and allocates. Every node under a `scroll`, a `tab`
  or a `segmented` re-resolved on every frame, which in the showcase is every
  node on the screen: 10 069 µs to render 77 unchanged elements. This was never
  on this list, because nothing had measured it. —
  [ADR-0142](adr/0142-a-style-handed-down-keeps-its-identity.md)
- ~~**Nothing tells the toolkit its window lost focus.**~~ **`FocusChanged`
  does.** A menu left open while the user switched applications stayed on screen
  over the one they switched *to*, because a popup is always-on-top by kind and
  light dismissal only ever saw a press inside the owner window. —
  [ADR-0144](adr/0144-a-popup-goes-away-when-the-application-does.md)
- ~~**`option` lives in `…controls.segmented` and `select` will want it.**~~
  **It lives in `…controls.option`, and `select` wants exactly what `segmented`
  wanted.** The guess this entry refused to make — "a model, possibly a tree
  node, a popup to render in" — turned out to be wrong in every part: a row in a
  dropdown is the same record as a cell in a bar, and the whole difference
  between them is a stylesheet's ancestor selector and one flag saying whether
  the keyboard chooses or merely moves. ADR-0092's rule paid for itself twice
  over — it stopped a generalisation that would have been made from the wrong
  example. —
  [ADR-0141](adr/0141-a-select-is-a-closed-control-and-a-list.md)
- ~~**A press that dismisses a popup also activates what it lands on.**~~ **It
  does not, and this was never written down as a gap because nothing had hit
  it.** With a list open, the press on the field that dismisses it was also read
  as "open it", so a `select` toggled twice and stayed open. The launcher already
  took the press for the secondary button (ADR-0108); it now takes any press that
  actually closed something, which is what the click that puts a menu away does
  everywhere. —
  [ADR-0140](adr/0140-a-widget-may-reach-its-window.md)

- ~~**A popup does not size itself to its content.**~~ **It does, in two passes.**
  `RenderTree.measure` lays a tree out with no surface; the second pass exists
  because Yoga lays a *root* out at exactly the available size when that size is
  definite — there is no parent for it to be "at most" of — so measuring against
  the window returns the window. Nothing definite first, then the width pinned
  only if the natural width overflows. —
  [ADR-0104](adr/0104-a-popup-is-measured-then-placed.md)
- ~~**Placement is not policy.**~~ **`Placement` is, and it is arithmetic.**
  Preferred side, flip only when the preferred side does not fit and the opposite
  one does, then shift along the cross axis; clamped to the near edge when it fits
  nowhere. Computed against the display's **work area** — `SDL_GetDisplayUsableBounds`,
  reached through `BackendWindow.workArea()` and translated by `position()` — which
  is the rectangle that excludes the taskbar a menu would otherwise open under. —
  [ADR-0104](adr/0104-a-popup-is-measured-then-placed.md)
- ~~**Focus does not travel into a popup.**~~ **The keyboard belongs to the open
  popup.** Its router focuses the first item after the first frame, and keys the
  owner window receives are forwarded to the topmost popup before the owner's own
  router sees them. Forwarded rather than delegated to platform focus, because
  whether a popup gets the keyboard is per-driver and a tooltip must never have
  it. What is still owed is the *return*: §7's "restores focus on close" is the
  widgets' to keep, and nothing yet remembers what had focus before a menu opened.
  — [ADR-0104](adr/0104-a-popup-is-measured-then-placed.md)

- ~~**`Sdl3Backend.translate`'s `MOUSE_WHEEL` branch has never run.**~~ **Answered: it
  runs, through the real SDL, on every CI run.** A test cannot turn a wheel — but
  `SDL_PushEvent` can, which is what the call is for. A fabricated
  `SDL_MouseWheelEvent`, written at the offsets the layout probe has already checked
  against the compiled C, goes onto SDL's own queue, comes back out of the ordinary pump
  and takes the shipping route: the real `translate`, the real window lookup, the real
  sink. The tests assert the sign is inverted exactly once (SDL's y is positive away
  from the user, the SPI's is positive down the document), that "natural scrolling" is
  undone before that rather than after, that a touchpad's fractions survive, and that
  the position comes from the wheel arm's own fields — reading it through the motion
  arm's accessor returns 3.0 where the answer is 120.0, because the vertical delta lands
  at exactly that offset. Under SDL's `dummy` video driver, so it needs no display and
  runs on all three platforms. The **cursor** half was already answered: the showcase
  sets `Cursor.CROSSHAIR` at start-up, so `SDL_CreateSystemCursor` and `SDL_SetCursor`
  really run. — [ADR-0061](adr/0061-the-events-a-test-cannot-produce-are-pushed.md),
  [ADR-0056](adr/0056-the-wheel-is-lines-and-the-sign-is-ours.md),
  [ADR-0057](adr/0057-the-cursor-rides-on-the-painted-box.md)
- ~~**Group opacity is a multiply, not a layer.**~~ **Answered: it is a layer.** A node
  with `opacity < 1` **and children** is composited through an offscreen raster drawn at
  full strength and faded once, which is what CSS specifies. `group-opacity.png` is two
  overlapping squares under a parent at 50%, and the test asserts the overlapping pixel
  *equals* the non-overlapping one — true for a layer, false for a multiply. A
  translucent **leaf** keeps the cheap path deliberately: its own shapes can overlap
  each other, but by a fraction of a level on an antialiased edge, and an allocation and
  a blit per faded label is a poor trade. Three goldens with a `:disabled` control at
  45% moved, and the diff is confined to that control — the correction, reviewed rather
  than accepted. — [ADR-0071](adr/0071-a-layer-is-a-subtrees-raster.md),
  [ADR-0064](adr/0064-a-rounded-rectangle-is-four-cubics.md)
- ~~**`body-strong` is not drawn, and no control uses a weight.**~~ **Answered: a weight
  is a face.** `Inter-SemiBold.ttf` is extracted beside the variable file, `font-weight`
  resolves to one of two shipped faces in the cascade, and a button's label is Inter 600
  at 13/18. Instancing the `wght` axis would have been the smaller download and needed
  symbols in both HarfBuzz and Blend2D — three export branches, answered only by a CI
  run across four targets — while §1.4 ships exactly two weights. The axis stays a real
  optimisation for the day an intermediate weight is specified. —
  [ADR-0066](adr/0066-a-weight-is-a-face-and-color-inherits.md)
- ~~**Seven shipped `button` colour pairs are below §1.2's 4.5:1 floor.**~~ **Fixed, and
  the worst of them was a rule applied where it does not hold.** §1.2 had always said
  "every text/surface pair meets **WCAG 4.5:1** […] validated in CI against both
  themes"; nothing validated anything until `badge` forced the question, and the first
  run of `ContrastTest` found `--gb-button-danger-text` on `--gb-button-danger-bg` at
  **3.55:1** — `--nord6` on `--nord11`, unchanged since the first control shipped. Two
  things in the numbers were the shape of the fix rather than its size. **Every ramp's
  darkest step already passed** (`button.danger:active` is 5.11:1 on light), so nothing
  needed a new colour system — the ramps needed *sliding*, and the value that was
  `:active` is roughly where rest belongs. And **the worst pair was a hover state that
  was worse than the rest state one step from it**: `button.danger:hover` at **2.95:1**
  on dark, below the 3.55 it moved from. The dark theme lightens on hover, correctly,
  for a *surface* moving one step toward the light — and a danger button is not a
  surface, it is a saturated fill carrying `--nord6`, so lightening moved it **toward
  its own text**. Stated as a rule it already described three of the four filled
  variants: **a fill that carries text moves away from it**. So `button.danger` on dark
  now darkens on hover, against that theme's usual direction and alone in the toolkit in
  doing so. `--gb-danger-fill` and `--gb-accent-fill` replace the aliases to `--nord11`
  and `--nord10`, and the danger ramp is now **identical on both themes**, because the
  hue is and the text on it is. The one piece of collateral was worth catching:
  `--gb-checkbox-bg-checked-hover` and its radio and toggle counterparts **aliased the
  button's ramp**, on the argument that a checked control and a primary button share the
  accent — true until a button's fill started being chosen for its *label*. A checked
  glyph carries a mark, which §1.2 asks 3:1 of, so `--gb-accent-bg-hover`/`-active` are
  split out holding the values the button's ramp used to, and **every checkbox, radio,
  toggle, slider, progress and spinner golden is byte-identical** — two button images
  are the only ones that moved, which is what says the split landed where it was aimed.
  `KNOWN_FAILURES` is now empty and *stays*, asserted equal to the measured failures and
  asserted empty by name: nothing is exempt, and re-exempting a pair fails a test that
  says what happened —
  [ADR-0088](adr/0088-a-fill-that-carries-text-moves-away-from-it.md),
  [ADR-0087](adr/0087-a-semantic-fill-brings-its-own-foreground.md),
  [ADR-0082](adr/0082-a-preflight-check-that-cannot-fail-is-not-a-check.md)
- ~~**Nothing animates.**~~ **Answered for the properties that can.** The frame clock,
  the curves, the overlay, the whitelist, OKLCH interpolation and reduced motion all
  ship, and the frame loop goes idle the frame after a transition ends. What is left of
  §1.7 is listed below rather than here. —
  [ADR-0067](adr/0067-motion-is-an-overlay-on-a-frame-clock.md)
- ~~**`transform` is in §1.7's whitelist and is not implemented.**~~ **Answered, and the
  trap it named is what the change is about.** `transform` and `transform-origin` parse,
  cascade, apply down the box subtree the way `opacity` does, animate through the
  overlay, and — the part worth the separate record — **route input through the inverse
  of the matrix the painter used**, computed once while painting rather than re-derived
  on the input path. A transform the painter applies and hit testing ignores produces no
  error and no wrong pixel: the control is drawn where the stylesheet asked and simply
  does not respond where it looks like it should. **No new native symbol crosses the
  boundary**: `bl_context_apply_transform_op` was already exported for the display
  scale, and `BL_TRANSFORM_OP_ASSIGN` replaces the context's matrix rather than
  composing onto it — so the stack is accumulated in Java, which is also what makes it
  invertible. Blend2D's `save`/`restore` are not exported and turned out not to be
  needed. A computed `transform` is the **function list**, not a matrix, because
  `translate(50%)` and the `50% 50%` origin default are proportions of a box that has no
  size until Yoga has run — and because halfway between `rotate(0)` and
  `rotate(180deg)`, interpolated entry by entry, is a collapsed box rather than a right
  angle. — [ADR-0068](adr/0068-the-transform-stack-is-java-side.md)
- ~~**The check mark still does not scale.**~~ **Answered, and `transform` was never
  what was missing.** §1.7 and §3.1 specify the checkbox tick and the radio dot as
  "scale 0.6→1 + opacity"; the opacity half shipped with ADR-0067 and the scale did not
  arrive with `transform`. The reason is that a `Box.Mark` is drawn **onto** the box
  carrying it, so scaling the indicator scaled the 16px glyph with it — the ring grew
  with the tick. The mark is now a cascade node of its own (`check-mark`, `radio-dot`),
  which makes them the third and fourth parts and the first justified by something other
  than "two surfaces need two backgrounds": two things must **move** independently, and
  the unit of independent movement is a node. The mark is built in *every* state and
  hidden with `opacity`, because a node that appears with the value has no previous
  style to move from and would snap. `radio-group-scaling.png` is the frame at 80 ms of
  160, one dot growing in and the one it replaced shrinking out, and what it asserts is
  that **all three rings are the same 16px circle** — which is precisely what the naive
  fix gets wrong. §3.1 now has no unimplemented row for any shipped control. —
  [ADR-0073](adr/0073-a-composite-is-one-tab-stop.md),
  [ADR-0068](adr/0068-the-transform-stack-is-java-side.md),
  [ADR-0065](adr/0065-a-part-is-styleable-and-not-constructible.md)
- ~~**`:active` was set on one element, so no control had a pressed state.**~~
  **Fixed.** `:hover` walked the ancestor chain from the beginning; `:active` was set on
  the single deepest element the press landed on — so pressing a checkbox's 16px glyph
  lit up `check-indicator`, pressing its label lit up `text`, and `checkbox` itself
  matched only in the sliver of padding between them. `checkbox:active` had been in
  `controls.css` since the control shipped and was very nearly a dead rule. §2.1
  requires every control to render a pressed state, and one that depends on which of its
  own parts you hit does not have one. Found by trying to write the radio's pressed
  appearance, not by a test — and the test that now covers it asserts the *ancestor*,
  which is the half the original test never looked at. —
  [ADR-0073](adr/0073-a-composite-is-one-tab-stop.md)
- ~~**An unnamed key crashed the window.**~~ **Fixed.** `keyPressed` built a `Shortcut`
  from every key that reached it, to use as a map key. `Shortcut` refuses to hold
  `Key.UNKNOWN` — an accelerator on it could never fire — so the
  `IllegalArgumentException` went up the UI thread with nothing above it. Not an edge
  case: `Key` names the keys a *shortcut* might use, so every letter, digit and
  punctuation mark that arrives as text is `UNKNOWN`, and the crash was one keystroke
  away at all times. The accelerator tests never saw it because they only ever pressed
  keys that had names. — [ADR-0073](adr/0073-a-composite-is-one-tab-stop.md)
- ~~**A checkbox was invisible on the surface it normally sits on.**~~ **Fixed, and the
  reason CI missed it is the interesting half.** `--gb-checkbox-bg` was `nord1`, which
  is `--gb-surface`; the light theme's was `#ffffff`, which is *its* `--gb-surface`. The
  token's own comment gives the mistake away — "one step up from the window" was
  measured against `--gb-bg`, and almost nothing sits directly on the window. Both
  glyphs now take the **button's** ramp on each theme rather than one of their own,
  which is the scale §2.1's "one surface step" is already defined by. **Every golden
  image in this repository paints on `--gb-bg`**, so a control that disappears on
  `--gb-surface` was invisible to the entire suite;
  `controls-on-surface-{dark,light}.png` add the missing axis rather than one more
  scene. — [ADR-0073](adr/0073-a-composite-is-one-tab-stop.md),
  [ADR-0050](adr/0050-golden-images-have-a-tolerance.md)
- ~~**`--gb-density` is not implemented.**~~ **Answered, and deliberately at four
  controls rather than at thirteen.** §1.3's `regular | compact` ships: every control
  sizes itself from `--gb-control-height`, and `density-compact.css` is a three-token
  `:root` block in the **theme layer** — the same slot as `nord-light`, because that
  layer is defined by what it holds rather than by what it is called, and a fifth
  cascade layer would differ from the fourth in its name and nothing else. The layer is
  also what makes the override work: both blocks are `:root`, so specificity ties and
  `layer` is the only term left to separate them, which is why the test asserts the
  layer rather than the resolved height. **`Density.REGULAR` ships no stylesheet at
  all** — regular is not something an application applies, it is what the toolkit
  already is, and a `density-regular.css` restating 32 would be one number in two files,
  which is the arrangement that produced both the §10.1 typography table and the
  checkbox's private surface ramp. **Padding, gap and radius stay literal**, asserted
  so: §1.3's density row names heights and list rows, and tokenising the rest "for
  symmetry" invents a scale the design system does not have. `--gb-density` itself is a
  **marker rather than the mechanism**, because a keyword cannot select a number in §8's
  subset. Every existing golden is byte-identical, which is the check that the token
  swap was a refactor; two new ones are the same scene at both densities. The showcase
  switches on `Ctrl+D` and **not one widget in that file mentions a height**, which is
  the whole of what "token-conformant apps adapt with zero code" claims. Named rather
  than implied: **compact is below §1.3's own 32×32 hit-target floor**, deliberately —
  the floor is the *regular* default rather than an invariant, the trade is what a
  density preference *is*, and it is bounded by the glyph staying 16px so compact costs
  margin around the target rather than a smaller target. —
  [ADR-0074](adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md),
  `docs/design-system.md` §1.3
- ~~**A slider's groove was invisible on a surface.**~~ **Fixed, and it is the fourth
  instance of one defect.** `--gb-slider-track-bg` was `nord1` on the dark theme, which
  **is** `--gb-surface` — so the unfilled groove vanished on any panel, which is where
  the showcase's options live. A slider hides this better than anything before it: the
  fill and the thumb still show, so the control looks like a control and merely appears
  to have no track. It is `--gb-border` now, because a 4px groove *is* an edge. What is
  different this time is that **`controls-on-surface-{dark,light}` already existed** —
  ADR-0073 added it for exactly this — and had not been extended to the new control, so
  the axis was covered and the control was not. `everySurfacelessControlIsCovered` now
  asserts every entry in `Controls.controlTypes()` is in that scene, with `button`
  exempt and saying why, and the scene is one helper the golden and the guard share.
  Verified by deleting the slider from the scene and watching it fail by name. —
  [ADR-0079](adr/0079-a-continuous-value-is-placed-by-ratio.md),
  [ADR-0073](adr/0073-a-composite-is-one-tab-stop.md)
- ~~**A slider has no tick marks and no value label.**~~ **Both ship, and the label
  needed exactly the mechanism this entry predicted.** A widget can name the **part**
  its pointer position is measured against — `Handles.localPart()`, a CSS type resolved
  by the router — because a label at the end of the row takes its width off the track
  and a value mapped along the *control* is short by that width at every position, drawn
  correctly and reported nowhere. The marks hang out of a zero-height row, moved clear
  of the thumb by a `transform` so that adding a scale does not move the groove. —
  [ADR-0080](adr/0080-a-value-is-measured-along-a-part.md),
  [ADR-0079](adr/0079-a-continuous-value-is-placed-by-ratio.md)
- ~~**`fader`'s dB scale is not implemented.**~~ **It ships, as a value rather than a
  function.** `Scale` is a sealed interface with two inverse methods and two records —
  the obvious `DoubleUnaryOperator` spelling is the wrong one, because §11's parity
  invariant compares two control records for equality and two lambdas doing the same
  arithmetic never are. `knob`'s taper is what it was built general for. What it does
  *not* have is a second curve: §3 names dB and nothing else, and inventing a `log` or
  an `exp` for symmetry would be inventing a scale the design system does not have
  (Principle 3). — [ADR-0080](adr/0080-a-value-is-measured-along-a-part.md)
- ~~**Arrow-key group navigation inside composites does not exist.**~~ **Answered, as a
  mechanism rather than as a radio group.** `Handles.focusScope()` makes a subtree one
  Tab stop with the arrows roving inside it, and `tabs`, `menu`, `select`'s popup list
  and a toolbar all get it by returning `true` from one method. The router owns both
  halves, by the argument already written on Tab — traversal is a property of the tree
  and not of any node in it — and the test is written against bare widgets in `:core`
  rather than against `radio`, because the next three users will look nothing like a
  radio. — [ADR-0073](adr/0073-a-composite-is-one-tab-stop.md)
- ~~**A focus scope has no axis.**~~ **Answered.** `Handles.focusScope()` returns a
  `FocusScope` — `NONE`, `HORIZONTAL`, `VERTICAL` or `BOTH` — and `radio-group` is the
  one composite in the catalog that legitimately answers `BOTH`, because its direction
  is its stylesheet's and `.inline` flips it. The **axis is the widget's** even though
  traversal stays the router's: the router cannot know what a widget means by the other
  pair, and the widget cannot see its own siblings. It only matters on the path where
  the widget **declines** the key, which is why a boolean survived four controls —
  arrows reach the focused chain first, so a menu bar that handles `Down` itself works
  either way. The failure it prevents is a menu item with no submenu declining `Right`
  and a `BOTH` scope quietly sliding focus to the next item: the user asked to open
  something and the selection moved instead, with no error anywhere. `Home` and `End`
  belong to no axis and reach the ends of any scope, because they name a position in the
  set rather than a direction on screen. Four widgets unblocked by an enum. —
  [ADR-0078](adr/0078-a-focus-scope-has-an-axis.md),
  [ADR-0073](adr/0073-a-composite-is-one-tab-stop.md)
- ~~**A disabled group fades correctly only by an explicit undo.**~~ **Answered, and the
  undo is deleted rather than generalised.** A rule whose only job was to undo its own
  mechanism was the mechanism saying it was the wrong one. —
  [ADR-0077](adr/0077-disabled-propagates-for-input-and-not-for-paint.md)
- ~~**Layer promotion does not exist, so every animating frame repaints the window.**~~
  **Answered.** A promoted subtree is rasterized at full strength and untransformed, so
  its alpha and matrix apply to the *blit* — and a group that is only fading or moving
  now **keeps its raster**, which is the case §1.7 wanted promotion for and which
  ADR-0071 shipped without. One flag had been answering three questions: does the screen
  differ (damage), does an *ancestor's* raster differ (yes, it bakes in this node's
  finished blit), does *this* raster differ (no, alpha and matrix are the composite's).
  A descendant's opacity **is** baked in, which is why it could not be fixed by dropping
  `opacity` from one comparison. Measured on the showcase's tree at 45%: **a frame of
  the fade is 199 µs against 554 µs**, 2.8×. `RenderTree.layersRepainted()` is public
  because a cached raster and a fresh one produce the same image, so no pixel assertion
  can tell them apart — which is exactly how the bug survived a test file written about
  layer caching. — [ADR-0072](adr/0072-a-partial-repaint-needs-a-promise.md),
  [ADR-0071](adr/0071-a-layer-is-a-subtrees-raster.md)
- ~~**Damage tracking says what to upload, not what to paint.**~~ **It paints what
  changed now.** `bl_context_clip_to_rect_d` and `bl_context_restore_clipping` are the
  third and fourth new exports, and `RenderTree.paint(frame, damage)` clips to the
  damage — **367 µs to 117 µs** on a frame where one small box changed. Read that
  carefully: the damaged area was 0.23% of the window and the saving is 3.1×, not 400×,
  because the clip saves *rasterization* while the tree walk still visits every box for
  Blend2D to clip away. Skipping the traversal too is a further change and is not made.
  Correctness rests on a **promise the SPI now makes**:
  `BackendWindow.retainsFrameContents()`, false by default so a backend that says
  nothing gets a full repaint. `Window` checks three things that fail independently —
  the promise, the buffer's *identity* (a backend may retain and still rotate between
  two), and the size — plus a fourth case where the backend lends nothing and the buffer
  is `Window`'s own, which retains by construction. A clipped repaint is asserted
  **pixel-identical** to a full one across a whole frame, because otherwise damage is a
  rendering bug with a performance excuse. —
  [ADR-0072](adr/0072-a-partial-repaint-needs-a-promise.md)
- ~~**A disabled container does not disable its descendants.**~~ **Answered, and the
  sentence turned out to have two halves that pull apart.** `docs/core-widgets.md` says
  "disables its descendants for **input and semantics**" — and deliberately not for
  paint, which is where the double-fade came from. **Input propagates**: no press,
  click, wheel, focus or key reaches a descendant of a disabled container. **Paint does
  not**: `:disabled` stays on the node that declared it, because the container's own 45%
  already fades everything under it (opacity multiplies down a subtree) and a descendant
  that also matched would land at 20%. It costs nothing in expressiveness, since §2.1
  requires disabled to be opacity and never a colour remap. The effective value is
  **derived by walking up the ancestors, not stored** — ADR-0073's lesson applied again:
  a second copy of a fact the tree already holds disagrees the first time something
  changes without telling the thing that cached it. The **router is the choke point**,
  one guard in `dispatch` plus `isFocusable`, so a control written without its own
  `disabled` check is still unavailable — and the **keyboard needed no guard at all**,
  because focus is the only route a key has, so one line about focus covers `onKey`,
  `onKeyCapture` and `onText` together. The cut is input versus **observation**: enter,
  exit, motion, hit testing and the cursor all still work, which is what keeps
  ADR-0059's two cases — a click that must not fall through, and a tooltip explaining
  *why* something is unavailable. `form`, `group-box` and a `dialog` in its `closing`
  phase all get this for free. —
  [ADR-0077](adr/0077-disabled-propagates-for-input-and-not-for-paint.md),
  [ADR-0059](adr/0059-a-control-is-a-record-a-node-and-a-rule.md)
- ~~**The state and rebuild API.**~~ **Answered.** The stateful-widget lifecycle,
  rebuild scheduling and dirty-marking are settled: state lives on the element,
  `setState` mutates immediately and defers the rebuild, and the tree flushes dirty
  elements once per frame. —
  [ADR-0052](adr/0052-state-lives-on-the-element-and-rebuilds-are-deferred.md),
  [ADR-0004](adr/0004-three-tree-retained-declarative-model.md)
- ~~**KDL 2.0 Java parser.**~~ **Answered, by writing one.** No third-party parser was
  adopted: the tokenizer and parser are hand-written for the §9 subset, with the §9
  example document as a test. —
  [ADR-0051](adr/0051-kdl-is-parsed-here-and-reloading-is-forgiving.md),
  [ADR-0005](adr/0005-css-subset-and-kdl-as-the-contracts.md)
- ~~**`YGSize` struct-by-value upcall returns.**~~ **Answered, and now driven by Yoga
  itself.** A Java upcall returning `YGSize` by value is called from C and arrives
  intact; the return segment is allocated once per callback rather than per call, and an
  exception thrown by a measure function is held and rethrown in Java instead of taking
  the process with it. The node API is bound, so the callback is invoked by real layout
  passes with the constraints the flexbox algorithm arrived at — not by a C probe
  written for the purpose. Proven on linux-x64; the checks run on every target in CI, so
  the other five are answered by the next run rather than by argument. —
  [ADR-0017](adr/0017-proving-the-struct-by-value-upcall.md),
  [ADR-0029](adr/0029-yogas-node-api-and-who-owns-a-node.md)
- ~~**Windows has never been built.**~~ **Answered.** All four targets link, and all
  three export branches are now exercised rather than argued about: the ELF version
  script on both Linux targets, the Mach-O `-u,_symbol` / `-exported_symbols_list` pair
  on `macos-aarch64`, and the MSVC `/INCLUDE:` and `.def` branch on `windows-x64`. The
  Windows leg builds `goldberry.dll`, runs `:natives:test` against it with
  `goldberry.native.required=true` so a skipped test cannot pass for a passing one, and
  matches the golden images — which is also what answers Win64's 4-byte `long`, the one
  thing no other target could catch. **What Windows has not done is open a window**: the
  leg links the library and runs the Java tests, exactly the hole
  [ADR-0039](adr/0039-macos-needs-the-first-thread.md) describes for macOS. The showcase
  image workflow is what would close it. —
  [ADR-0012](adr/0012-native-ci-runners-with-a-pinned-glibc.md),
  [ADR-0041](adr/0041-three-platforms-four-artifacts-two-backends.md)
- ~~**Live resize stalls on Windows and macOS.**~~ **Taken, and half proven.** Both
  platforms run a modal loop during a resize gesture, so SDL does not return from event
  pumping until the drag ends and frames stopped with it. Goldberry now installs an
  `SDL_AddEventWatch` callback and **draws from inside it**: SDL keeps pumping events
  within the platform's loop, and a watch is called from inside that pump, so it is the
  one place a frame can be produced while the platform holds the thread. Four guards
  decide whether it does anything — the UI thread, an active sink, re-entrancy, and the
  event type — and each is there because a watch is called in circumstances a pump never
  is; the resize the queue then delivers a second time is coalesced away rather than
  laid out twice. **What CI proves is the whole mechanism except the platform**: a test
  pushes an event from inside an event handler, which is the same state a modal loop
  creates, and asserts that the resize *and* a frame come out of the watch re-entrantly.
  What is left is that Windows' and macOS' loops really do pump during a drag — SDL's
  own documented behaviour, and a human with a mouse is what would confirm it. —
  [ADR-0060](adr/0060-a-resize-draws-from-inside-sdls-event-watch.md),
  [ADR-0024](adr/0024-a-repaint-must-wake-the-loop.md)
- ~~**Blend2D and AsmJit have no release tags.**~~ **Answered.** Neither upstream has
  ever cut one, so both are pinned by **commit SHA** instead — Blend2D at `6dbc2ce` and
  AsmJit at `0bd5787`, the pair that has actually built, linked and passed the tests.
  All six upstreams now resolve to exactly one commit, so the build is reproducible.
  What remains before publishing is the licence texts. —
  [ADR-0030](adr/0030-pin-blend2d-and-asmjit-by-commit-sha.md)
- ~~**Shaping itself is unverified: there is no font to shape with.**~~ **Answered.**
  Inter, JetBrains Mono and OpenMoji are fetched at build time, pinned by version and
  SHA-256, and packaged into `goldberry-core`
  ([ADR-0033](adr/0033-assets-are-fetched-and-compiled-not-committed.md)). Shaping now
  runs against real outlines: real glyph ids rather than `.notdef`, a proportional face
  measurably different from a monospace one, and emoji resolving through OpenMoji.
  Right-to-left glyph *reordering* is still unchecked — it needs a script the bundled
  faces cover. — [ADR-0032](adr/0032-shaping-is-utf16-in-glyphs-out.md)
- ~~**Nothing draws a glyph or an icon yet.**~~ **Both do.** `bl_font_*` and
  `bl_context_fill_glyph_run_d_rgba32` were bound first; the path API followed —
  seventeen symbols, one per SVG command, plus the three stroke options an icon needs
  because Lucide is drawn in strokes rather than fills. `SvgPath` reads the table's path
  data with SVG's own number grammar, and every one of the 1544 icons is asserted to
  parse and produce geometry. **What is still open is that an icon is not a `Box`**: the
  showcase draws them over its sidebar rather than laying them out in it, because
  nothing decides an icon's intrinsic size until the widget model does. —
  [ADR-0043](adr/0043-icons-are-stroked-paths.md),
  [ADR-0004](adr/0004-three-tree-retained-declarative-model.md)
- ~~**A `Font` costs two copies of the font file, and there is one per size.**~~ **Two
  copies per *face* now, not per size.** `FontFace` holds HarfBuzz's whole font — which
  is size-independent because Goldberry never scales the shaper — and Blend2D's data and
  face; `Font.on(face, size)` adds only the object the size lives on. A second size
  measures at 4.4 µs against 681, and four sizes of Inter cost three megabytes rather
  than twelve. Faces are owned explicitly rather than cached globally, because these
  objects are thread-confined and a per-thread cache of native memory has no hook that
  would ever free it. What remains is the two copies themselves: each library owns its
  own memory, and neither takes a borrowed buffer for font data. —
  [ADR-0044](adr/0044-one-face-many-sizes.md)
- ~~**Nothing measures text for layout yet.**~~ **It does.** A `Paragraph` shapes once
  and wraps with arithmetic, and its measure function reports a height to Yoga through
  the `YGSize` upcall. What is still ahead is bidi run splitting — right-to-left text is
  **refused at construction** rather than mis-wrapped, because HarfBuzz returns those
  glyphs in visual order and prefix sums taken in logical order would measure the wrong
  ones — and font fallback between the UI and emoji slots, which makes a paragraph
  several runs rather than one. —
  [ADR-0036](adr/0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md)
- ~~**The paragraph cache is a one-entry memo.**~~ **Both caches exist, and the numbers
  say why.** `ParagraphCache` holds shaped paragraphs keyed by `(font, text)`; the width
  memo stays inside each `Paragraph`. Shaping is 56 µs and a cache hit is 0.05 µs, while
  a memoised wrap is already 0.02 µs — so shaping is the only part worth a cache, and
  caching layouts would save nothing. The cache has **no consumer yet**, because nothing
  rebuilds a widget tree; it exists because the measurement says it will be needed the
  moment something does. §6's third key component, the width bucket, is the
  per-paragraph memo, and the "resolved text style" is a `Font` until the CSS engine has
  something better. — [ADR-0037](adr/0037-what-the-text-path-costs.md)
- ~~**A fresh upcall stub per text box per frame is the largest cost of text in a layout
  pass.**~~ **Answered: the render tree is retained.** `RenderObject` owns a `YGNode`
  that survives the frame and keeps its measure callback for as long as the paragraph
  behind it is the same instance. Measured on a showcase-shaped tree with seven measured
  leaves at 960×640: **layout and walk fall from 190 µs to 7.2 µs**, and a whole frame
  from **354 µs to 148 µs**. The 7.2 µs row is the one that had to be won — it hands
  over a *fresh box tree every frame*, as a real application produces, and it matches
  the do-nothing case because every Yoga setter is guarded by a comparison against the
  box already applied. Yoga dirties a node when a style is **set**, not when it changes,
  so an unguarded retained tree would cost exactly what a thrown-away one costs plus the
  memory management. Retention also introduced this repository's first keep-state bug,
  caught by its own equivalence test: **Yoga does not dirty a node when its measure
  function is replaced**, so a paragraph swapped for longer text reported the height
  cached for the old one — six lines of prose laid out as one, with no error anywhere. —
  [ADR-0069](adr/0069-the-render-tree-is-retained.md),
  [ADR-0037](adr/0037-what-the-text-path-costs.md),
  [ADR-0004](adr/0004-three-tree-retained-declarative-model.md)
- ~~**The cascade is now the largest term in a frame.**~~ **Answered: it resolves
  invalidated nodes, which is what §5 always said it did.** A node's resolved style is
  cached on its element and checked by identity against two things — the **resolver**,
  so a theme swap or a hot reload invalidates everything at once with no event to
  remember to fire; and the **inherited style**, so a parent that re-resolved hands its
  children a different instance and they re-resolve without being told. Invalidation is
  a **subtree**, because a descendant combinator means a node's own match depends on an
  ancestor's state: `checkbox:hover check-indicator` restyles the indicator while the
  checkbox's own style need not change at all, and that rule is in `controls.css` today.
  One hook — `setPseudoClass` — covers `:hover`, `:active`, `:focus`, `:disabled`,
  `:checked` and `:indeterminate`, and fires only on an actual change, which matters
  because the renderer mirrors three of them onto every styled element every frame.
  **The CPU a frame spends before rasterizing falls from 148 µs to 3.5 µs** — 354 µs to
  3.5 µs taken with the retained render tree, a factor of a hundred. —
  [ADR-0070](adr/0070-the-cascade-resolves-invalidated-nodes.md),
  [ADR-0052](adr/0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)
- ~~**The isolated paint benchmark and the in-app paint number disagree by 10×.**~~
  **Answered: it is `present`.** A frame that follows a present costs about four times
  what the same frame costs painted back-to-back — 2.19 ms against 0.57 ms, measured by
  skipping present and changing nothing else — and the benchmark never presents. It was
  **not** the borrowed compositor buffer, which was the standing hypothesis: painting
  into a heap buffer measured 2.28 ms against the surface's 2.22 ms. Nor the icons
  (+0.01 ms), the display server (Wayland 2.22, X11 2.07), the compositor (SDL's `dummy`
  driver 2.00), or the environment at all — the benchmark's own loop, run *inside* the
  live application between two real frames, came out at 0.49 ms while those frames cost
  2.06 and 2.25. The mechanism is cache and TLB pollution; a synthetic 96 MB eviction
  between iterations reproduces 1.6× of the 3.8×. —
  [ADR-0045](adr/0045-a-frame-is-not-a-benchmark-iteration.md)
- ~~**Every frame damages the whole window.**~~ **Answered for the upload.** Something
  now knows which parts changed: the retained render tree remembers each node's
  rectangle and reports the union of old and new for whatever moved. What is still true
  is that the *painting* is full-frame — see the damage entry above for why that needs
  an SPI change rather than more code here. —
  [ADR-0071](adr/0071-a-layer-is-a-subtrees-raster.md),
  [ADR-0004](adr/0004-three-tree-retained-declarative-model.md)
- ~~**CMake arguments live in five places.**~~ **The refs do not any more.**
  `CMakeLists.txt` reads `gradle/libs.versions.toml` itself, so a ref bump is one edit
  and there is no default to drift from; a floating ref is refused at configure time.
  The manylinux container never needed a JDK to read the catalog, only something that
  can parse a text file. `checkPinnedRefs` is inverted — it asserts no copy has come
  back, across *every* workflow rather than three, which is what would have caught
  `example.yml` pinning Blend2D to a floating `master`. The rest of the argument list —
  build type, install prefix, target id — is still kept in step by hand. —
  [ADR-0035](adr/0035-the-catalog-is-the-only-place-a-ref-lives.md)
- ~~**Nothing warns at run time that a window came up undecorated.**~~ **Answered:
  `WaylandDecorations` warns, once, with the command that fixes it.** Not by asking SDL,
  which cannot answer — `libdecor_new` succeeds even when every plugin failed, so SDL
  marks the surface `WAYLAND_SHELL_SURFACE_TYPE_LIBDECOR` and exposes nothing to say the
  frame is empty. It is inferred from which plugin files are installed, which works
  because the GTK plugin is *guaranteed* to fail in a JVM. The verdict is three-valued
  and stays silent when it cannot locate a plugin directory: a warning that is sometimes
  wrong is worse than none. —
  [ADR-0084](adr/0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md)
- ~~**A Goldberry window on GNOME/Wayland has no titlebar out of the box.**~~
  **Answered: X11 is the Linux default now.** On a Wayland session the backend asks SDL
  for `x11,wayland`, unconditionally — under XWayland the window manager decorates the
  window itself, which is the only configuration today that produces a titlebar matching
  the desktop. Wayland stays behind X11 rather than being dropped, so a session without
  XWayland still gets a window, and the
  [ADR-0084](adr/0084-the-gtk-plugin-cannot-decorate-a-jvms-window.md) warning still
  fires there. `-Dgoldberry.backend.videoDriver=wayland` asks for Wayland anyway. The
  cost is [ADR-0027](adr/0027-prefer-wayland-fall-back-to-x11.md)'s resize quality and
  fractional scaling, given up for as long as decorations are unobtainable on the better
  axis. — [ADR-0086](adr/0086-x11-is-the-linux-default-for-now.md)
- ~~**A window on GNOME/Wayland had no titlebar and could not be resized.**~~
  **Answered: SDL was built without libdecor.** Wayland has no decoration protocol of
  its own, GNOME's compositor declines to draw them server-side, and every use of the
  client-side path in SDL sits behind `#ifdef HAVE_LIBDECOR_H`. Without `libdecor-0-dev`
  SDL builds a complete Wayland driver that opens an undecorated toplevel — and since a
  Wayland resize is client-initiated from the decoration's own edge, the same missing
  header removes resizing too. The Java side was never involved: `WindowSpec.of` asks
  for decorated and resizable and `Sdl3Backend.createWindow` passes exactly that. It
  only became visible when ADR-0082 added `egl` and the Wayland driver started being
  built at all. — [ADR-0083](adr/0083-on-gnome-wayland-libdecor-is-not-a-fallback.md)
- ~~**`checkToolchain` passed and the build died two minutes later.**~~ **Answered: the
  table it checked had drifted from what SDL demands.** It probed `pkg-config --exists
  xss`, a module no distribution ships — SDL's own spec is `xscrnsaver` — so the row
  returned "absent" whether the package was installed or not, and it was marked optional
  besides, while SDL's `CheckX11` treats XScrnSaver as a `FATAL_ERROR`. XTest, the next
  hard stop in line, was not in the table at all. Both CI workflows already knew all of
  this, in comments, written by whoever hit it there twice. The table is now
  `LinuxDependencies` in build-logic with a three-valued `Necessity`, and
  `LinuxDependenciesTest` asserts it against the packages the workflows install — the
  invariant that broke. —
  [ADR-0082](adr/0082-a-preflight-check-that-cannot-fail-is-not-a-check.md)

