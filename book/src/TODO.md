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
of them and records where it knowingly departs. **Five of the seven were taken on
2026-09-17** and each went the way the section says it should: the platform
primary modifier is a modifier you can name
([ADR-0378](adr/0378-the-desktops-own-modifier-has-a-name.md)), a disabled
container reaches the cascade
([ADR-0379](adr/0379-a-disabled-container-reaches-the-cascade.md)), `text style=`
is built ([ADR-0381](adr/0381-a-rank-has-two-spellings-and-one-meaning.md)), the
`tooltip` row's radius and rank are what ships
([ADR-0380](adr/0380-the-tooltip-row-is-what-ships.md)), and "one module or two"
had been settled in `core-widgets.md` itself a month before anyone noticed. What
is left is where the emoji font lives and who owes its attribution, whether
`goldberry-charts` is an artifact, what "zero new natives" costs (see [Content
modules](#content-modules)), and — recorded in §17.1 and never counted here —
dual y-axes, the word `checkable` doing two jobs, and `masonry`'s missing row. **Pixel-precise wheel deltas left this list** and
have now left the list below it too:
[ADR-0115](adr/0115-a-wheel-reports-a-fraction-and-a-detent.md) settled it as a
difference rather than an agreement — what §2.4 wanted from "pixel-precise" is
scrolling that does not quantize, and a fractional line delivers that without
the mechanism the sentence named — while the entry itself sat on under
*Input, focus and the pointer* for two milestones. That section is **gone**: its
other entry was a cost nobody had measured, and measuring it was the answer.

## Overlays, popups and windows
- **A toast is not announced, and the only thing still missing is the bridge.**
  The widget half is finished: a toast answers [Live#POLITE] and [Role#STATUS] and
  names itself with its own text, which is the claim §7's "live region" is and the
  claim a role and a name cannot make. It matters here and nowhere else in the
  catalog because every other widget is announced when something *happens to it* —
  the focus lands, the pointer arrives — and a toast has no such event: nobody
  focuses it, nobody has to click it, and it is gone in five seconds. So a reader
  that speaks only what is reached would still have said nothing about it on the
  day the bridge landed. What is left is M5's AccessKit bridge and no decision
  from the catalog. —
  [ADR-0225](adr/0225-a-toast-says-it-is-worth-interrupting-for.md),
  [ADR-0177](adr/0177-a-toast-is-a-queue-and-the-stack-is-the-widget.md)

  **On hold —
  [ADR-0440](adr/0440-the-accessibility-bridge-is-on-hold-and-the-semantics-tree-stays.md).**
  The bridge this waits on is not being built and no milestone owns it. The entry
  keeps its prose and its reasoning, which is still the right reasoning; what has
  changed is that the thing at the end of it is not coming on a schedule. The way
  back is a consumer asking, not a date.
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
- **A popup's contents inherit nothing from the widget that opened them, and the
  answer this entry proposed is wrong for the widget it named.** They are the root
  of a second element tree, so no `color`, no `font-size` and no descendant
  selector reaches in. Right for a menu, whose items are a list rather than part
  of a button's subtree. This used to add "a limitation for `tooltip`, which wants
  the styling of the thing it describes, and the answer there is to pass the
  anchor's resolved style in" — and `tooltip` **pins** its typography for a stated
  reason, §1.4's `caption` rank being the one departure in its row that somebody
  had thought about ([ADR-0263](adr/0263-three-numbers-in-one-row-and-nothing-watching.md)).
  Inheriting the anchor's `font-size` would draw a tooltip on a `display`-ranked
  heading at 28px. So the subject stands and the example does not: what is left is
  a `popover` or a `menu` whose *application* wanted a descendant selector to
  reach in, which nothing has asked for. —
  [ADR-0263](adr/0263-three-numbers-in-one-row-and-nothing-watching.md),
  [ADR-0103](adr/0103-a-popup-is-a-second-tree-in-a-second-window.md)
- **60 ms is how long a focus-lost is disbelieved for, and it can now be told
  otherwise.** Long enough to cover the focus-lost/focus-gained pair that opening
  a popup produces, short enough that nobody sees a menu over another application.
  It is still **one default covering every driver** and it cannot be derived — the
  gap between the two events is the compositor's own scheduling and nothing
  reports what it will be — so what changed is that `-Dgoldberry.popup.settle=250`
  overrides it without a rebuild, clamped to 1–2000 ms because a delay of zero
  acts on the first of the pair every driver sends and is the exact bug the delay
  exists for. A driver that needs the flag will still *look* like a menu that
  closes as it opens until somebody sets it. —
  [ADR-0144](adr/0144-a-popup-goes-away-when-the-application-does.md)
## The catalog: specified and unbuilt

### What the last four widgets left behind
- **`Role` has no link and no list.** `link` answers `BUTTON`, and `steps`,
  `timeline` and `breadcrumbs` answer `GROUP` over `ROW`s, each with the reason
  written on it: a role nothing consumes is a value written for a bridge that does
  not exist. The AccessKit bridge is where the words arrive. —
  [ADR-0346](adr/0346-a-link-is-a-word-and-the-desktop-opens-the-rest.md)

  **On hold —
  [ADR-0440](adr/0440-the-accessibility-bridge-is-on-hold-and-the-semantics-tree-stays.md).**
  The bridge this waits on is not being built and no milestone owns it. The entry
  keeps its prose and its reasoning, which is still the right reasoning; what has
  changed is that the thing at the end of it is not coming on a schedule. The way
  back is a consumer asking, not a date.
### `text-input`, and what §4 still owes
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
- **`isModal` has one consumer**, which is one fewer than a mechanism should
  have. `sheet` is the plausible second, and it is not built. **A `wizard` is
  not**: §6 makes its content a focus-scope, and a wizard written inline that
  trapped the keyboard and the pointer would lock the window around it. It is
  tested in `:core` against bare widgets rather than through `dialog`, so the
  second one finds a mechanism rather than a dialog-shaped hole. —
  [ADR-0176](adr/0176-a-dialog-is-a-widget-and-showing-one-is-not.md),
  [ADR-0356](adr/0356-a-connector-grows-from-where-you-were-and-an-entry-has-a-marker-slot.md)

  **On hold —
  [ADR-0440](adr/0440-the-accessibility-bridge-is-on-hold-and-the-semantics-tree-stays.md).**
  The bridge this waits on is not being built and no milestone owns it. The entry
  keeps its prose and its reasoning, which is still the right reasoning; what has
  changed is that the thing at the end of it is not coming on a schedule. The way
  back is a consumer asking, not a date.
- **Four widgets announce what they are and cannot say what they hold.**
  Each has a specification sentence with two halves and only the first is built.
  `code-input` is "a single textbox with the whole code as its value" —
  `Role.TEXT_FIELD`, one Tab stop, boxes with no role at all. `calendar` is "grid
  with each cell's full date as its name" — `Role.GRID`, cells that are parts.
  `date-picker` is "combobox owning a grid, with the formatted date as its value
  text" — `Role.COMBO_BOX`. `color-picker` is "combobox with the hex as its value
  text", and it gets *half* of that one: its closed swatch is a `Role.BUTTON`
  whose accessible **name** is the hex, which is as close as a name can come to a
  value. The second half of all four needs the same thing and there is nowhere to
  put it: `Semantics` is a role, a name and a liveness, with **no value channel
  and no per-cell channel** for any widget. So this is the AccessKit bridge's
  entry rather than any of theirs, and the four are named because they are the
  controls whose specifications spent a sentence on what they would say. M5. —
  [ADR-0276](adr/0276-a-plane-is-hsv-and-the-hex-is-the-value.md),
  [ADR-0274](adr/0274-a-calendar-is-told-what-day-it-is.md),
  [ADR-0273](adr/0273-a-code-is-a-string-and-the-boxes-are-a-drawing.md)

  **On hold —
  [ADR-0440](adr/0440-the-accessibility-bridge-is-on-hold-and-the-semantics-tree-stays.md).**
  The bridge this waits on is not being built and no milestone owns it. The entry
  keeps its prose and its reasoning, which is still the right reasoning; what has
  changed is that the thing at the end of it is not coming on a schedule. The way
  back is a consumer asking, not a date.
- **A trail is not a landmark, and a crumb is not a link.** §6 gives
  `breadcrumbs` "navigation landmark containing links, current page marked", and
  `Role` has neither a landmark nor `LINK`: the row answers `Role.GROUP` — "a
  boundary with content in it and no better word" — and the crumbs answer
  `Role.BUTTON`, which is true of what pressing one does and silent about what it
  *is*. The third of the three, "current page marked", **is** built, through
  `:checked` and the accessible name.
  Filed rather than guessed at, for the reason the two entries below are: a role
  nothing can consume is a constant written for a bridge that does not exist, and
  adding `LINK` and a landmark now would make this gap look closed. M5. —
  [ADR-0306](adr/0306-the-last-crumb-is-where-you-are.md)

  **On hold —
  [ADR-0440](adr/0440-the-accessibility-bridge-is-on-hold-and-the-semantics-tree-stays.md).**
  The bridge this waits on is not being built and no milestone owns it. The entry
  keeps its prose and its reasoning, which is still the right reasoning; what has
  changed is that the thing at the end of it is not coming on a schedule. The way
  back is a consumer asking, not a date.
- **A slider with two axes has no role, here or in ARIA.** `color-picker`'s plane
  answers `Role.SLIDER`, which is true as far as it goes — a control whose value
  you move continuously — and says nothing about the second axis. `GROUP` is "a
  boundary with content in it" and a plane has none; `GRID` promises cells
  addressed by row and column, which is the one thing a continuous plane is not.
  Filed rather than guessed at: the answer is probably a role *and* a second value
  channel, and the shape of that depends on the AccessKit bridge nothing has
  built. M5. —
  [ADR-0276](adr/0276-a-plane-is-hsv-and-the-hex-is-the-value.md)

  **On hold —
  [ADR-0440](adr/0440-the-accessibility-bridge-is-on-hold-and-the-semantics-tree-stays.md).**
  The bridge this waits on is not being built and no milestone owns it. The entry
  keeps its prose and its reasoning, which is still the right reasoning; what has
  changed is that the thing at the end of it is not coming on a schedule. The way
  back is a consumer asking, not a date.
- **There is no third text rank, and one was invented and taken back out.** A
  tour's step counter wanted something quieter than `--gb-text-muted`;
  `--gb-text-subtle` was added, resolved to `nord3`, and produced a counter
  nobody could read on `nord1` — §1.2's contrast floor applies to metadata as
  much as to prose, and the Nord palette has nothing between muted and the border
  colour. The size carries the demotion instead. A real third rank would need a
  colour the palette does not contain. —
  [ADR-0121](adr/0121-a-tour-is-a-veil-and-a-sequence.md)
- **A `tour` cannot find the viewport its target is in — read against the code,
  and it stands.** §5 asks it to scroll a target into view, and `Stop` takes a
  `ScrollController` the application supplies. Discovering it means walking from
  an element to its nearest scrolling ancestor. `BuildContext.findAncestorState`
  looks like the answer and is not: it walks up from the element being **built**,
  and what a tour needs is a walk up from the **target it names** — a different
  question, and one the tree offers no way to ask. ADR-0120 avoided the same wall
  by turning the question around; here there is nothing to turn around, because
  the tour is not the thing being revealed. —
  [ADR-0268](adr/0268-a-tour-card-says-how-tall-it-came-out.md),
  [ADR-0121](adr/0121-a-tour-is-a-veil-and-a-sequence.md)

  **Closed —
  [ADR-0439](adr/0439-a-viewport-is-found-by-walking-up-from-the-target.md).**
  Both premises are true and the conclusion is false, which is why re-reading it
  twice did not catch it. `Element` **implements** `BuildContext`, so
  `findAncestorState` walks up from whatever element it is called on rather than
  from the one being built; and `Host.anchor(id)` already returns a region whose
  `owner()` is that element — the tour was calling it on every build for the
  rectangle and discarding the owner. `ScrollScope.enclosing(target)` is the
  walk. ADR-0120 had written down that `findAncestorState` "stays, because it is
  how an application-level `scrollIntoView` from *inside* a scroll view reaches
  the viewport", which is this call, kept for it, three hundred decisions
  earlier. `Stop.within(controller)` survives for the application that means an
  **outer** viewport, since the walk finds the innermost.
- **A scrollbar's thumb stops being proportional on a very long document.** It is
  floored at 24px, so past about four screens the thumb no longer says how much
  is visible — only that there is a lot. The trade every scrollbar makes, named
  here because it is a place the widget knowingly stops telling the truth. —
  [ADR-0117](adr/0117-a-widget-may-be-told-what-it-measured.md)
- **`Measured` has several consumers whose reason is a *sibling's* geometry**, which is new:
  a toast stack banks how tall each toast came out so that it can move the survivors
  by the height of the hole when one goes ([ADR-0178](adr/0178-a-stack-closes-its-own-hole.md)).
  Every other consumer reads its own box. It obeys the third rule by construction for
  the same reason the scrollbar does — a reflow is a `transform`, so the box it moves
  is laid out where it always was.
- ~~**`margin` is not in §8's subset**, which `tab-new` found after `border-bottom`
  and `currentColor`.~~ **It is now**
  ([ADR-0311](adr/0311-margin-is-room-outside-and-auto-is-the-half-that-mattered.md)),
  and this entry closed the case on the wrong evidence. It was right that
  `tab-new` stopped wanting one — what that widget reached for was a way to sit
  somewhere other than the top of its row, which `align-self` answers
  ([ADR-0244](adr/0244-a-child-may-say-where-it-sits.md)) — and wrong to conclude
  from it that the property had no consumer, because `align-self` is the **cross**
  axis. On the main axis a box that wants to centre itself, or to sit at the far
  end of a row its container is not arranging for it, had no spelling at all:
  `justify-content` is the container's decision about every child at once, and a
  `flex-grow: 1` spacer is a box in the tree that draws nothing. `margin: 0 auto`
  and `margin-left: auto` are what those are, and Yoga's binding has had the
  `auto` call since ADR-0029.

  The entry's other half stands and is worth keeping: three properties a widget
  reached for and did not find, all silently ignored, and the subset is right to
  be small. ~~and nothing warns when a
  declaration is dropped.~~ **Something does now, for the toolkit's own sheets:**
  `border-bottom` was written a fourth time, in `table-head`, and drew nothing
  ([ADR-0215](adr/0215-a-property-the-engine-drops-is-a-rule-that-does-nothing.md)).
  `SupportedPropertyTest` resolves every rule the catalog and the showcase ship
  through the real cascade and fails on anything reported as unsupported — so a
  dead declaration is one failure with the property in it rather than a debug
  line among thousands. **And on anything reported as a bad *value*, since
  ADR-0216**: `border-radius: 7px 7px 0 0` and `background: none` were two more
  rules doing nothing, with the property spelled right and the value refused.
  **An application's stylesheet is still on its own**, deliberately: naming
  `backdrop-filter` before it exists must not stop a window opening. (That
  sentence said `box-shadow` until ADR-0310 built it; `backdrop-filter` and
  `letter-spacing` are what is left of §8's unimplemented list.) —
  [ADR-0216](adr/0216-a-corner-is-four-numbers-and-a-lint-reads-values-too.md),
  [ADR-0215](adr/0215-a-property-the-engine-drops-is-a-rule-that-does-nothing.md),
  [ADR-0109](adr/0109-a-tab-arrives-and-departs-on-the-frame-clock.md)
- **The catalog's specified surface roughly tripled, and most of it is built now.**
  `docs/core-widgets.md` gained twenty-one widgets and four options in one pass —
  `link`, `affix`, `segmented`, `date-picker`, `time-picker`, `color-picker`,
  `code-input`, autocomplete on both `text-input` and `select`, tree-select, `collapse`,
  `carousel`, `statistic`, `skeleton`, `breadcrumbs`, `steps`, `wizard`, `message`,
  `tour`, `tree`, `calendar`, `timeline`, and `button`'s `outlined` / `square` /
  `circle` / `float` options — each with a `design-system.md` §3 metrics row and, where
  it moves, a §3.1 row. §5 requires a spec **and** a metrics row **and** gallery
  coverage before code, in that order: they had passed two gates of three, and the
  third is what "built" means.

  **This entry said "none of it is built", then "one of them is built now", then
  "four widgets and four options are left" — and now none are.** The last four
  went in on 2026-09-17: `link`
  ([ADR-0346](adr/0346-a-link-is-a-word-and-the-desktop-opens-the-rest.md)),
  `steps` and `wizard`
  ([ADR-0344](adr/0344-a-list-of-steps-writes-where-each-one-stands.md)),
  `timeline` ([ADR-0345](adr/0345-a-timeline-is-a-list-whose-line-goes-on.md)),
  and `button`'s `outlined` / `square` / `circle` / `float`
  ([ADR-0347](adr/0347-an-icon-only-button-is-a-circle-and-float-is-a-place.md)).
  What each left behind is its own entry under *The catalog* below.

  Everything else on it went in: `segmented` first, then `affix`, the three
  pickers, `code-input`, autocomplete on both controls, tree-select, `collapse`,
  `carousel`, `statistic`, `skeleton`, `message`, `tour`, `tree`, `calendar`, and
  `breadcrumbs` last.

  **The way `segmented` went is still the argument for writing them down first**,
  read from the other end: two of its five specified metrics and both of its
  specified transitions turned out to be undrawable in §8's subset, and that was
  found by implementing it rather than by writing it. The point of writing them
  down first is that the arguments are cheap then and expensive later — `message`
  against `toast`, `segmented` against `radio-group`, `code-input` against a
  styled `text-input` are all decisions that would otherwise have been made by
  whoever happened to need one, and none of them was.
- **A virtual list can have its focused row scrolled out of existence.** Wheel
  far from the focus ring and the focused row leaves the window, is unmounted,
  and the router drops it — which is ADR-0180's rule doing exactly what it should
  to an element that has left the tree. Arrow keys are unaffected, because the
  ring asks the viewport to follow it; only pointer-scrolling away and then
  pressing one loses the place. Every recycling list has this unless it pins the
  focused index, and pinning it would keep a row nobody is looking at built for
  ever. —
  [ADR-0213](adr/0213-a-virtual-list-is-two-spacers-and-a-window.md)
- **A `table` focuses rows and not cells.** Right for §10's grid semantics and
  wrong for a spreadsheet, which is a different widget rather than an option on
  this one. Horizontal virtualization is absent for the same reason: it is a
  different arithmetic, and it is worth it past about fifty columns, which is
  past where a table is the right thing to be looking at. —
  [ADR-0214](adr/0214-a-table-is-a-list-with-columns.md)
- **`tree` moved from deferred to specified, and `table` has since followed it**, which changes what M5 owes. ARCHITECTURE
  §17 defers "tables/trees"; `table` still is, because it waits on virtualization, but
  `tree` reuses `list`'s model and item-factory and does not — and `select tree=#true`
  needs it, so the two arrived together.
- **A segmented control fills its parent when nothing gives it a width**, which is new
  and is a real loss of convenience: in a toolbar beside other widgets it takes the
  whole row until an author writes `width`. It buys the travelling indicator, and there
  is no third option under flexbox — content-sized cells cannot be travelled between,
  and a zero basis collapses the bar entirely.
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
- **The toggle does not shrink with a compact density**, and that is
  **answered rather than open** (`docs/widgets-finishing.md`, ADR-0356): §3's row
  carries no compact value for `toggle` where the rows that shrink carry one, so
  the pill staying 36×20 inside a 28-tall row is the specification rather than a
  gap. Kept here because the *screenshots* are what would say whether §1.3 meant
  it. Read off §3 rather than decided: the rows with a compact value carry it in parentheses and the `toggle` row
  does not, so the pill stays 36×20 while the row around it takes `--gb-toggle-height`.
  Whether a 28-tall row holding a 20-tall pill is what §1.3 intends is a question for
  whoever writes the compact screenshots. —
  [ADR-0075](adr/0075-a-gestures-origin-is-the-routers.md),
  [ADR-0074](adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md)
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
## Images, and what the primitive is not
- **`Image.decode` is still synchronous.** A large JPEG is tens of milliseconds,
  and a `canvas` painter that decodes pays it on the UI thread. The `image` widget
  does not: it decodes on a virtual thread through `ImageLoader` (ADR-0358), and
  that is the seam a painter should use too. Nothing has measured a painter that
  needs it.
## Rendering without a window
## Editing text
- **No bidi caret.** `Paragraph.isBidiApproximate` already says the shaping does
  not promise visual order for mixed-direction text, and a caret in it needs a
  walk the toolkit does not have. Latin, Cyrillic and CJK are exact; Arabic and
  Hebrew are approximate in the same way the paragraph is.
- **An `Editor` does not scroll.** It draws where it is told and does not know it
  has been clipped. A canvas with a long document moves its own transform, which
  it is already doing for everything else on it; a *widget* wanting this is
  `text-area`, which has a viewport.
## The clipboard
- **Nothing watches it.** There is no "the clipboard changed" notification, so a
  paste button cannot grey itself out until its menu opens and asks
  ([ADR-0286](adr/0286-a-clipboard-write-is-an-offer.md)). X11 and Wayland both
  deliver ownership changes and Windows has a viewer chain; what is missing is a
  consumer worth the plumbing.
- **No file lists.** `text/uri-list` is bytes like anything else and works today,
  but nothing turns those bytes into paths. **Drag-and-drop is a different
  platform mechanism and is built now**: `Window.onFileDrop` delivers one
  `FileDrop` per gesture, with the paths and the point they landed on
  ([ADR-0330](adr/0330-a-dropped-file-arrives-somewhere.md)). What is still
  unbound there is `SDL_EVENT_DROP_TEXT` — the same shape, and nothing has asked
  for it.

  **`SDL_EVENT_DROP_TEXT` closed —
  [ADR-0408](adr/0408-a-dropped-line-of-text-is-a-dropped-file-in-every-way-but-one.md).**
  "The same shape" turns out to be literal rather than loose: SDL tokenises
  dropped text on `\r\n` and raises one event **per line**, then one shared
  `DROP_COMPLETE` for both kinds — so `TextDrop` carries a list of lines, and
  a test exists specifically to stop the shared completion turning a file drop
  into a text drop. What had blocked it was diagnosed here and is worth
  keeping: the blocker is a missing **constant**, not a missing symbol, so
  adding the enum value fails the *layout probe* rather than the link, and the
  bill is a shim row and an ABI bump on four platforms. ADR-0422 was bumping
  the ABI anyway, so the bill was already paid.
- **No primary selection.** X11's middle-click buffer has its own SDL calls
  (`SDL_GetPrimarySelectionText`) and is unbound: it is one platform's idea, and
  the widgets that would fill it — a text field on X11 — would have to know they
  are on X11.
## Content modules

`docs/content-widgets.md` specifies eleven optional modules; **one of them
exists** — `:html`, whole, with no engine under either half — and none of the
other ten is scheduled while M3 still owes client-side decorations and the rest
of §4. The shape they share is
[ADR-0190](adr/0190-a-content-module-brings-its-own-natives.md) and the summary
is `docs/ARCHITECTURE.md` §11.1. What follows is what each is actually waiting
on, which in four cases is the same thing.
- **Both halves of `goldberry-html` are built, and neither has an engine under
  it.** `:html` ships `Markdown.parse`, `MarkdownHtml`, `markdown-view`, `Html.parse`
  and `html-view`
  ([ADR-0294](adr/0294-a-parser-crosses-the-boundary-once.md),
  [ADR-0295](adr/0295-a-document-is-a-value-and-a-paragraph-is-a-row-of-words.md),
  [ADR-0298](adr/0298-html-is-a-document-and-not-an-engine.md)),
  and what they do *not* do is a short list that mostly has one cause — there is no
  inline layout engine under either, because that is what litehtml would be:
  - **Neither can lay out a line of mixed faces as one shaped run**, so there is no
    justification and no hyphenation. This is the item that *is* litehtml's, and it
    is now **the whole** of what an engine would buy: links, images, task boxes
    (ADR-0300) and text selection
    ([ADR-0301](adr/0301-a-selection-is-geometry-the-frame-already-had.md)) all
    used to be on this list and none of them needed one.
  - **Dragging a selection past the edge of a viewport does not scroll on.** Both
    views select, copy and highlight
    ([ADR-0301](adr/0301-a-selection-is-geometry-the-frame-already-had.md)); what a
    drag to the bottom of a pane does is stop selecting rather than carry the
    viewport with it. The same want a `text-area` has, and neither has it — an
    auto-scroll is a timer plus a clamp, and the interesting part is deciding what
    it does on a touchpad's fractional deltas.
  - **Emphasis is a faux oblique** — `transform: skewX(-10deg)` — because §6.1
    ships two upright faces. A third face is an asset decision rather than a code
    one, and `:assets` is where it would be made.
  - **A hard break inside a paragraph does nothing.** A wrapping row has no widget
    meaning "start a new line here", and a `spacer` with `flex-grow` — the obvious
    trick — makes the line before it look justified.
  - **A table's cells have no rules between them, and a fence does not scroll
    sideways.** Both are the CSS subset: `border` is uniform, so there is no
    `border-left`, and horizontal `scroll` is not in §10 either.
  - **`src="…"` is not a thing on either view.** Reading a file from markup means
    deciding what a relative path is relative to and what a missing one does —
    three answers `Icons` and the stylesheets each needed a resolver for. An
    application reads the file and passes the text.
  - **`<style>` and `style=` are kept in the HTML model and applied by nothing**,
    and neither is a `<script>` run. The cascade an `html-view` is under is the
    application's stylesheets, which is what makes a page follow the theme; an
    author's own colours would fight it (ADR-0298).
  - **A keystroke re-parses and rebuilds the whole preview** (ADR-0296). Right for
    a note in a pane, and the widget count is what bites first on anything longer —
    ADR-0295 put it at roughly one per word. An incremental parse is md4c's to
    offer and it does not; a rebuild bounded by what the viewport shows is the
    `list` virtualization argument applied to a document, and nothing needs it yet.

  **Closed —
  [ADR-0426](adr/0426-a-paragraph-is-one-row-of-words-until-somebody-ends-a-line.md).**
  A hard break is now its own `Words.Piece`, and a paragraph with one becomes
  a column of line rows; with none it builds exactly the single row it built
  before, which is why no `:html` golden moved. The CSS split is the decision:
  `.md-prose` and `.html-prose` are declaration-less paragraph hooks now, the
  row geometry moved to `.md-line` / `.html-line`, and the column carries the
  **same** `0.25em` gap so a typed break and a width break sit at the same
  leading. **A break inside a link deliberately does not split** — one
  `button.link` is one Tab stop and one hover — so it becomes a space in the
  label. Three things the entry could not have said: a *trailing* hard break
  is unwritable in Markdown (md4c strips the two spaces), two in a row come
  from a lone backslash and do survive as an empty line, and a table cell
  provably cannot hold one. `gallery-markdown` moved, because the showcase's
  own sample says "Two spaces at the end of a line / are a hard break" and now
  demonstrates it.
- **A code editor is `goldberry-code`, and that module does not exist.** The
  Markdown screen's editor is a `text-area` in the code face: a caret, a selection,
  undo, the clipboard and an input method. It has **line numbers** now
  (`gutter=#true`,
  [ADR-0331](adr/0331-a-gutter-numbers-hard-lines-at-soft-positions.md)) and a
  seam an application can write shortcuts against (`onEdit`/`edit`,
  [ADR-0332](adr/0332-an-editor-is-handed-the-caret.md)), so `Ctrl+B`,
  list continuation and `Tab`-indent are an application's to write rather than
  impossible. What is still missing here is **highlighting** — Tree-sitter is what
  that waits on, and the fence's language already reaches the model for it to read
  (`CodeBlock.language()`).
- **The export list has no paint surface wide enough for a native
  `document_container`.** `goldberry-html` puts litehtml's C++ container inside
  its own native library because FFM cannot implement a virtual class, and that
  container draws through `libgoldberry`'s exported C symbols. There are twenty
  `bl_context_*` entries and they are the ones the toolkit's own painter needs:
  no gradient, no rounded geometry, and no `bl_context_save` — the symbol file
  says why in its own comment, that there is only ever one clip depth here.
  `content-widgets.md` §1.5 promises linear and radial gradients and
  `border-radius`, and CSS state nests. So the first commit of an *engine-backed*
  `goldberry-html` is a widening of the toolkit's own native surface, reviewable on
  its own, and it is *shared* work: `goldberry-vector` and `goldberry-terminal`
  want the same surface. **Nothing on screen is waiting on this any more**
  (ADR-0298): `html-view` renders through the widget tree, and what an engine would
  add is the inline layout and the text selection above. When it lands it is a
  second renderer over the same model rather than a replacement for one. Statically linking a second Blend2D into the module is the way out
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
- **`goldberry-code` has a consumer before it has a widget, and the consumer now
  exists.** md4c's code fences want a highlighter; `markdown-view` renders a fence
  as plain monospace lines with the language shown above them, which is the
  "renders fences plain" branch — and the language is already in the model
  (`CodeBlock.language()`), so the seam is a real one rather than a plan. So
  `goldberry-html` either depends on `goldberry-code` optionally or keeps
  rendering them plain. The optional-dependency mechanic — a module that improves
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
- ~~**`goldberry-web` is parked, not deferred.**~~ **Built, and not as a module**
  ([ADR-0441](adr/0441-a-web-page-is-a-window-not-a-box.md)). Every word of the
  entry was true about Servo and none of it was about the question: libservo is
  Rust-only against a deliberately unstable API, so the module would indeed own a
  `cdylib` shim and its breakage — and nobody asked whether a page needed an
  engine of this project's at all. `webview/webview` is MIT, is one header, and
  **brings no engine**: it drives the WebKitGTK, WebView2 or WKWebView the desktop
  already has, so neither condition that quarantines a content module applies.
  **This is the fifth entry in this run of work that was wrong about itself**, and
  it is the most expensive kind for the second time: "parked" reads like an answer
  and stopped anybody re-reading it for two milestones.

  What it is *not* is a widget. `webview/webview` cannot render offscreen, so a
  page is always a platform window; and a Wayland session allows neither
  reparenting a foreign surface nor placing a window where a widget is, so a
  `web-view` in a layout would be a box on X11, Windows and macOS and a loose
  window on the default Linux desktop. It ships as §9's second `widget.shell`
  member instead — a value and the call that opens it, `tray-icon`'s shape. CEF-OSR
  stays the documented escape hatch, and is still the only engine that would have
  made a box possible.
## Layout
## Style, colour and motion
- ~~**Nothing in the catalog wears an elevation yet.**~~ **Five surfaces do**
  ([ADR-0312](adr/0312-the-catalog-puts-the-two-new-properties-on.md)): `card` at
  §1.5's level 1 and lifting to level 2 on `card.interactive:hover`, `dialog`,
  `tour-card` and `toast` at level 2, and `affix:affixed` at level 1 with the
  `transition: box-shadow` §1.7's motion table has asked for since before there
  was a shadow. The edges all stay, for ADR-0166's reason: a shadow cast onto
  another card falls on that card's own colour and says almost nothing, where the
  rim says it exactly.

  **`popover`, `menu` and `tooltip` are the ones still without**, and no longer
  because the subset lacks the property. They are drawn in popup windows created
  at the panel's own measured size (ADR-0104), so a shadow — which is drawn
  outside the box that casts it — would fall entirely outside the window and be
  clipped: a run of fills that draws nothing. What it needs is a popup sized to
  the panel *plus* the shadow's reach with the extra transparent, which wants the
  same transparent-popup compositor support the rounded corners are waiting on.
  `--gb-elevation-3` is unused and stays so: it is the level for a thing the
  pointer is dragging, and nothing here is dragged. —
  [ADR-0312](adr/0312-the-catalog-puts-the-two-new-properties-on.md),
  [ADR-0166](adr/0166-a-raised-thing-is-told-apart-by-its-edge.md)
- ~~**Nothing in the catalog uses a margin yet.**~~ **It does**
  ([ADR-0312](adr/0312-the-catalog-puts-the-two-new-properties-on.md)):
  `dialog-actions` writes the top margin §2 asked for instead of the
  `padding-top` that stood in for it, and `tour-card`'s footer lost the `Spacer`
  that pushed Skip away from Back and Next — `margin-right: auto`, pixel for
  pixel the same picture. The showcase's notice bar likewise. **`spacer` is not
  deprecated**: it is a §1 widget an application writes in markup, and a document
  has no stylesheet of its own to put a margin in, so the showcase's status bar
  keeps one on purpose with the notice bar beside it as the comparison.
- **A dropped declaration is reported once, and `align-items: start` is why.** The
  Panels screen filled the console while it scrolled: `start` is CSS's alias for
  `flex-start` and Yoga has only the second, so the declaration was dropped —
  correctly — and reported *per element per style resolution*, which on a moving
  screen is sixty times a second. The typo is fixed and the report is now
  deduplicated by property and value, because a stylesheet is static and a value
  that is not one cannot become one on the next frame. **What changed since**: `start` and `end` are
  taken now, because they are not aliases but **CSS** — Box Alignment Level 3
  defines them and Yoga has only the `flex-` pair, so the toolkit had been
  dropping a declaration the specification allows
  ([ADR-0247](adr/0247-start-is-css-and-flex-start-is-yoga.md)). `left` and
  `right` are still refused, and for a reason rather than an omission: they are
  not the same as `start`/`end` under RTL.
- **A popup's transparent corners need a compositor**, and are unverified on
  Windows and macOS. Without one the flag is ignored and the corners are whatever
  the platform leaves there. The fallback that always works — filling the frame
  with the panel's own colour, for square corners — is kept in reserve. —
  [ADR-0111](adr/0111-a-text-box-is-painted-inside-its-padding.md)
- **`Styled.restyle` is an escape hatch with three callers now, and the honest risk is what goes into it.**
  What a widget writes there is unthemeable and unoverridable — right for a number
  nobody else can compute, wrong for anything else. It has one caller in the toolkit and
  one rule ("only what a stylesheet could not have written"); a second caller that is
  *not* a count is the signal to look at it again.
- **A segment's focus ring lands exactly on the bar's edge.** §2.2's ring is 2px at a
  2px offset and the bar's inset is 2, so the two coincide — legible in
  `segmented-focus.png`, and an accident of two numbers derived separately rather than a
  thing anyone chose. If either moves, look at the image.
- **A generated registry can fail at class-init time now, and only for private
  members.** A `VarHandle` lookup that cannot find its field throws
  `ExceptionInInitializerError` where a direct field reference would have thrown
  `NoSuchFieldError` at link time — the same class of failure with a different
  exception, and both are impossible within one compilation, which is how a registry and
  its model are always built. Recorded because it is the one thing ADR-0098 moved later
  rather than earlier.
## Rendering and performance
- **Opening a long note still shapes all of it, on the frame that opens it.** A
  keystroke into a 500 kB `text-area` costs what a keystroke into a 2 kB one
  costs now, because the text is shaped one hard line at a time and only the rows
  on screen are drawn. The first frame is not: 499 079 characters, 78 to 95 ms
  across runs on this machine, and again whenever the cascade resolves a
  different face. It is irreducible in
  the shape the control has — how far the content scrolls is a fact about every
  line, and nothing knows a line's height without shaping it — so closing it
  means shaping the lines below the fold **off** the frame and filling in the
  scroll range as they arrive, which is a different control and a different
  promise about what the scrollbar means. Nobody has reported it: the downstream
  editor's complaint was the keystroke, and `TextAreaFrameBenchmark` is where the
  number would have to come from first (ADR-0045). —
  [ADR-0388](adr/0388-a-note-is-shaped-a-line-at-a-time.md)
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
- **`Element.update` invalidates a subtree only when the cascade could see the
  change.** ADR-0149 narrowed the *state* path and ADR-0315 narrowed this one: a
  rebuilt widget throws away what is below it when `matchesDiffer` says its
  identity to the cascade moved — its type, its classes or its id — and
  invalidates its own style alone when it did not. What is left is the case where
  the identity *did* move, which still costs the subtree and which nothing has
  measured as a problem. —
  [ADR-0149](adr/0149-a-state-invalidates-what-it-can-reach.md),
  [ADR-0315](adr/0315-a-rebuild-is-not-a-restyle.md)
- **A HUD costs about three shaped paragraphs a frame, and reports the cost as
  its own.** Its readings are strings that change every frame, so no cache keyed
  on the string can hold them. The caption says so rather than hiding it, and the
  ways out are all worse than the disclosure: refreshing the text at 10 Hz would
  need per-frame state a widget cannot have, and excluding the overlay subtree
  from the timings would report a frame the window did not paint. —
  [ADR-0152](adr/0152-the-cascade-looks-at-rules-that-could-match.md)
## Platform, compositor and CI
- **The 60 fps claim is measured on one machine, and closing M1 is a CI job.**
  §16's M1 asks for a styled wrapped paragraph *resized* at 60 fps on Linux,
  macOS and Windows. The budget half is met with 3.9× of headroom
  ([ADR-0047](adr/0047-a-frame-nobody-sees-costs-full-price.md)); the breadth half
  is one VirtualBox VM. **Scheduled at M5** — see [status.md](status.md#m5--hardening) for
  the shape of it. The three things that were missing are **all built**
  ([ADR-0342](adr/0342-a-window-is-resized-from-outside-and-the-run-says-what-it-cost.md)):
  `Window.resize` and `--resize=WxH` walk a window's size from outside,
  `FrameSummary` prints what a run cost at exit, and `showcase.yml` paints 300
  frames while resizing on each runner and fails over `--late-budget`. What is
  missing now is a **run**: that workflow fires on a tag or by hand, there is no
  tag, and the only recorded numbers are 60 frames headless on one machine. The caveat travels with the numbers: GitHub's runners are GPU-less VMs,
  so what this can prove is that three platforms' *drivers* hold the budget, not
  that hardware does. —
  [ADR-0045](adr/0045-a-frame-is-not-a-benchmark-iteration.md),
  [ADR-0147](adr/0147-a-frame-has-a-budget-and-the-build-checks-it.md)
- **A like-for-like Wayland frame measurement is still owed.** ADR-0037's numbers
  — paint 5.10 ms, present 1.92 ms, 7.86 ms median — were taken on **X11**, after
  the Wayland run crashed the compositor, and they are compared against ADR-0031's
  Wayland ones. Nothing in that work made present faster; the driver changed. The
  two rows should not be read against each other until the same frame has been
  measured twice on the same session type. —
  [ADR-0037](adr/0037-what-the-text-path-costs.md),
  [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)
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
  **half closed**: `macos.yml` still links the library and runs the tests without
  ever opening a window, but `showcase.yml` runs the *packaged* image on
  `macos-14` and asserts it painted three frames — so a repeat of this failure
  would now turn a tick red. What that leg cannot catch is anything about
  `gradlew run`, which is the path this bug was found on and the one an
  application author uses. —
  [ADR-0039](adr/0039-macos-needs-the-first-thread.md)
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
  resize edge. The manylinux leg runs CMake directly with no JDK, so `checkToolchain`
  never gets to ask either question, and the drift guard deliberately held that workflow
  only to the packages SDL refuses to configure without. Answering it means reading
  `SDL_VIDEO_DRIVER_WAYLAND` and `HAVE_LIBDECOR_H` out of a container build's
  `SDL_build_config.h` — not another look at the table. —
  [ADR-0082](adr/0082-a-preflight-check-that-cannot-fail-is-not-a-check.md),
  [ADR-0083](adr/0083-on-gnome-wayland-libdecor-is-not-a-fallback.md)

  **Half of this moved while G32 was being closed**
  ([ADR-0325](adr/0325-a-build-says-what-it-can-ask-the-desktop.md)), and what is left is
  now a packaging problem rather than an unknown. Measured in
  `quay.io/pypa/manylinux_2_28_x86_64`: `dbus-devel`, `systemd-devel`, `ibus-devel` and
  `mesa-libEGL-devel` all install and all provide their `.pc` files; `libdecor-devel` and
  `xkeyboard-config` are **in no repository the container has**, so those two cannot be
  fixed by adding a line to the workflow. The superbuild does now read SDL's generated
  `SDL_build_config.h` and cross-checks it against its own probe — for
  `HAVE_DBUS_DBUS_H`, `HAVE_IBUS_IBUS_H` and `HAVE_LIBUDEV_H`, which is the same
  machinery this question asks for pointed at three other defines — and the drift guard
  now also holds `linux.yml` to every package a *capability* depends on. Extending both
  to `SDL_VIDEO_DRIVER_WAYLAND` and `HAVE_LIBDECOR_H` is the remaining work, and the
  honest form of it is probably a `Capability.WINDOW_DECORATIONS`, since the answer for
  the container may be "it cannot" rather than "install this".

  **Closed —
  [ADR-0422](adr/0422-what-sdl-compiled-is-the-fact-and-decorations-are-a-capability.md).**
  It guessed the honest form right: `Capability.WINDOW_DECORATIONS` and
  `Capability.WAYLAND`, warned about rather than required, because
  `libdecor-devel` is unavailable in the release container and a `REQUIRED`
  probe there would produce no library at all. What it did not anticipate is
  that *extending both* was the wrong move. The existing pattern is a
  pkg-config prediction confirmed against SDL's generated header, and SDL
  decides the whole Wayland driver with one check over five specs plus a
  scanner binary — so a prediction narrower than that reports "present" where
  SDL reports "absent", and the cross-check then fails a build that was fine.
  These two are therefore **read** out of `SDL_build_config.h` and never
  predicted, which is strictly better where it is available and is why a
  header SDL did not generate now reports both bits absent rather than
  carrying on. `WINDOW_DECORATIONS` is also deliberately narrower than it
  sounds: it is libdecor at build time and says nothing about ADR-0084's
  plugin, because a bit that was set on the exact machine where the bug is
  would be the worst possible value.
- **No CI leg exercises Wayland.** `showcase.yml` runs under
  `xvfb-run`, which is X11, where the window manager decorates the window and libdecor
  is never reached — which is why two consecutive decoration bugs shipped without a
  single red tick. A Wayland leg needs a headless compositor in CI (`weston
  --backend=headless` or `sway --headless`), which is a job nobody has written yet. —
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
- **CI is green, and the fixes that made it so were written blind.** Nine causes on
  Windows and macOS were diagnosed from runner logs and fixed on a Linux machine; all
  passed at `fd36169a` and `d478ecfe`. What that leaves: no machine here can run a
  Windows or macOS test before a push, so a platform-specific regression is caught by
  the Snapshot rather than locally. The annotations make that cheap to read, not free.
  — [ADR-0338](adr/0338-a-red-run-says-why-in-public.md)
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
- **The publishing chain has never run against Central.** Everything up to the upload
  is built and was rehearsed locally with stand-in libraries; what cannot be done from
  the repository is Central's side — the `io.github.digitalsmile` namespace, **snapshots
  enabled for it** (off by default), a user token, a GPG key on a keyserver, and the
  four secrets. Until then every snapshot run rehearses into `mavenLocal` and says so.
  `docs/releasing.md` is the list. —
  [ADR-0334](adr/0334-central-is-fed-once-per-run.md)
- **The macOS and Windows native showcases are built from unreviewed traces.** The
  checked-in reachability metadata was traced on linux-x64 and is reviewed as source;
  CI traces the other two headlessly before building, for 120 frames, and uses what it
  saw. A screen that run never reaches can lack a registration and fail when opened.
  Diffing the first CI traces against the checked-in file says whether per-platform
  traces are needed at all; if they are, they belong in the repository beside the
  Linux one. The `macos-14` runner's 3 cores are the likeliest place for the build to
  be slow (2.27 GiB peak and 1 min 23 s on 8 Linux threads). —
  [ADR-0337](adr/0337-the-native-showcase-is-built-on-every-platform.md)
- **A stale Linux trace is found by a native build, not before it.** The foreign
  calls no longer depend on the trace at all — every holder and every upcall owner is
  registered from the bindings, and `ForeignSurfaceTest` holds the owner list to the
  sources that call `upcallStub` (ADR-0339). What the trace still carries is
  reflection and resources, and a screen the run never opened can still lack a
  reflective registration; with the showcase built only on a tag or by hand
  (ADR-0340), that is found later than it was, on the release build. —
  [ADR-0339](adr/0339-a-foreign-call-is-registered-because-it-exists-not-because-a-run-reached-it.md)
- **An application still adds its platform's natives jar by hand.** The `goldberry`
  umbrella cannot pick `goldberry-natives:<v>:linux-x64` for the consumer's platform —
  a POM has no way to — so the BOM lines up its version and the classifier is the
  application's. A Gradle plugin, or module-metadata variants keyed on OS and
  architecture, would close it. —
  [ADR-0336](adr/0336-one-dependency-to-start-from-and-a-bom-to-line-up-the-rest.md)

  **Narrowed —
  [ADR-0438](adr/0438-a-jvm-consumer-carries-no-platform-so-a-variant-has-nothing-to-match.md).**
  Half the proposed fix does not work, and it was measured rather than argued.
  **Module-metadata variants keyed on OS and architecture cannot close this**:
  a variant is chosen by matching the *consumer's* attributes, and a plain JVM
  consumer declares no operating system — so it resolves the unattributed jar
  silently, exactly as today but with more machinery behind it. A consumer
  that *does* declare one then fails with an ambiguity, because a variant that
  is missing an attribute is compatible with every value of it, so the
  ordinary `runtimeElements` ties with the platform-specific one. The three
  ways out of that tie are all the consumer's: attributing the shared bindings
  jar (which is not platform-specific), deleting the unattributed variant
  (which breaks every consumer that works today), or a disambiguation rule —
  and those are registered on the **consumer's** schema, where a producer
  cannot put one. So a Gradle plugin is the whole of the answer, which is what
  JavaFX, LWJGL and sqlite-jdbc each ship. It is not built: it is a new
  published artifact with its own release surface, on a publishing chain that
  has never run. What did change is the documented snippet — **all four
  classifiers**, because `NativeLibrary` picks at run time and the
  one-platform form is the one that fails quietly for somebody building on
  macOS for Linux.
- **The release job has never uploaded to a GitHub Release.** ADR-0340 attaches the
  three native images to the tag's draft release with `gh release`; the first `v*` tag
  is its first run, and a manual dispatch exercises everything but that step. —
  [ADR-0340](adr/0340-the-showcase-is-a-release-artifact-not-a-package.md)

## Answered

Kept rather than deleted: each is a trap somebody hit, and the reasoning that got
out of it is usually worth more than the fact that it is fixed.

- ~~**Only a `popover` follows a scrolling anchor; a `menu` and a `select` hold the
  rectangle they opened against.**~~ Following is a property of having been opened
  **by id**, which is `Popover`'s documented shape and the one the entry that
  asked for this named. `Menus` and `SelectState` resolve the anchor to a
  rectangle themselves, because they want a minimum width and a `Fit` as well and
  no `Host.popup` overload takes an id *and* those two. It is one overload's worth
  of work and nothing has asked for it: a dropdown is dismissed by a press
  elsewhere, and the wheel over an open one scrolls its own list. —
  [ADR-0270](adr/0270-a-popup-is-placed-again-when-its-window-moves.md),
  [ADR-0145](adr/0145-a-dropdown-is-as-wide-as-what-it-drops-from.md)

  **Closed —
  [ADR-0432](adr/0432-a-menu-is-anchored-by-the-name-it-was-opened-with.md).**
  The overload exists — `Host.popup(content, anchorId, placement,
  minimumWidth, fit)` — and `Menus` opens by name through it. **The entry is
  wrong about `SelectState`**: a `select` has no id to be anchored by,
  `SelectField` is `Located` and takes its rectangle from the frame, and
  ADR-0119 explicitly rejected generating one because two unnamed selects in a
  window would then depend on that generation being unique. So only `Menus`
  was a customer and `select` still does not follow. The entry's own "nothing
  has asked for it" stands as the value of this record on its own; what it is
  really for is being the prerequisite of the entry below.
- ~~**A popup whose anchor scrolls out of sight follows it out of sight.**~~ Now that
  a `popover` travels with its anchor, an anchor scrolled past the top of its
  viewport takes the popup with it, and the placement clamps it to the work area
  rather than dismissing it — so a menu can end up pointing at a widget that is no
  longer drawn. The region carries the clip that would answer "is it still
  visible", so the mechanism is there; what is missing is a decision about what
  should happen — close it, hide it, or pin it to the viewport's edge — and
  nothing has asked for one yet. —
  [ADR-0270](adr/0270-a-popup-is-placed-again-when-its-window-moves.md),
  [ADR-0114](adr/0114-a-clip-is-a-rectangle-the-painter-carries.md)

  **Closed —
  [ADR-0433](adr/0433-a-popup-whose-anchor-leaves-goes-with-it.md).** The
  decision is **close**, and the other two were rejected for reasons worth
  keeping. *Pin* is the only one that makes the toolkit lie: a menu parked at
  the viewport's edge points at whatever row scrolled up to meet it and the
  user cannot tell. *Hide* leaves a popup holding the keyboard, so `Down`
  moves a selection nobody sees and `Enter` runs a command nobody chose. Close
  costs the in-progress interaction and nothing else, which is the one failure
  a user can see and undo. The threshold is intersection rather than
  containment, and closing takes the popups opened after it, since a submenu
  anchors to a rectangle inside its parent. **The region's clip answers only
  half the question**, which the entry assumed was the whole of it: a row
  scrolled fully away is still reported — ADR-0114's empty-clip stop is about
  a subtree's *own* clip — and a box with no clipping ancestor sits under an
  infinite clip, so the predicate is the clip *and* the window's rectangle.
  One shipped behaviour changed: ADR-0270's `followsAScrollingAnchor` asserted
  a menu travelling with an anchor that had left the window entirely, which is
  the picture this record calls wrong.
- ~~**A `masonry`'s column count is a number and not a breakpoint, and what stops
  it is the spec gate rather than the mechanism.**~~ Two columns at 1200px are two
  columns at 720px — half as wide and twice as tall — because the count is a
  constructor argument and no selector can count columns. This used to say that
  "as many columns as fit at a minimum width" is "a layout pass that reads its
  own width, which is the loop ADR-0196 built the last-frame read to avoid", and
  that reads the record backwards: ADR-0196 *is* the last-frame read, `masonry`
  already banks every card's height through `Measured`, and reading its **own**
  width is the same door one step over. `Measured`'s third rule holds for it too,
  with one caveat worth stating — a column count changes the masonry's *height*
  and not its width, so the number is stable under the thing it causes **for a
  masonry whose width comes from its parent**, which is every one in the
  showcase and not every one imaginable. What actually blocks it is that
  `masonry` **has no row in `docs/core-widgets.md` at all** — it is named once,
  as what the showcase's screens are made of — so §5's spec-then-metrics-then-
  gallery gate has nothing to have passed. —
  [ADR-0222](adr/0222-a-showcase-is-a-window-a-bar-and-seven-screens.md),
  [ADR-0196](adr/0196-a-masonry-is-a-layout-that-reads-last-frame.md)

  **Closed —
  [ADR-0436](adr/0436-a-column-count-is-a-width-the-window-does.md).**
  `min-column-width` is built, exclusive with `columns`, defaulting to 320,
  with the wall's own width read through `Measured` from `MasonryBox` — and
  the resolved `gap` read with it, because `n` columns need `n` minimums
  **and** `n−1` gaps and counting without them over-counts at every boundary.
  It settles in **3 passes** worst case, which matters exactly: `Offscreen`
  measures twice and paints the third, so a responsive wall is photographed
  settled with nothing to spare. **The spec row written for this task was
  wrong about shrink-to-fit** and has been corrected: `masonry-column` is
  `flex-basis: 0`, so a wall with no definite width measures zero whatever its
  count is and the count is independent of itself by construction — degenerate
  and stable, and identical with a fixed `columns`, so it is a pre-existing
  defect rather than anything this option introduces. The showcase adopts it
  on the Basic screen only, at 560 rather than 320, because at 320 a
  1168-point wall becomes three columns a third narrower than its cards were
  built for.
- ~~**The gallery goldens cannot see typography at all, and the 150% half is now
  waiting on a decision rather than on a mechanism.**~~ `GalleryGoldenTest` builds
  its renderer with the single-font constructor — which ignores `font-family`,
  `font-size` and `font-weight` by design, so that a golden image is not a test of
  whichever Inter is on the machine — so every screenshot draws prose, headings
  and button labels at one size. A screen with no typographic hierarchy looks
  exactly like a screen with one, which is how a screen title and the paragraph
  under it stayed the same 13px with nothing catching it. `ShowcaseTypographyTest`
  asserts sizes through the cascade instead. **The clipping half read as a gap in
  the tests and was a gap in the toolkit**: nothing enforced §1.4's 150% because
  nothing *implemented* it, so there was nothing for an image to be of.
  `renderer.textScale` exists now
  ([ADR-0267](adr/0267-a-text-scale-scales-the-text-and-not-the-layout.md)) — it
  scales the text and deliberately not the boxes, which is the condition §1.4
  asks components to survive. What is left is **what to assert**: since
  `text-overflow: ellipsis` shipped, some cutting is correct, so "no text is
  clipped" is no longer the sentence, and a golden of eleven screens at 150%
  would pin every one of those decisions at once in a picture before anybody had
  taken them. —
  [ADR-0267](adr/0267-a-text-scale-scales-the-text-and-not-the-layout.md),
  [ADR-0118](adr/0118-a-popup-that-does-not-fit-scrolls.md)

  **Closed —
  [ADR-0435](adr/0435-a-150-percent-check-is-a-rule-not-a-picture.md).** The
  assertion is a **rule**, not a picture, and it is differential between 100%
  and 150%: no line is cut without something asking for the cut, and no box
  overruns its container. Three candidates were rejected, one of them by
  measurement — *"every ellipsis at 150% was reachable at 100%"* is backwards
  and false on the corpus, since HTML gains one correct marked cut and
  Markdown two. **The entry's premise is also wrong in a way that would have
  made the picture worthless**: `renderer.textScale` exists but does not reach
  the gallery, because `WidgetRenderer`'s one-font constructor discards the
  style the scale is applied to — a 150% golden taken the way the gallery's
  are taken would have photographed the 100% tree and passed for ever. The
  audit opens a book instead, making it the first check here to lay the
  gallery out with real `font-family`, `font-size` and `font-weight`.
  `OverflowWatch` answers half: its noise is a fact, its silence is not,
  because the walk is gated on the **root** node's `hadOverflow`. **The
  gallery does not survive 150% today** — the overruns are carried as a named
  ratchet, and the five the narrow Basic screen had are already gone, removed
  by `masonry`'s responsive columns rather than by anything aimed at them.
- ~~**`Measured` is a door every widget can now open and almost none should.**~~ A
  widget that sizes itself from last frame's measurement lags its own content,
  and one that does so in a way that changes the measurement never settles.
  Nothing enforces the rule that keeps it safe — read geometry to interpret an
  input or to draw something that cannot affect layout, never to decide a size —
  and the scroll view obeys it by construction rather than by check. —
  [ADR-0117](adr/0117-a-widget-may-be-told-what-it-measured.md)

  **Closed —
  [ADR-0420](adr/0420-the-measured-rule-is-checked-by-a-fixed-point.md).**
  `Settled`, a harness that drives the real frame loop to a fixed point and
  fails on oscillation, now holds six consumers to the rule. A *runtime* check
  was considered and refused, because it cannot tell an oscillation from a
  resize drag. **There are eleven consumers now, not one**, and the
  interesting number is how many actually feed back: masonry, scroll and
  split-pane settle in 2 passes; table, text-area and text-input in **1**,
  meaning they show no layout feedback at all — so the harness also asserts
  that a consumer was reached, which is what caught `toast` placing no box in
  a windowless harness and passing vacuously. `toast`, `tour`, `image` and
  `IconSheet` stay uncovered, each with its reason written down.
- ~~**A row's focus name still collides between two unnamed lists.**~~ `host.focus`
  takes a name global to the window, and `list` scopes its rows by the list's own
  `id` — which settles it wherever an application named one, and leaves the case
  of two lists, both unnamed, holding an item with the same identity. `tree` has
  the unscoped version of the same thing. What would close it properly is a focus
  name that is relative to a subtree, which the router has no notion of. —
  [ADR-0212](adr/0212-a-list-owns-the-models-a-tree-borrowed.md)

  **Closed —
  [ADR-0437](adr/0437-a-focus-name-resolves-in-the-composite-the-keyboard-is-in.md).**
  `PointerRouter.focusById` resolves inside the enclosing `focus-scope` chain
  before falling back to the window. **The entry says the router "has no
  notion" of a subtree and it does** — `enclosingScope`, used for traversal
  and never for resolving a name — so the fix is about eight lines and no
  published API moved. `focus-scope` is the right naming boundary for a reason
  that is not a coincidence: the elements a composite manufactures names for
  are exactly the ones its arrow keys rove over, because a row gets a name *so
  that* `End` can reach it. The entry also understates `tree`, which is not
  merely "the unscoped version" — a **named** tree collided too, where a named
  list was already settled by its id prefix. One residue is written down
  rather than fixed: a virtualized unnamed list's not-yet-built row is not in
  its own scope to be found, so the cross-frame retry can still reach another
  list's row for one frame.
- ~~**`SelectList` is in the wrong package.**~~ It now has two callers, which is what
  moved `Option` into a package of its own; it stayed put because the CSS type it
  carries is `select-list`, so moving it renames a type in every stylesheet and
  every golden rather than editing one file. Autocomplete itself reaches markup
  through `suggestions=` and `options=` (ADR-0367). —
  [ADR-0182](adr/0182-a-select-may-hold-more-than-one.md)

  **Closed — [ADR-0417](adr/0417-a-css-type-is-a-string-not-a-package.md).**
  **The reason this entry sat is false.** `cssType()` returns the string
  literal `"select-list"`; nothing derives a CSS type from a class's package
  or simple name, so the move renamed nothing in any stylesheet, golden or
  test — every `select-list` in `controls.css`, the `select-list-dark` golden
  name and `SelectTest`'s assertion are byte-for-byte unchanged. Eight Java
  files moved and that was all. The *other* half of ADR-0182's note was true
  and was the real defect: the class documented itself as "not constructible"
  while sitting public in an **exported** package, so the new package is not
  exported.
- ~~**A slider maps the pointer over the track's full width**~~, so at the extremes the
  thumb's centre is up to 8px from the finger. Mapping over the *travel* needs the
  thumb's width, which is the stylesheet's and not the widget's. The mapping is
  monotonic and reaches both ends exactly. **The door this entry named is open and
  a different one is shut**: "a widget being told a resolved metric" is
  `Paints.Context.length` and has been since
  [ADR-0251](adr/0251-a-widget-may-read-a-token-and-a-nested-scroller-is-named.md)
  — but it is a **`render`-time** read, and the pointer arrives at `onPointer`
  where there is no context to ask. `scroll` solved exactly that by banking the
  number into its `State`; a `Slider` is a `record` with nowhere to bank one, so
  closing this means making `slider` stateful. That is still a bigger change than
  8px, and it is now a different sentence. The **tick marks do not
  have this problem**: their inset is half a thumb, written in the stylesheet beside the
  thumb's own width, so a mark and the thumb agree exactly while the finger is the thing
  that is up to 8px out. — [ADR-0080](adr/0080-a-value-is-measured-along-a-part.md),
  [ADR-0079](adr/0079-a-continuous-value-is-placed-by-ratio.md)

  **Closed —
  [ADR-0430](adr/0430-a-slider-maps-the-pointer-over-its-travel.md).**
  `slider` is stateful on `scroll`'s arrangement — `Slider` (record) builds
  `SliderControl` (the CSS type), and `SliderState` banks
  `--gb-slider-thumb-size` read at `render` for `onPointer` to use. The
  mapping is over the travel. The entry was right about everything including
  the tick marks, which `SliderGeometryTest` had been asserting all along and
  which pass untouched. One cost it could not have known: **there is no
  `calc()`**, so the thumb's `border-radius` and `slider-ticks`' inset stay
  hand-maintained halves of the new token, held together by a test rather than
  by arithmetic.
- ~~**An indeterminate bar turns where it should run off the edge, and that is now
  a choice rather than a limit.**~~ `progress`'s indeterminate sweep travels
  there-and-back within its track because the off-the-edges drawing — the more
  common one — needs the bar clipped at the track's edges. This entry said nothing
  clipped; `overflow: hidden` has shipped since ADR-0114, so the drawing is
  available. What is left is a design decision about a shipped animation rather
  than a missing mechanism — and it is the **only** thing left on ADR-0235's list,
  now that the label half has been built ([ADR-0255](adr/0255-a-label-that-does-not-fit-is-cut-not-wrapped.md)). —
  [ADR-0235](adr/0235-a-cut-label-needs-nowrap-not-text-overflow.md)

  **Closed —
  [ADR-0418](adr/0418-the-indeterminate-bar-runs-off-both-edges.md).** The
  sweep crosses and leaves — −100% to 333% of the bar's own width — with
  `overflow: hidden` written in `controls.css` rather than forced in Java, so
  the clip stays the stylesheet's. Two goldens moved and were re-blessed;
  `progress-determinate`, `progress-light`, `progress-reduced` and both
  spinners did not, which is the evidence the clip costs the other drawings
  nothing. One new cost, named rather than hidden: `Clip` is a rectangle and
  not CSS's rounded clip, so the track's 2px cap squares off momentarily —
  about 0.86 px² per corner.
- ~~**No `Image.scaled(...)`.**~~ Scaling happens at the blit, which is where the
  destination size is known. A resampled *copy* — for a thumbnail written to disk
  — is a different operation and would need a filter argument that
  `bl_image_scale` has and nothing has asked for.

  **Closed — [ADR-0428](adr/0428-a-resampled-copy-is-not-a-scaled-blit.md).**
  `bl_image_scale` is bound as `BlendScaledImage`, mirroring
  `BlendDecodedImage`. The filter argument the entry names is real and
  mandatory in C, and the answer is an enum **with** a default: no single
  filter is right both for shrinking a photograph and for doubling a 16×16
  icon, and the wrong choice is silent in both directions, so `Image.scaled(w,
  h)` is Lanczos and `Image.scaled(w, h, Resampling)` is the other four.
  `BL_IMAGE_SCALE_FILTER_NONE` is deliberately unbound — it is the absence of
  a filter rather than one of them.
- ~~**The frame sequence exists twice.**~~ `Launcher.paint` and `Offscreen` run the
  same steps in the same order, and only one of them is the hot path with damage,
  frame statistics, the HUD and the models' refresh woven through it
  ([ADR-0284](adr/0284-a-picture-with-no-window-under-it.md)). Extracting the
  common core is the right refactor and was not taken during a feature: what holds
  them together meanwhile is that every golden image goes through `Offscreen`, so
  a divergence moves a picture.

  **Closed —
  [ADR-0423](adr/0423-one-frame-sequence-shared-by-the-window-and-the-buffer.md).**
  `FrameSequence` in a new non-exported `frame` package holds the element
  tree, the render tree and the router, and both callers use it — every golden
  unmoved, which is the safety net this entry itself named. **The entry was
  wrong that it is one sequence.** There are three orders, not two: a window
  lays out, paints, then captures; a measuring pass lays out and captures
  without painting; the drawing pass lays out and paints without capturing. A
  single "run a frame" method would have needed two booleans about windows. So
  five of the six steps are shared and `draw` is deliberately left out — it
  differs by design (ADR-0072) and has no ordering constraint to protect.
- ~~**No animation strip.**~~ One call, one picture. A caller wanting frame 3 of a
  transition wants to drive the clock between paints, which is an object with a
  lifetime rather than a builder that renders once.

  **Closed —
  [ADR-0424](adr/0424-a-tree-mounted-once-and-photographed-repeatedly.md).**
  `Offscreen.strip(Widget)` returns a `Filmstrip`: a closeable object that
  mounts the tree once and answers `advance(millis)` and `frame()` repeatedly.
  The hard part was what stays alive between frames, which the record states
  rather than leaves to be discovered — every frame gets its own buffer, and
  the pictures survive closing the strip.
- ~~**No reuse and no cache.**~~ Each render builds a fresh element tree and unmounts
  it, so rendering the same document twice does the work twice. A font book can be
  handed in and kept; nothing else can.

  **Closed —
  [ADR-0425](adr/0425-what-a-render-may-keep-and-the-thread-it-may-keep-it-on.md).**
  `Studio` keeps a renderer over a font book and hands out wired `Offscreen`
  builders. **The entry named the wrong thing as the cost.** "A font book can
  be handed in and kept" is true, and the book was not the expensive part: the
  cascade index and the shaping cache were, and both live on the renderer,
  which had no way in. A *result* cache is still refused, and for a reason
  worth keeping — a `Widget` has an `equals`, which is exactly what makes it
  tempting and wrong, because a card closing over a mutable model is equal to
  a stale one.
- ~~**Nothing renders off the UI thread**~~, and nothing says it must not. A render
  touches no window and no backend, so a server thread is probably fine — "probably"
  is why it is written here rather than in the javadoc. What would have to be
  checked first is the shaping cache and Blend2D's own worker pool.

  **Closed —
  [ADR-0425](adr/0425-what-a-render-may-keep-and-the-thread-it-may-keep-it-on.md).**
  Folded into the entry above, because they are one decision: the reusable
  unit is the renderer over a font book, and that is precisely the object that
  must not be shared across threads. **Both suspects the entry named were
  clean.** The shaping cache is per-renderer and already fail-fast; Blend2D's
  pool is process-wide but already degrades to synchronous with identical
  pixels. The real hazard was `Fonts`, which had documented confinement since
  ADR-0044 and enforced nothing — and **the unsafe arrangement was the one
  `Offscreen`'s own javadoc recommended**, "hand over one `Fonts` and keep
  it". That advice is gone and the assert is there; `rendersConcurrently`
  drives eight threads to a pixel-identical result. So the javadoc says it
  rather than saying "probably".
- ~~**No word-wrap-aware `PageUp`/`PageDown`.**~~ The page is ten lines, hard-coded,
  because an editor drawn on a canvas has no viewport to measure. A caller that
  knows its own height moves the caret itself.

  **Closed — [ADR-0410](adr/0410-a-page-is-the-callers-height.md).**
  `Editor.viewportHeight(double)` makes a page `max(1, floor(height /
  lineHeight))`, counted in **visual** lines, with ten kept as the fallback
  and defended against the four alternatives. A page is the screenful rather
  than the screenful-less-one, because the overlap belongs to a scroll and
  this editor's caller owns the scroll. The entry's "a caller that knows its
  own height moves the caret itself" turns out to be more expensive than it
  sounds — it means re-implementing `desiredX` column-keeping and intercepting
  the key before `onKey`, which is the argument for the setter.
- ~~**The headless clipboard is eager.**~~ It keeps the bytes rather than serialising
  on demand, so nothing in a test exercises the *laziness* the platform imposes;
  the upcall path is covered in `:natives` against the real SDL instead.

  **Closed —
  [ADR-0407](adr/0407-the-headless-clipboard-serialises-when-asked.md).** It
  holds a supplier per type now, so a test can assert that nothing was
  serialised until something asked. Every `read` calls the supplier again
  rather than caching, which is the platform's actual contract: a double that
  produced bytes at `write` time rewards an application for serialising per
  copy instead of per paste, and the real clipboard then silently forgives it.
- ~~**A refusal is not modelled anywhere.**~~ Every write returns a boolean and the
  in-memory clipboard always returns true, so the branch an application writes for
  "the compositor declined" is only ever taken on a real desktop.

  **Closed —
  [ADR-0407](adr/0407-the-headless-clipboard-serialises-when-asked.md).**
  `refuseWrites(boolean)` makes the `false` branch reachable from a test, with
  the default behaviour unchanged — a test seam rather than a new policy.
  Worth noting that `ClipboardDataTest`'s own javadoc asserted this gap ("what
  it cannot model is the platform's laziness or a refusal") and was wrong from
  the commit that closed it; ADR-0286's identical sentence is left as written,
  because it was true when it was written.
- ~~**A `text-input` holding a long value shows its end, not its beginning.**~~
  `TextEdit.of` puts the caret at the end and the field keeps the caret in view
  from its first layout, which is what `text-area` did until
  [ADR-0297](adr/0297-an-editor-fills-its-pane-and-a-split-knows-its-own-width.md).
  The fix is the same flag and the same argument; it is not done here because a
  field is not a document and changing two controls on one screen's evidence is how
  a fix becomes a regression somewhere nobody looked.

  **Closed — [ADR-0412](adr/0412-a-field-shows-its-beginning.md).** An
  untouched field shows the head; the caret stays at the end; a press, key,
  edit, composition or focus makes it chase again. No markup attribute — the
  default *is* the decision, and ADR-0326's own argument applies, that a call
  site which must say where the caret goes can forget to. **The entry said
  "the same flag and the same argument" and only the flag was the same**:
  ADR-0326 had already fixed the read-only half and left a test asserting the
  editable tail *on purpose*, so this had to argue against a written sentence
  rather than against silence. It also turned up a real bug the entry could
  not have predicted — the flag was set after `apply()`'s equality check, so
  `End` on an untouched field did nothing at all.
- ~~**An icon larger than its slot overflows it.**~~ An `Icon` is a path built at a
  size and cannot be rescaled at paint time (ADR-0043), so a 20px glyph in a menu's
  16px leading column is 20px — centred now rather than parked in the corner,
  which is the difference between "large" and "misaligned", but still larger than
  the column. An application that wants them to fit builds them at 16, and
  nothing says so at the door. —
  [ADR-0143](adr/0143-a-strip-keeps-its-height-and-an-icon-its-centre.md)

  **Closed —
  [ADR-0419](adr/0419-the-slot-size-is-named-where-the-icon-is-built.md).**
  `Icons.SLOT` and `Icons.bind(String)` name the size at the door, and
  `ItemLead` reports an overhang at **debug**, deduplicated by name, size and
  column — ADR-0394's rule, since a warning firing on every legitimately
  larger icon says nothing. **The entry was misleadingly general**:
  `item-lead` is the *only* slot in the catalog an icon can overflow, because
  every other widget uses `Box.icon`, which sizes the box to the glyph. It
  also corrected a comment that had the override backwards — `.style(style)`
  is applied last, so the CSS width wins, not `Box.icon`.
- ~~**An outer shadow is painted *under* the box, not cut out of it.**~~ CSS knocks
  the border box out of a `box-shadow` so a translucent background does not have
  its own shadow showing through from underneath. The toolkit paints the whole
  shape and relies on the box being drawn on top — and cannot do better today,
  because cutting the hole needs a path clip or a fill rule and the Blend2D
  binding exports neither. The obvious trick is worse than the problem: a
  reversed sub-path under the default non-zero winding *fills* the parts of
  itself the outer shape does not cover, so the inner half of a blur would paint
  a dark ring where it was supposed to erase one. **It is invisible under an
  opaque background**, which is every shadowed surface the design system has, and
  shows under a box mid-`opacity` transition, which fades its shadow by the same
  factor and so darkens itself slightly. What it would take: `BLContextSetFillRule`
  or a path-clip call on the export list, and then one reversed sub-path per
  band. `ShadowPaintTest.throughATranslucentBox` pins the current behaviour, so
  the day that lands there is a test that says the deviation is gone. —
  [ADR-0310](adr/0310-a-shadow-is-a-stack-of-rectangles.md)

  **Closed — [ADR-0427](adr/0427-the-shadow-is-cut-out-of-its-box.md).** Each
  band is now filled together with the border box under
  `BL_FILL_RULE_EVEN_ODD`, so a point inside both is crossed twice and left
  empty — one extra sub-path per band, no extra fill. **The entry offered a
  choice that does not exist**: Blend2D clips to a *rectangle and nothing
  else*, and both `bl_context_clip_to_rect_i/_d` were already exported, so
  there is no path clip to prefer and the fill rule was the only option rather
  than the cheaper one. Its reversed-sub-path warning was right and is why the
  rule matters. "Invisible under an opaque background" turned out to be true
  of *interior* pixels only: twelve goldens moved, every one of them on the
  anti-aliased arc of a rounded corner where the box covers a fraction of a
  pixel and the shadow beneath that fraction is now cut away — the same seam a
  browser has. The fix also retired machinery the entry did not mention:
  ADR-0310's `occluded` band flag has one answer once the hole is cut, so
  culling moved to `ShadowGeometry.coveredAt`.
  `ShadowPaintTest.throughATranslucentBox` asserted the deviation and now
  asserts its absence.
- ~~**One non-text pair is below §1.2's 3:1, and no colour can lift it.**~~ This
  entry said sixteen, and filed them as one thing waiting for one decision.
  Measured against the arithmetic rather than against the sentence they were
  three, and fifteen are fixed
  ([ADR-0258](adr/0258-the-edge-a-measurement-chose.md)). The **twelve control
  boundaries** were a gap in the palette nobody had put anything in: Nord stops
  between `--nord3` and `--nord4`, which measure 1.17:1 and 6.39:1 against
  `--gb-surface-2`, so a palette edge is either invisible or a white ring around
  a dark control — `--gb-checkbox-border` is the midpoint, at 3.17:1 and 3.22:1.
  The **three marks** were `--gb-accent` on `--gb-border`, one pair wearing three
  names, missing by 0.02; the light accent slid to `#5c7ea8` and every other pair
  it appears in moved the same way, so there was nothing to trade against. What
  is left is the **light theme's slider thumb**, and it is not a ramp question:
  the track sits between a white thumb and a dark accent fill, and clearing 3:1
  against both needs its relative luminance at once **≤ 0.300 and ≥ 0.688**. No
  solid colour is both. What has to change is what a light-theme thumb *is* — a
  border round it, or a fill that is not white — which is a sentence
  `docs/design-system.md` §3 does not contain and is the one genuine decision in
  the original sixteen. Twenty-two goldens moved, which is why this had waited. —
  [ADR-0258](adr/0258-the-edge-a-measurement-chose.md),
  [ADR-0240](adr/0240-the-ring-follows-the-accent.md),
  [ADR-0239](adr/0239-a-mark-is-measured-against-the-box-it-is-drawn-in.md),
  [ADR-0088](adr/0088-a-fill-that-carries-text-moves-away-from-it.md)

  **Closed —
  [ADR-0429](adr/0429-a-light-themes-thumb-is-a-disc-with-an-edge.md).** The
  entry called this "the one genuine decision" and framed it as a choice
  between a border and a fill that is not white. **It is not a choice**: the
  groove needs a thumb at luminance ≤ 0.209 to clear 3:1, and the light accent
  fill sits at 0.200, so every fill dark enough to be seen against the bare
  groove vanishes into the half of the track that is filled. Clearing both at
  once needs ≤ 0.083 — `#525252` or darker — which is the "hole punched
  through the control" the theme file already rejects twice for the switch. So
  the fill answers the accent and a 1px `--gb-slider-thumb-border` answers the
  groove, and the dark theme sets it `transparent` because nord6 on nord3 is
  already 6.40:1. **The measurement found a worse pair than the one it went
  looking for**: the light theme's *pressed* thumb was `var(--nord4)`, the
  groove's own colour, at **1.00:1** for as long as the control has existed —
  the sweep had only ever looked at resting fills. It sweeps all three thumb
  states now, one border token covers all three, and `MARKS_BELOW_FLOOR` is
  empty. "Twenty-two goldens moved" was the cost of sliding a *ramp*; an edge
  touches only the thumb, and **two** moved.
- ~~**`button.ghost` has no contrast ratio, and is therefore not checked.**~~ Its fill is
  `transparent` and its hover is a `#ffffff14` wash, so what a user reads depends on the
  surface underneath — there is no single pair to measure. It is left out of
  `ContrastTest` rather than measured against black, which is what ignoring alpha would
  silently do and would score it as passing. The same is true of `--gb-selection`. A
  backdrop-aware check would need the painted frame rather than the cascade, which is a
  different kind of test. —
  [ADR-0087](adr/0087-a-semantic-fill-brings-its-own-foreground.md)

  **Closed —
  [ADR-0431](adr/0431-a-translucent-fill-is-measured-on-the-frame.md).**
  `BackdropContrastTest` renders real trees with the real rasterizer and reads
  the pixels back — five surfaces × four probes × two themes, forty pairs. It
  deliberately does not reimplement `src-over`: a hand-rolled compositor is a
  second opinion and the first one is what ships. **Nothing failed**, worst at
  4.79:1 (`button.ghost:active` on the dark theme's `--gb-surface-2`), so the
  entry's four exclusions were correct to make *and* correct to leave — what
  changed is that an unmeasured pass is now a measured one. Worth recording
  that the check's own first draft reported three failures that were its
  fault, sampling three pixels into an unpadded row and reading ink over fill;
  the flat-region guard that caught it is why the rest is believable.
- ~~**`rem` is the *configured* root size, not the root element's.**~~ What
  [ADR-0242](adr/0242-em-is-the-elements-own-size.md) left: `em` resolves against
  the element's own computed size now, and `rem` still reads
  `CssLength.Context.rootFontSize()`. CSS says the **root element's** computed
  `font-size`, so the two agree unless a root declares one — and recovering that
  inside `ComputedStyle.of` is not possible, because a node is handed its
  *parent's* style and not the root's. It needs a third thing threaded down, or a
  field on the renderer that is only correct after the root has resolved. Nothing
  in the catalog styles a root's `font-size`, so this is exact today. —
  [ADR-0242](adr/0242-em-is-the-elements-own-size.md)

  **Closed —
  [ADR-0416](adr/0416-rem-is-the-root-elements-size-and-the-walk-is-what-knows-it.md).**
  Both halves, split: the root's own `rem` falls out of the two-pass structure
  ADR-0242 already built for `em`, and descendants get it through
  `WidgetRenderer`. **The entry called the renderer-field shape "correct only
  after the root has resolved" as a drawback, and it is the specification** —
  CSS says `rem` *on the root's own* `font-size` refers to the initial value.
  And the claim that this was "not possible inside `ComputedStyle.of`" was
  half wrong: that half was already in reach.
- ~~**A bare `text` with no ancestor setting `color` renders black**~~, which is ADR-0066's
  deliberate `INITIAL` and a trap all the same: the showcase's new gain label was
  unreadable on the dark theme. A control gets away with saying nothing because
  `controls.css` sets `color` on `checkbox`, `radio`, `toggle` and `slider` themselves;
  a primitive does not. The showcase now sets `color: var(--gb-text)` on its root, which
  is what an application should do — but nothing warns one that has not. —
  [ADR-0066](adr/0066-a-weight-is-a-face-and-color-inherits.md)

  **Closed —
  [ADR-0415](adr/0415-the-root-has-no-colour-is-a-fact-about-the-sheets.md).**
  `StyleLint.uncolouredRoot(root)` answers it as a lint asked for rather than
  a warning logged, which is ADR-0257's shape and avoids ADR-0394's trap of
  firing sixty times a second on a correct application. **The entry implies
  the check can read the sheets, and it cannot**: the showcase — the one
  application that does this right — writes `#root` rather than `:root`, so a
  synthetic `:root` probe would have reported the reference application as the
  defect. It takes the root element instead.
- ~~**`StyleElement` documents three nullable members inside a `@NullMarked`
  package and annotates none of them.**~~ `type()`, `id()` and `parent()` each say
  "or null" in their own javadoc and each is declared as a plain `String` or
  `StyleElement`, in a `css` package that *is* marked — so NullAway reads all
  three as non-null. Nothing had noticed because every implementation lived in an
  unmarked package; `StyleLint`'s probe is the first written in a marked one, and
  it cannot say what the interface says. The lint's package is unmarked as a
  result, which is the wrong end to fix it from. Closing it properly means
  annotating the interface, which moves every implementation and every caller —
  and would probably find real nullness bugs on the way, which is the argument
  for doing it rather than against. —
  [ADR-0257](adr/0257-a-diagnostic-is-asked-for-not-logged.md)

  **Closed —
  [ADR-0413](adr/0413-a-signature-that-lies-turns-the-checker-off.md).** All
  three are `@Nullable` now, `Selector.Compound` with them, and `css.lint` is
  marked rather than unmarked — which was the entry's point, that the lint's
  package was the wrong end to fix it from. **The entry predicted "real
  nullness bugs on the way" and there were none.** Six NullAway diagnostics,
  every one already handled at the call site, several with comments explaining
  why. The finding is better than the prediction: a `@NullMarked` package had
  been enforcing a contract nobody believed for long enough that the first
  code physically unable to lie about it was made to leave the room instead.
- ~~**A widget's CSS classes share a namespace with the design system's.**~~ A `hud`
  reading named `display` picked up §1.4's `.display` type rank and rendered at
  28px. Renamed, and nothing prevents the next one: there is no prefix
  convention, no check, and the two sets of names are written in different files
  by different people. —
  [ADR-0153](adr/0153-a-rate-is-counted-a-refresh-is-asked-for.md)

  **Closed —
  [ADR-0414](adr/0414-a-rank-applies-to-anything-so-its-name-is-reserved.md).**
  The convention is **not** a prefix, which is what the entry guessed: both
  namespaces are published vocabulary, and a widget's class is already
  namespaced by its type, so the seven §1.4 rank names are **reserved**
  instead and `ClassNamespaceTest` holds the line. **There were two more
  collisions already in the tree**, which is the entry's "nothing prevents the
  next one" having already happened twice. `TreeRow` minted `heading`, so
  every branch label in every tree drew at 15px/600 in a fixed-height row —
  three goldens moved when it was fixed. `Skeleton.Shape` minted `title`,
  harmless in pixels today and waiting for the first `em`.
- ~~**`Editor` still shapes its whole text.**~~ The `canvas` editing seam from
  ADR-0285 holds one `Paragraph` over everything it is given, which is what
  `text-area` did until ADR-0388. `TextDocument` is exported and is the obvious
  second caller. Nothing has measured an `Editor` over a document, so nothing has
  earned the change. —
  [ADR-0388](adr/0388-a-note-is-shaped-a-line-at-a-time.md),
  [ADR-0285](adr/0285-a-caret-is-the-text-stacks-and-not-a-controls.md)

  **Closed — [ADR-0411](adr/0411-an-editor-shapes-a-line-at-a-time.md).**
  `Editor` holds a `TextDocument` now, and the numbers the entry said nobody
  had taken are taken: a keystroke into 500 kB goes **111.5 ms → 1.4 ms**, and
  the characters shaped go **500 097 → 132** against 129 for a 2 kB document.
  Opening is still proportional (130 ms for 500 kB) and the record says so.
  **Only the shaping half of ADR-0388 ports**: its "drawn a screenful at a
  time" half does not, because the rows in view are the caller's scroll and
  transform rather than the editor's. `gallery-canvas` is byte-identical,
  which is the evidence the change is pixel-neutral.
- ~~**What is still asserted only at 1x, now that the goldens are not.**~~ Every image in
  the golden corpus — about 245 of them now — is drawn again at 2x and 1.5x and checked
  for being the same picture, and `ClipTest`, `TransformPaintTest` and `IconPaintTest` do the same
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

  **Closed —
  [ADR-0434](adr/0434-every-check-sweeps-1-25-and-nothing-sweeps-1-75.md).**
  1.25 is in every `check`'s sweep and **1.75 goes nowhere**, which is the
  opposite of what the entry asked for and is arithmetic rather than thrift: a
  multiplier exercises Yoga's rounding, so what matters is the offsets `k·m
  mod 1` visits — 2 gives `{0}`, 1.5 gives `{0, ½}`, 1.25 gives `{0, ¼, ½,
  ¾}`, and **1.75 gives the same four quarters**, re-asking a question 1.25
  has answered while its magnitude is bracketed by 1.5 and 2. The cost is
  about 4 s of `check` per multiplier repo-wide. Two things the entry could
  not know: `gallery-canvas` misses at 1.25 (1.229% against a 1.200% budget)
  because a hard-edged QR module grid resampled at 5/4 comes back *inverted*
  rather than blurred, so that one image is excluded from the sweep by name
  rather than the budget being loosened on 245 images that meet it with
  tenfold room; and on that same screen a *natural-size* image tile differs
  wholesale between scales where the stretched and cropped tiles beside it
  differ only at their outlines, which has the shape of ADR-0157's bug and is
  now recorded but unswept.
- ~~**Present costs 6.6 ms with no compositor to wait for.**~~ The question ADR-0045 opened
  while closing another. [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md)
  measured present at ~10 ms and concluded "most of it is waiting on the compositor
  rather than copying". Under SDL's `dummy` video driver — no compositor, no display, no
  surface to hand anyone — present still measures **6.6 ms**, essentially the same as
  under Wayland. Whatever that time is, the explanation on record is wrong, and present
  is the largest single term in a frame. —
  [ADR-0031](adr/0031-blend2d-and-the-borrowed-buffer.md),
  [ADR-0045](adr/0045-a-frame-is-not-a-benchmark-iteration.md)

  **Closed —
  [ADR-0409](adr/0409-present-costs-a-tenth-of-a-millisecond-and-the-wait-is-the-rasterizers.md).**
  The entry was right that the explanation on record was wrong and wrong about
  everything else, its own number included. Measured over 300 frames under the
  driver it names, present's median is **0.127 ms** and its p95 0.319 ms — off
  by a factor of about fifty — and it is 0.8% of a frame rather than the
  largest term in one. **The largest term is `end`**, at 10.2 ms: the join
  that waits for Blend2D's workers, with `draw`'s 5.9 ms of queueing in front
  of it, so a frame under `dummy` is rasterization and almost nothing else.
  Two plausible misattributions were ruled out on the way and both were
  already right — the Blend2D join is timed *before* the present boundary, and
  the frame pacer's sleep caps the event wait rather than the painted frame.
  What survives is the qualifier: under `dummy` the frame is rasterized
  straight into SDL's surface and present copies nothing, so 0.127 ms is the
  floor rather than the universal figure. ADR-0031's Wayland number is left
  standing because it cannot be re-measured here — opening a real surface on
  this machine takes GNOME Shell down — and the like-for-like Wayland
  measurement below stays open.
- ~~**Nothing bumps `goldberryVersion` after a release.**~~ Forgetting leaves master
  publishing `2026.1-SNAPSHOT` after `2026.1` is out, which Maven orders below the
  release. A step in `release.yml` that opens the bump as a pull request would close
  it. — [ADR-0333](adr/0333-a-version-is-a-year-and-a-count.md)

  **Closed — [ADR-0421](adr/0421-the-release-line-moves-on-by-itself.md).**
  That is exactly what landed, and the entry named the right shape: a `bump`
  job that `needs: publish`, checks out the default branch and opens
  `bump/<next>`. Two things it did not say. The *arithmetic* is the part with
  a decision in it, and it is a tested value in `build-logic` rather than a
  `sed` — `VersionBump` moves a patch line to its next **patch**, because a
  `release/2026.1` branch bumped to `2026.2` would claim a feature release
  from a maintenance branch, and a `sed` that incremented the last number
  would get that right and get `2026.3` in January wrong. And the guard needed
  no rule of its own: comparing what the default branch declares against the
  tag rules out both a patch tag and a re-run whose bump already merged.
  `CalendarVersion.nextRelease` had been written by ADR-0333 and called by
  nothing until now.
- ~~**PNG is the only format written.**~~ **WebP is written too, 2026-09-17**,
  and losslessly by default: VP8 is worst at the flat colour and hard edges a
  user interface is made of, so `encodeWebp()` is the lossless path and
  `encodeWebp(quality)` is for a photograph. **JPEG is still not written** and is
  what is left of this entry: Blend2D ships a JPEG decoder and no encoder, so it
  means a third codec library or a written one — neither worth it while a
  lossless WebP is a third of a PNG. —
  [ADR-0385](adr/0385-webp-is-written-and-animated.md)
- ~~**Reduced motion is obeyed but not detected.**~~ **Detected, 2026-09-17**, and
  obeyed by a renderer the launcher builds: the settings portal on Linux,
  `SystemParametersInfoW` on Windows and `NSWorkspace` on macOS, each a read-only
  FFM query against a library the process already has, with no native build
  behind any of them. "The desktop does not say" is still an answer and still is
  not an instruction. Asked once per process; a change mid-session waits for a
  restart. —
  [ADR-0383](adr/0383-the-desktop-is-asked-whether-to-move-less.md)
- ~~**Nothing detects the density the user wants.**~~ **Answered: there is
  nothing to detect, 2026-09-17.** Reduced motion was detectable because three
  desktops expose it; density is not, because none of them has such a setting —
  §1.3's compact mode is an application's own decision about its screens, which
  is what this entry suspected and what asking the three platforms confirmed. —
  [ADR-0383](adr/0383-the-desktop-is-asked-whether-to-move-less.md)
- ~~**Layout verification has not yet passed in CI.**~~ **It has, since the first
  all-green snapshot.** The verify legs on all three runners ran the layout probe
  against the downloaded artifact at `d478ecfe`, which is the run
  `book/src/status.md` records as twelve green jobs. —
  [ADR-0016](adr/0016-verify-the-artifact-and-never-skip-the-check.md)
- ~~**AsmJit's W^X handling on Apple Silicon is now reachable.**~~ **Reached, and
  green.** The showcase painted frames on `macos-14` on all three legs and the
  macOS goldens ran, which is the JIT it was a question about doing its work. What
  is still untried is AVX-512, which is a different machine and is on the
  scale-invariance entry rather than this one. —
  [ADR-0338](adr/0338-a-red-run-says-why-in-public.md)
- ~~**`text` has no `style="body"` attribute.**~~ **It has, 2026-09-17**, and it is
  the same class the stylesheet already had a rule for: `style=` names a closed
  vocabulary and is checked, `class=` is the open one and is not. —
  [ADR-0381](adr/0381-a-rank-has-two-spellings-and-one-meaning.md)
- ~~**One frame only.**~~ **Every frame of a GIF, 2026-09-17**, composited under
  the file's own disposal rules, with its delays and its loop count;
  `image.anim.Animation` says which one is showing and holds no clock. An
  animated **WebP** followed a few hours later, once `webpdemux` was linked —
  libwebp composites its own canvases, so the disposal model is upstream's there. —
  [ADR-0382](adr/0382-a-gif-has-the-frames-after-the-first.md),
  [ADR-0385](adr/0385-webp-is-written-and-animated.md)
- ~~**A `tabs` indicator still cannot travel, though `segmented`'s does.**~~ **It
  travels, 2026-09-17**, by being displaced onto the header it is leaving and then
  let go of — a difference between two painted rectangles rather than a position,
  so a strip painted with no router behind it draws exactly what it drew before. —
  [ADR-0377](adr/0377-an-underline-travels-by-being-let-go-of.md)
- ~~**Two key maps.**~~ **One, 2026-09-17**, and there were three by the time it was
  read again. `text.edit.keys` is the table; each editor keeps its own text and
  answers a sealed `EditCommand`, so a key added tomorrow fails to compile in the
  three places that have to answer it. Converging the *editors* is still the
  rewrite it always was. —
  [ADR-0376](adr/0376-one-key-map-three-editors.md)
- ~~**Nothing has a minimum size, so overflow is silent.**~~ **It says so once,
  2026-09-17.** The layout pass asks the root whether its line overran — one
  foreign call on a frame where everything fits — and names what is off the edge
  when it did. A `scroll` viewport and an absolute child are not overruns. —
  [ADR-0375](adr/0375-a-box-that-does-not-fit-says-so.md)
- ~~**`align-content` is still absent.**~~ **It is resolved, 2026-09-17**,
  defaulting to `stretch`, which is what every box in the catalog already did. It
  also gives `SPACE_BETWEEN` and its two neighbours a property that means them:
  they were constants the enum advertised and no declaration could reach. —
  [ADR-0374](adr/0374-wrapped-lines-share-a-cross-axis.md)
- ~~**`flex-basis` is one of two layout properties §8 names and nothing
  resolves.**~~ **It resolves, 2026-09-17**, and `masonry` is the consumer that
  wanted it: `flex-basis: 0` with `flex-grow: 1` is 1/n of a row after its gaps,
  where the inline `width: 100/n %` it replaces was 1/n before them. The collapse
  that took it out in the first place is still real and is now the author's to
  avoid. —
  [ADR-0373](adr/0373-a-column-starts-from-nothing-and-grows.md)
- ~~**`statistic`'s sparkline waits on `canvas`.**~~ **Built on 2026-08-23**, and
  the entry outlived it: `canvas` is in the catalog and the sparkline is the last
  child of the column, exactly as this said it would be.
- ~~**No image cache for `Image.decode` itself.**~~ **Answered rather than built,
  2026-09-17.** `ImageLoader.shared()` is public, bounded and off-thread, and a
  `canvas` painter that decodes by hand can use it — which is what the entry
  itself named as the seam. A second cache *inside* `Image.decode` would be one
  the caller cannot see, cannot bound and cannot clear. —
  [ADR-0358](adr/0358-an-image-loads-off-the-frame-and-is-its-own-size.md)
- ~~**Nothing reorders tabs.**~~ **A strip with `onReorder` does, 2026-09-17.** The
  dragged tab follows the pointer 1:1, which needs no interpolation, and the drop
  asks the application for the new index; the others still jump into place,
  which is ADR-0097's geometry and nothing asked for more. —
  [ADR-0372](adr/0372-a-tab-is-dragged-and-the-strip-asks-where.md)
- ~~**An `affix` pins on one axis.**~~ **On one per axis, 2026-09-17**:
  `edge="top left"`. There was no rule to write about which wins, because each
  axis is its own subtraction. —
  [ADR-0371](adr/0371-an-affix-pins-to-one-edge-per-axis.md)
- ~~**A reveal moves both axes at once.**~~ **By default, still, 2026-09-17**, and
  a caller that means one axis says so with `reveal(self, clip, axes)`. —
  [ADR-0370](adr/0370-a-reveal-can-keep-to-one-axis.md)
- ~~**The circular drag is not built, and §3 offers it.**~~ **It is, opt-in,
  2026-09-17**, with no accumulated angle: a jump across the gap is recognised
  from the knob's current value, and held at the nearer end. —
  [ADR-0369](adr/0369-a-knob-turns-round-its-dial-from-its-own-value.md)
- ~~**A `select tree=#true` has no typeahead.**~~ **It has the tree's own,
  2026-09-17.** The design question was answered by ADR-0209 (visible rows only).
  The defect was elsewhere: a tree moves its typeahead with `host.focus(id)`, and
  a popup's host asked only the window. Focus by name now tries the open popups,
  topmost first. —
  [ADR-0368](adr/0368-a-focus-by-name-reaches-the-popup-it-came-from.md)
- ~~**A `list` is Java, like `canvas` and like autocomplete.**~~ **A document
  places one, 2026-09-17**, and `table` and `tree` the same way: `bind=` names the
  widget the model built, since its factory is code. Autocomplete names the bound
  list its answer lands in, with `suggestions=` or `options=`. —
  [ADR-0367](adr/0367-a-document-places-a-list-it-cannot-describe.md)
- ~~**A tab's content is rebuilt when it is selected again.**~~ **Only by default,
  2026-09-17.** `keep-alive` keeps every shown tab mounted and hidden while another
  is selected, through a new `Styled.isHidden()`: kept, not rendered, not
  focusable. §5's lazy default is unchanged. —
  [ADR-0366](adr/0366-a-kept-tab-is-hidden-not-removed.md)
- ~~**A tab strip scrolls, and has no chevrons at either end.**~~ **It has them,
  2026-09-17**, while it overflows: a `ScrollController` can now say where its
  viewport is and when that changes, which was the missing question. —
  [ADR-0365](adr/0365-an-overflowing-tab-strip-pages-from-its-ends.md)
- ~~**The "always show scroll bars" gutter is not built, and nothing switches
  it.**~~ **Both, 2026-09-17**, in density's shape rather than a settings
  mechanism: `Scrollbars.ALWAYS` is a token stylesheet an application passes to
  `Controls.stylesheets`, and a viewport that finds a gutter pads its content by it
  and stops fading its bars. —
  [ADR-0364](adr/0364-always-shown-scroll-bars-are-a-token-sheet.md)
- ~~**A revealed row lands rather than glides.**~~ **It glides, 2026-09-17**, over
  the overlay duration. The offset goes to the target at once and the viewport
  draws the way there on the frame clock, so direct input never waits; a reveal
  asked mid-glide measures where its row will be. —
  [ADR-0363](adr/0363-a-programmatic-scroll-glides-and-the-offset-is-already-there.md)
- ~~**A `text-area` has no visible scrollbar.**~~ **It has `scroll`'s, 2026-09-17**:
  neither a `scroll` around the text nor a second bar. `ScrollBar` is three numbers
  and two callbacks, and a text area knows all three. —
  [ADR-0362](adr/0362-a-text-area-draws-scrolls-bar.md)
- ~~**A `table` has no column resizing.**~~ **It has, 2026-09-17**, by asking: a
  resizable column's header carries a grip, and a drag asks the application for a
  width in pixels anchored at the width the header last came out as. Not a
  `split-pane` between the headers, which divides one box between two panes. —
  [ADR-0361](adr/0361-a-column-is-resized-by-asking.md)
- ~~**A pinned `affix` is not pushed out by the next one.**~~ **It is, 2026-09-17**,
  without knowing its sibling: an affix stays inside the box it is in, which is
  CSS's rule for `sticky`, so a section's header leaves with its section. The same
  change gave `table` a sticky header, which this entry was blocking. —
  [ADR-0360](adr/0360-an-affix-stays-inside-its-container.md)
- ~~**A `select`'s *field* is as wide as its current value.**~~ **As wide as its
  widest option, 2026-09-17.** The value cell's preferred width is the widest of
  the option labels and the placeholder, shaped in `render`; a stylesheet's width
  still wins. —
  [ADR-0359](adr/0359-a-select-is-as-wide-as-its-widest-option.md)
- ~~**There is no `img` widget.**~~ **There is, 2026-09-17**: `image`, with the
  `object-fit` modes, a natural size that takes part in layout, loading and error
  states and a decode that does not run on the UI thread, which is the list this
  entry said a catalogue entry would need. SVG is still not decoded. —
  [ADR-0358](adr/0358-an-image-loads-off-the-frame-and-is-its-own-size.md)
- ~~**A step's connector fills by colour, not by `scaleX`.**~~ **It grows,
  2026-09-17.** The reason given was that §8's subset has no `transform-origin`,
  and it has had one since ADR-0068. The sentence was copied from an older note
  and never checked against `TransformTest`. The connector is now a track with a
  fill scaled about its start edge. —
  [ADR-0356](adr/0356-a-connector-grows-from-where-you-were-and-an-entry-has-a-marker-slot.md)
- ~~**A `badge` cannot be a timeline's marker.**~~ **It can, 2026-09-17**, from a
  `marker` child that `entry` lifts onto the axis. The slot is named rather than
  inferred, so a badge written as content stays content. The rail keeps its
  width and a wide marker overhangs it. —
  [ADR-0356](adr/0356-a-connector-grows-from-where-you-were-and-an-entry-has-a-marker-slot.md)
- ~~**A floating button does not scale in.**~~ **It does, 2026-09-17**, from an
  `@starting-style`: the entering state the overlay layer had no way to give
  it, and CSS's own answer to "what does an element transition from on its first
  frame". **And it scales out**: a `FloatSlot` bound to a switch puts `leaving` on
  the button, the stylesheet plays the exit on `fast`, and a host timer removes
  the overlay after it. A general closing phase for overlays is still not built,
  and nothing else has asked for one. —
  [ADR-0352](adr/0352-an-element-enters-from-its-starting-style.md),
  [ADR-0355](adr/0355-a-button-leaves-a-field-counts-both-paddings-and-a-floor-starts-on-its-first-frame.md)
- ~~**A field's room is `width - 2 × left padding`.**~~ **Each edge comes off
  once, 2026-09-17**, in `text-input` as in `text-area`. —
  [ADR-0355](adr/0355-a-button-leaves-a-field-counts-both-paddings-and-a-floor-starts-on-its-first-frame.md)
- ~~**§8's subset has no `@keyframes` and is not going to grow one.**~~ **It has
  one, 2026-09-17.** ADR-0081's argument was about loops that must stay in phase,
  and those stay clock functions. What it did not cover is authored motion
  (sequences, staggers, fills) that otherwise needs a widget of its own.
  §1.7's rule 4 still binds the toolkit's sheets, and `ToolkitLoopsTest` checks
  it. —
  [ADR-0353](adr/0353-a-stylesheet-may-name-keyframes.md)

- ~~**The published javadoc is built with doclint off.**~~ **On, less `missing`,
  and clean across every published module.** The 120 errors were one idiom —
  `@param` lines on a `…Calls` holder class rather than on its `call` method,
  425 of them, moved by a script — and twenty `[Foo]` links to a type in another
  package or module, three of which named things that no longer existed. The
  `missing` group stays off because this codebase documents in prose. —
  [ADR-0343](adr/0343-the-published-javadoc-is-linted.md)
- ~~**No licence text is vendored yet.**~~ **All seven are, 2026-09-17.** The
  superbuild's cached checkouts under `natives/.deps/<target>/<name>-src` *are*
  the pinned revisions — their `HEAD`s were compared against
  `libs.versions.toml` before copying — so the verbatim files came from there
  rather than from a download that might have been a different tag.
  `checkLicenses -Pgoldberry.releaseCheck=true` passes with eleven components.
  What stays true: a bumped pin means re-copying that one file, because the
  copyright lines are the upstream's and not the licence's. —
  [ADR-0015](adr/0015-licensing-and-third-party-disclosure.md)
- ~~**IME preedit is not drawn, and committed text already works.**~~ ~~**IME
  preedit is missing entirely.**~~ **Both were closed by `docs/gaps.md` G15 and
  G16 and the entries had not been struck.** `SDL_EVENT_TEXT_EDITING` and
  `SDL_SetTextInputArea` are bound, `text.edit.Editor` draws the composition
  where it will land, and `text-input` and `text-area` take one inline; a
  `password` deliberately does not, because a candidate window is an unmasked
  window. —
  [ADR-0289](adr/0289-a-composition-is-not-an-edit.md),
  [ADR-0292](adr/0292-a-field-composes-and-a-password-does-not.md)
- ~~**The native image's foreign registrations are generated but the fix is untested
  on an image.**~~ **Tested by hand on all three platforms, 2026-09-17.** A manual
  Showcase run built the image on Linux, macOS and Windows, and the html, canvas and
  Markdown screens open on each. What the hand test found on the way was not a
  foreign call but a resource: the canvas screen's five sample images had never been
  declared, which ADR-0160's rule already covered and `DeclaredResourcesTest` now
  enforces. What is still open from this entry: CI runs the image for three frames
  and opens no screen, so a `--screen=<name>` launcher argument would let it do what
  the hand test did. —
  [ADR-0339](adr/0339-a-foreign-call-is-registered-because-it-exists-not-because-a-run-reached-it.md)

- ~~**A `Validator` is over a `String`, and `date-picker` will want
  otherwise.**~~ **The second seam turned out to be a composition, and `field`
  needed no change at all.** `Validator.parsing(parse, message, rule)` is a rule
  over the *parsed* value expressed as a rule over the text it was parsed from, so
  `Field` still holds a `Validator<String>`, `FieldState` still reads its
  control's binding as text, and neither of them knows a date was involved —
  which is the evidence that this was never a type parameter. It also keeps this
  entry's own premise intact rather than contradicting it: what the user typed
  *is* text until something parses it, and a validator is exactly the thing that
  decides whether it can be. `parse` may throw or answer null and both mean the
  same thing, because `java.time` throws where a hand-written parser returns null
  and a seam that took only one would make the other an application writing a
  try/catch to satisfy a method. An empty value passes without the parser running,
  for `matching`'s stated reason. —
  [ADR-0274](adr/0274-a-calendar-is-told-what-day-it-is.md),
  [ADR-0169](adr/0169-a-field-is-silent-until-you-leave-it.md)
- ~~**An absolutely positioned child is placed against the border box, and the
  clip is the padding box.**~~ **Fixed where ADR-0265 said it was, and the golden
  tail was somewhere else.** `ContainingBlock` shifts an absolute child's inset by
  its containing block's padding on the way to the Yoga node, per edge and only on
  the edges the box named — Yoga's no-inset path already lands on the padding and
  is already right. On the **style** rather than on the computed rectangle,
  because with `left` and `right` both given Yoga derives the child's *width* from
  them: a correction applied after the layout pass could have moved the child and
  could not have resized it. Percentages are declined on both sides of the sum and
  say so, since a percentage inset resolves against a size that does not exist
  yet. `text-input`'s and `text-area`'s compensations came out in the same commit,
  as ADR-0265 insisted they must. What the record could not predict is where the
  images moved: **not** `segmented`, `tour` or `scroll`, whose parents genuinely
  have no padding, but `tabs` — an underline pinned across a header with
  `padding: 0 12px` came out 24 points short of its own label — and `toast`, where
  an overlay pinned to a corner started counting from the application's content
  box instead of from the window. Both mean the border box and now say so, through
  `acrossBorderBox`, in terms of the padding their own style resolved rather than
  a number repeated in a stylesheet. —
  [ADR-0272](adr/0272-an-absolute-child-is-placed-inside-the-padding.md),
  [ADR-0265](adr/0265-yoga-measures-an-inset-from-the-border-box.md),
  [ADR-0167](adr/0167-a-field-owns-its-caret-and-the-model-is-told.md)
- ~~**A window that *moves* does not re-clamp its popups, and a scrolling anchor
  does not drag one.**~~ **Both halves are built, and the second one was a
  question nobody was asking rather than a report nobody was making.** The move
  half is what the entry described: `BackendEvent.Moved`, an SDL translation of
  `WINDOW_MOVED` deduplicated per position, and a fabricated-event test under the
  `dummy` driver. It re-places **immediately** rather than after the next paint,
  which is the opposite of the resize case and for the stated reason — a move
  produces no new capture and invalidates none, so the current one is the right
  one, and no repaint follows a move at all because nothing inside the window
  changed. The scroll half was proposed as `Located` on the anchor, and the anchor
  turns out to have nothing to report: `anchor(id)` answers from a hit-test
  capture taken **every frame**, so the position already had a fresh answer and
  what was missing was the question. `replacePopups` now runs at the end of any
  frame with a popup anchored **by id**, which is also the guard — a popup opened
  against a rectangle a caller computed has nothing to re-resolve. Underneath both
  was a third thing, and it had been wrong since before anything scrolled: a popup
  anchored to `Region.bounds()`, the **layout** rectangle, which for a button
  inside a `scroll` is hundreds of pixels from where the button is drawn. It reads
  `painted()` now, in all three places, and the two rectangles are identical for
  every box nothing transformed — which is why it took a scrolling anchor to show
  it. —
  [ADR-0270](adr/0270-a-popup-is-placed-again-when-its-window-moves.md),
  [ADR-0231](adr/0231-a-popup-is-placed-again-when-its-anchor-moves.md),
  [ADR-0123](adr/0123-a-pinned-box-paints-after-its-siblings.md)
- ~~**Nothing reports a dropped frame.**~~ **`late` is a reading now, and the
  pacer is what can count it.** Both halves the entry named are in one number:
  the frames the loop never reached, which leave no record in a ring that only
  holds frames that were painted, and the frames the platform refused *after* they
  were painted, which were in the mean as though somebody had seen them. The
  arithmetic is `max(pendingSince, lastFrame + interval)` — and `pendingSince` is
  the whole of why this is honest, because the naive version (the gap since the
  last frame, over the interval) reports a window nobody touched for a minute as
  three and a half thousand dropped frames. It drew every frame it was asked for.
  §1.7's idle loop is the common case, so a count of missed refreshes that does
  not know when the request arrived is a count of how long the user was away. A
  **window** rather than a total, like every other number on `FrameStats`: a total
  only ever goes up, and somebody watching a HUD while they work would never see
  it come back to zero. —
  [ADR-0271](adr/0271-a-frame-that-never-happened-is-counted.md),
  [ADR-0146](adr/0146-a-hud-shows-where-the-frame-went.md),
  [ADR-0101](adr/0101-a-diagnostic-must-not-be-the-thing-it-measures.md)
- ~~**"Pixel-precise wheel deltas" is not reachable through SDL.**~~ **A
  difference rather than an agreement**, and the entry that outlived its own
  resolution: §7.1 asked for pixel-precise deltas with a line-based fallback, SDL
  reports only detents as floats, and the two platforms with a pixel axis
  underneath do not surface it. What §2.4 actually wanted from "pixel-precise" is
  scrolling that does not quantize, and a **fractional line** delivers that
  without the mechanism the sentence named — so the honest change was to the
  document rather than to the backend. This had already been settled and said so
  in this file's own introduction while the entry sat on in the list below it. —
  [ADR-0115](adr/0115-a-wheel-reports-a-fraction-and-a-detent.md),
  [ADR-0056](adr/0056-the-wheel-is-lines-and-the-sign-is-ours.md)
- ~~**Every pointer event now costs an `SDL_GetModState`.**~~ **Measured: 8.71
  ns a call, so 34.8 µs per second of dragging.** Polled per event rather than
  carried on it, because SDL's mouse events have no `mod` field where its keyboard
  events do. The entry said "not measured — named here so it can be if a profile
  ever points at it", which is what `ModifierPollBenchmark`
  (`./gradlew :natives:benchmark`) is: at a generous 4000 events a second that is
  0.0035% of one core, spread across a whole second of frames whose budget is 16.7
  ms each. Nothing to do, and nothing to carry on the event either — ADR-0089's
  reason for polling stands, since latching the mask from the last key event
  leaves it stuck down when a window loses focus mid-chord. The number is the
  answer: the next person to wonder should find the measurement rather than repeat
  the worry. —
  [ADR-0089](adr/0089-a-knobs-gesture-is-a-rate.md)

- ~~**A guard at the top of `onPointer` is a guard on every pointer kind, and
  nothing warns.**~~ **It warns now, once per kind per node type.** The entry's
  own last sentence was the design: a default that is "right for `dragX`'s `NaN`
  and quietly wrong for a null `button`", because the two are not the same kind
  of default. `NaN` is **arithmetic** — the meaninglessness propagates and every
  comparison against it is false in both directions, so a caller cannot act on it
  by accident. A null button is a **reference**: it is unequal to *everything*, so
  `button() != PRIMARY` is true for a move and the guard fires backwards, keeping
  the press it was written for and dropping every drag. It stays null rather than
  throwing — an input handler that threw would turn a lost drag into a window that
  falls over — and `Button.NONE` was the other shape and fixes nothing, since the
  guard still fires backwards against a value that now looks deliberate. All nine
  `button()` reads in the toolkit are already inside a kind check, so it fires on
  the mistake and on nothing else. —
  [ADR-0266](adr/0266-a-null-button-is-unequal-to-everything.md),
  [ADR-0168](adr/0168-a-field-is-a-well-and-a-drag-is-a-selection.md)
- ~~**A widget can reach its window, and the in-window overlay layer is still the
  application's.**~~ **`Toasts.of(context)` is the door, and it is a map.** The
  entry had already shrunk once — `BuildContext.host()` answered the popup half —
  and what was left was a `toast` raised from a handler deep in the tree, with
  every layer above it carrying a callback. `findAncestorState` cannot do it and
  it is worth knowing why: a toast stack is mounted in the **overlay layer**,
  which is a *sibling* of the application's root under `window-root` and not an
  ancestor of anything inside it, so walking up reaches `window-root` and stops.
  `host()` gives the window and `Toasts.at` is the one place that knows which
  stack is on it, so the mechanism is a weak map from the first to the second —
  kept in `:widgets`, because `:core` learning what a toast is would undo the
  reason `Toasts`, `Menus` and `Dialogs` are three classes there rather than
  three methods on the window. A general `host.service(Class)` is the shape to
  reach for **if `Menus` or `Dialogs` ever want the same thing**, which is
  ADR-0140's own rule about one consumer not being enough. —
  [ADR-0264](adr/0264-a-widget-may-find-the-toast-stack.md),
  [ADR-0140](adr/0140-a-widget-may-reach-its-window.md),
  [ADR-0100](adr/0100-a-window-has-a-layer-above-its-application.md)
- ~~**The enter/exit lifecycle is a tab's own, not the toolkit's.**~~ ~~**A tour
  has no arrival or exit.**~~ **The promotion happened two records ago, and the
  tour uses it now.** The first entry asked for `TabPhase` to be promoted "when
  the second consumer arrives"; it is `widgets.core.Phase`, moved there by
  [ADR-0166](adr/0166-a-raised-thing-is-told-apart-by-its-edge.md) — whose own
  javadoc says "there was never anything tab-shaped in it" — with the
  `closing → removed` half extracted into `Departure` by
  [ADR-0234](adr/0234-the-overlay-lifecycle-is-a-departure-and-a-phase.md). Six
  families use one or both, **including `toast` and `dialog`**, which is to say
  both of the consumers the entry named as *wanting* it. So the second entry's
  "that is `TabPhase` again" was pointing at a wall that had been a door for
  milestones, and what was missing was a `tour` walking through it. It has an
  arrival now (`opacity` and a 4px rise, §3.1's popover row bar the scale, which
  `popover` itself also lacks and for the same `transform-origin` reason) and a
  travelling cut-out (one rectangle interpolated, so the ring, the hole and the
  card cannot disagree mid-flight). `AnimationSweepTest` caught two real gaps
  within a minute — a `Phase` on `TourVeil` with no `isAnimating`, and a package
  with no test naming it — neither of which an image would have shown. **The
  goldens did not move**: `TourGoldenTest` has a virtual clock now, so the
  settled tour is the picture it always was. —
  [ADR-0269](adr/0269-a-tour-arrives-and-its-cut-out-travels.md),
  [ADR-0166](adr/0166-a-raised-thing-is-told-apart-by-its-edge.md),
  [ADR-0109](adr/0109-a-tab-arrives-and-departs-on-the-frame-clock.md)
- ~~**A tour card's height is estimated, not measured.**~~ **It is measured, and
  the mechanism was already in the file.** The entry said measuring "needs the
  measure-then-place machinery ADR-0104 built, which works on *windows* rather
  than on boxes" — and `TourStop` already banks the **window's own rectangle**
  from the frame before, through `Located`. The card is one node further in and
  `Measured` is the same door. `ESTIMATED_HEIGHT` survives with a narrower
  meaning: what the *first* frame decides with, before anything has been laid out
  and had a height to report. `Measured`'s third rule holds **by construction** —
  the card's width is fixed and its content is the stop's own text, so the height
  does not depend on whether it was placed above or below — which is `masonry`'s
  argument rather than the scrollbar's, and is asserted rather than claimed. —
  [ADR-0268](adr/0268-a-tour-card-says-how-tall-it-came-out.md),
  [ADR-0121](adr/0121-a-tour-is-a-veil-and-a-sequence.md)
- ~~**A tooltip's 500ms delay is a constant, and the token that would replace it
  cannot be read.**~~ **Both blockers expired, and one was never true.** The
  first — "nothing above the cascade can read a resolved custom property" — is
  `BuildContext.token`
  ([ADR-0254](adr/0254-a-build-may-ask-the-cascade-for-a-number.md)), and the
  launcher holds an `Element`, which *is* a `BuildContext`. The second asked
  whether the design system should carry a duration that is not motion, and
  `design-system.md` §3's `tooltip` row had already answered: **"delay 500ms show
  / 100ms move-between"**. So the question was settled before it was asked — and
  the code had built the first number as a constant and **the second not at
  all**, so a user reading along a toolbar was served the full sentence of hover
  intent at every button. That is a specified behaviour that was never built,
  hiding inside an entry about tokens. `BuildContext.duration` is `token`'s
  sibling with the cascade's own `ms`/`s` parser where its length parser is —
  made public rather than written twice, because two readers for one syntax
  disagree the day either grows a unit. —
  [ADR-0262](adr/0262-a-delay-is-a-metric-and-metrics-are-tokens.md),
  [ADR-0105](adr/0105-a-tooltip-is-an-attribute-not-a-widget.md)
- ~~**A focus ring is only ever pictured on the dark theme, apart from one.**~~
  **There is a rule now, and it is a test.** The entry asked for "a rule about
  which states are worth a second theme rather than one more image", and the rule
  is narrow on purpose: §2.2's ring is the one mark in the system with **no second
  means of being seen** — a hover has a wash, a checked control has a fill, a
  disabled one has its opacity, and each of those is drawn in colours some other
  golden already covers. A ring is only a ring, and `--gb-focus` differs per
  theme. `FocusGoldenPairTest` reads the resource *directory* rather than a list,
  so a focus golden added next month is checked next month; it also asserts that
  the sweep **found** something, because a discovering test's own failure mode is
  passing by seeing nothing. Three images came with it — `menu-focus-light`,
  `menubar-focus-light`, `tabs-focus-light` — and doubling the whole corpus was
  the alternative and is not a rule so much as the absence of one. —
  [ADR-0261](adr/0261-a-ring-is-photographed-on-both-themes.md),
  [ADR-0240](adr/0240-the-ring-follows-the-accent.md)
- ~~**An icon-only segment has no accessible name, and neither does an icon-only
  button.**~~ **`name=` is on `Attributes` now, so every widget has one.** The
  entry separated two things that had drifted together: "§13's semantics are
  M5's" is true of the **AccessKit bridge**, and was never true of
  `Semantics.role()` and `accessibleName()`, which have shipped for milestones
  with a sweep enforcing them. What was missing was somewhere to put a name a
  widget cannot work out — an icon-only control's label is the empty string *by
  construction*, so `Button.accessibleName()` answered `""`: a control a reader
  cannot announce, passing a sweep that only checked for null. It sits beside
  `tooltip` and `context-menu` for their reason, which the entry had already
  written ("a gap the whole catalog shares"), and the label wins where there is
  one. —
  [ADR-0260](adr/0260-a-name-is-an-attribute-every-widget-has.md)
- ~~**`--gb-list-row-height` has no consumer.**~~ **It has two, and has had since
  `list` shipped.** `ListState` reads the token through `BuildContext.token`
  ([ADR-0254](adr/0254-a-build-may-ask-the-cascade-for-a-number.md)) and
  `list-row` writes `height: var(--gb-list-row-height)`, so the number the
  density decides is the number the rows are and the number the spacers are
  spaced by — which is now checked, since a disagreement between the last two is
  reported ([ADR-0257](adr/0257-a-diagnostic-is-asked-for-not-logged.md)). The
  entry's closing line, "`list` is M3", is the other half that expired: `list` is
  built. —
  [ADR-0257](adr/0257-a-diagnostic-is-asked-for-not-logged.md),
  [ADR-0254](adr/0254-a-build-may-ask-the-cascade-for-a-number.md),
  [ADR-0074](adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md)
- ~~**A `static` `@Action` is still unsupported, and nothing refuses one
  explicitly.**~~ **Both paths refuse one by name, and both refusals are
  tested.** The entry was right that it cannot work — the woven path would
  generate `target::method` for a method with no target, and the reflective one
  writes `findVirtual` — and wrong that nothing says so. `ModelWeaver` throws a
  `WeaveException` and `RuntimeBinding` an `IllegalStateException`, both reading
  *"is static; an action changes a model, and a static one has no model to
  change"*, and `ModelWeaverTest.staticAction` and
  `RuntimeBindingTest.staticAction` are the two tests. Nothing was built to close
  this; it had been closed and the entry was not updated, which is the argument
  for reading an entry against the code before believing it. —
  [ADR-0098](adr/0098-a-private-member-is-reached-by-a-handle.md)
- ~~**Nothing warns when a declaration is dropped for being unsupported, in an
  application's stylesheet.**~~ ~~**An application's stylesheet can still be all
  classes, and nothing says so.**~~ **Both are asked for now, which is what both
  entries said the answer had to be.** `StyleLint` is
  `SupportedPropertyTest`'s machinery with the test taken off it: every rule
  through the real cascade, every declaration to the real `ComputedStyle`, and
  `Finding` values back with the line and column the parser saw. Neither entry
  wanted a louder log and ADR-0216 is why — a dropped *value* already warned at
  WARN and `group-box-title` drew square corners for months anyway. The engine
  side is **four lines**: `with` returns `this` in exactly two places and both
  are failures, so identity is the answer and it cannot drift from the behaviour
  because it *is* the behaviour. What it deliberately does not do is tell the two
  failures apart, which would mean the engine reporting rather than being asked —
  thirty edited switch arms in the frame loop for a difference the author reads
  off §8's list either way. An unresolvable `var()` is not a finding either: it
  is already the resolver's report, once, which is ADR-0243's shape. The test
  that used to do this lost a logback appender, two sentence-matching filters and
  its own two guard tests, and **gained two sweeps it could not afford** — the
  light theme and the compact density, either of which can resolve a `var()` the
  other does not. —
  [ADR-0257](adr/0257-a-diagnostic-is-asked-for-not-logged.md),
  [ADR-0249](adr/0249-a-rule-that-can-name-a-type-does.md),
  [ADR-0216](adr/0216-a-corner-is-four-numbers-and-a-lint-reads-values-too.md),
  [ADR-0215](adr/0215-a-property-the-engine-drops-is-a-rule-that-does-nothing.md)
- ~~**`flex-grow` means nothing inside a `scroll`, and nothing says so.**~~ **It
  says so now, once per axis.** The entry expected this to be hard — "a
  diagnostic would have to know that a `grow` resolved against an unbounded main
  axis, which Yoga knows and does not report" — and it is a field comparison:
  `ScrollContent.render` is handed its children as **boxes**, with `flex-grow`
  already resolved, and the content box's main axis *is* the scrolling axis by
  construction. Nothing had to be asked of Yoga. It also catches a widget that
  set the growth itself, which no rule in any stylesheet would have shown.
  ADR-0251's `warnIfNestedOnTheSameAxis` is the shape, down to the static set
  that keeps it a message rather than a stream. —
  [ADR-0257](adr/0257-a-diagnostic-is-asked-for-not-logged.md),
  [ADR-0251](adr/0251-a-widget-may-read-a-token-and-a-nested-scroller-is-named.md),
  [ADR-0116](adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)
- ~~**A row height that disagrees with the stylesheet is a silent layout
  error.**~~ **It is reported now, and by a simpler mechanism than the entry
  predicted.** It asked for "a `Measured` assertion on the first built row"; what
  it got is the **cascade**, because `list-row` declares
  `height: var(--gb-list-row-height)` and that number is resolved before the row
  is laid out — so the check is exact, free and a frame earlier than a
  measurement. Two things it turned up. **The mismatch has to be seen twice**,
  because the first build of a tree has no cascade
  ([ADR-0254](adr/0254-a-build-may-ask-the-cascade-for-a-number.md)): a list
  reading the token answers the default on that build and the stylesheet's value
  on the next, so under a compact density there is one frame of a real
  disagreement that settles by itself — and reported naively, the form that
  *cannot* be wrong would have been the noisiest one. And that forced the second:
  the check runs on **one row of the window**, since twenty rows resolving the
  same height would report twenty times a frame and "seen twice" could not then
  tell a frame from a sibling. The entry's last sentence was already stale —
  reading `--gb-list-row-height` is ADR-0254's door and it is open. —
  [ADR-0257](adr/0257-a-diagnostic-is-asked-for-not-logged.md),
  [ADR-0254](adr/0254-a-build-may-ask-the-cascade-for-a-number.md),
  [ADR-0213](adr/0213-a-virtual-list-is-two-spacers-and-a-window.md)
- ~~**A slider's value label is left-aligned in its box, because §8's subset has
  no `text-align`.**~~ **It is `text-align: end` now, and nothing had to be added
  to `Box`.** The entry's reason was quoting §8's own note — "`Box` cannot express
  them" — and that note was right about `backdrop-filter` and `letter-spacing`,
  wrong about this one, and has since been overtaken on `box-shadow` too
  ([ADR-0310](adr/0310-a-shadow-is-a-stack-of-rectangles.md): a `Decoration`
  component and a stack of rounded rectangles, no `Box` field required). `Paragraph.paint` is already handed
  the box's width, because it has to be or the text could not wrap to it, and
  every `TextLine` has already measured itself: the two numbers an alignment
  needs were in the same method the whole time, and what was missing was a
  keyword saying what to do with them. `slider-value` is `width: 40px` by
  declaration (ADR-0080), which is exactly the condition under which an alignment
  means anything — and the four goldens that moved are all the same readout,
  `slider-value.png` plus the showcase's Basic screen in its three variants,
  with `9%`, `50%` and `100%` finally lining up on their trailing edge. `left` and
  `right` are **refused**, for
  [ADR-0247](adr/0247-start-is-css-and-flex-start-is-yoga.md)'s reason: they are
  not the same as `start`/`end` under RTL. `justify` is refused for a different
  one — it is a respacing rather than a placement, and a paragraph shaped once
  has nowhere to put the extra advance. —
  [ADR-0256](adr/0256-a-line-is-placed-by-the-paint-not-by-the-box.md),
  [ADR-0255](adr/0255-a-label-that-does-not-fit-is-cut-not-wrapped.md),
  [ADR-0080](adr/0080-a-value-is-measured-along-a-part.md)
- ~~**A menu row overflows rather than ellipsising, and the missing property is
  `white-space: nowrap`.**~~ ~~**A segment's label overflows its cell when it is
  longer than 1/n of the bar.**~~ **Both are cut now, and so are `option` and
  `select-value`.** The entry was right about the property and right about why
  three attempts at clipping had failed: a box with text is a **measured leaf**,
  so narrowing it re-measures the paragraph and *wraps* it, and there is then
  nothing overflowing to clip. §8's subset has `white-space: normal|nowrap` and
  `text-overflow: clip|ellipsis` now, and `white-space` is the whole mechanism —
  under `nowrap` the measure function ignores the width Yoga offers and reports
  the width the text wants, so a box may be laid out narrower than its own
  content, which is the state the clip and the ellipsis were always waiting for.
  Three things worth keeping. **`text-overflow` is read only at paint time**: an
  ellipsised line is drawn short and measured long, because a paragraph whose
  measurement shrank from being truncated would let the ellipsis decide the width
  that caused it. **The cascade carries the two properties apart** and hands out
  one value, because CSS inherits `white-space` and does not inherit
  `text-overflow` and a bundle cannot be half-inherited — so `whiteSpace` had to
  join `inheritsSameAs` as well as `inheritingFrom`, which is
  [ADR-0248](adr/0248-only-the-inherited-half-is-handed-down.md)'s standing
  warning. And **ADR-0148's `flex-shrink: 0` came off the label** rather than
  being reverted: it stopped the wrap by stopping the shrink, and `nowrap` stops
  the wrap without it. The accelerator keeps its own, because half of
  `Ctrl+Shift+K` is not a shortcut. What ADR-0235 left that is *not* closed is
  `progress`'s indeterminate sweep, which is a design decision about a shipped
  animation. —
  [ADR-0255](adr/0255-a-label-that-does-not-fit-is-cut-not-wrapped.md),
  [ADR-0235](adr/0235-a-cut-label-needs-nowrap-not-text-overflow.md),
  [ADR-0148](adr/0148-a-menu-row-does-not-wrap.md)
- ~~**`--gb-list-row-height` has no consumer, and no widget can read a resolved
  custom property at build time.**~~ **`BuildContext.token` is the other door,
  and it was three lines.** `Element` already implemented **both**
  `BuildContext` and `StyleElement`, and `ElementTree` has held a `StyleResolver`
  since ADR-0149 — what was missing was the method. `WidgetRenderer.prepare` is
  the new part and it is about *ordering*: `render` hands the tree its resolver
  on the way in, which is a frame too late for a reader in `build`. **The stakes
  were higher than a repeated number**: `density-compact.css` sets
  `--gb-list-row-height: 26px`, so a list written `virtualized(32)` virtualizes
  on the wrong pitch the moment an application switches density. Two things
  measuring turned up. The **first build of a tree has no cascade** — a
  `Stateful` widget builds inside the `ElementTree` constructor, before any
  renderer exists — so a token there answers its default and the second build is
  the first that can see the stylesheet; a virtualized list settles by
  construction, and that is now written down rather than assumed. And the token
  must be declared **at or above** the list, because `ListView` is a composition
  node whose state builds the `list` element — which is where it ships, on
  `:root`. —
  [ADR-0254](adr/0254-a-build-may-ask-the-cascade-for-a-number.md),
  [ADR-0251](adr/0251-a-widget-may-read-a-token-and-a-nested-scroller-is-named.md),
  [ADR-0213](adr/0213-a-virtual-list-is-two-spacers-and-a-window.md)
- ~~**`--gb-caret-width` is not a token and the caret is one logical pixel.**~~
  **It is a token now, and it was an accessibility gap rather than a styling
  question.** The entry's diagnosis was right — a `caret { width: 3px }` is
  *overwritten* rather than honoured, because the caret's box is set after the
  cascade — and the token was not shipped only because nothing could read one,
  which [ADR-0251](adr/0251-a-widget-may-read-a-token-and-a-nested-scroller-is-named.md)
  changed. A thicker caret is a **low-vision aid**, which is why §13 lists that
  kind of switch. Two things the entry did not mention. `text-input` and
  `text-area` each had their own `CARET_WIDTH = 1`, the second's comment saying
  it was the first's — one constant in `widgets.form.Carets` now, with a test
  that says they agree. And there is a **third** consumer:
  `TextInputState.laidOut` reserves "the caret's own width of room" so a field
  does not scroll short of showing it, hard-coded to 1 — a three-pixel caret
  against a one-pixel reserve is a caret clipped at the end of the text, which is
  the failure that would have looked like a text-rendering bug. —
  [ADR-0253](adr/0253-a-caret-is-as-wide-as-the-theme-says.md),
  [ADR-0251](adr/0251-a-widget-may-read-a-token-and-a-nested-scroller-is-named.md),
  [ADR-0167](adr/0167-a-field-owns-its-caret-and-the-model-is-told.md)
- ~~**A window's maximized state is write-once and cannot be read back.**~~
  **All three are built, and the question the entry left open has an answer that
  follows from what maximizing is.** `Window.maximize()`, `restore()` and
  `isMaximized()` ship, and `SDL_EVENT_WINDOW_MAXIMIZED`/`RESTORED` arrive as one
  `BackendEvent.MaximizedChanged` — `FocusChanged`'s shape, because SDL sends two
  and every consumer wants the boolean. **`isMaximized()` answers what the
  platform last *reported*, not what was last asked.** ADR-0221 had already
  established that maximized is a *state* rather than a size, and every platform
  routes the ask through a window manager that may refuse it — so a flag set on
  the way out would be a lie the moment one did. The cost is stated rather than
  hidden and is asserted by a test: between the request and the event,
  `isMaximized()` is still false, because that is a window which has been asked
  and has not yet agreed. It is also what makes the interesting half work — an
  application can learn that the **user** maximized it, which no amount of
  tracking one's own calls can produce. —
  [ADR-0252](adr/0252-a-window-is-maximized-when-the-platform-says-so.md),
  [ADR-0221](adr/0221-a-window-may-open-maximized.md)
- ~~**A widget cannot read a resolved custom property, so `scroll`'s line height
  is a constant.**~~ **It can, and the entry was half stale when it was
  written.** `Paints.Context.color` has read one since ADR-0195 — that is how a
  chart gets `--gb-chart-1…8` — so what was missing was the same door for a
  *number*, and `length` is it. The interesting half is that reading it is not
  enough: **the wheel arrives where there is no context to ask**, so
  `ScrollViewport` reads the token in `render` and *banks* it into `ScrollState`
  through the shape `onMeasured` already had. That makes it a frame late, which
  is ADR-0117's bargain unchanged — a paint always precedes an input. What is
  left is `list`, and it needs a different door; it is
  [above](#the-catalog-specified-and-unbuilt). —
  [ADR-0251](adr/0251-a-widget-may-read-a-token-and-a-nested-scroller-is-named.md),
  [ADR-0195](adr/0195-a-painter-reads-the-theme-through-a-custom-property.md),
  [ADR-0116](adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)
- ~~**Nested same-axis scrollers are banned in the canon and nothing enforces
  it.**~~ **A nested pair says so now, once.**
  `BuildContext.findAncestorState` is the whole implementation — it exists for
  `scrollIntoView` and answers this question with nothing added, which is why it
  is asked in `ScrollState.build` rather than by teaching the renderer about
  scroll views. It stays **a diagnostic and not a refusal**: chaining already
  makes the arrangement work, and turning a canon rule into a crash is worse than
  the rule going unheard — the author's problem was that nobody told them.
  Deduplicated by axis for ADR-0243's reason, because `build` runs per element
  per invalidation and a document that nests in four places has one mistake. —
  [ADR-0251](adr/0251-a-widget-may-read-a-token-and-a-nested-scroller-is-named.md),
  [ADR-0243](adr/0243-a-missing-token-is-a-message-not-a-stream.md),
  [ADR-0116](adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)
- ~~**`stack` is still owed.**~~ **It is built, and it positions nothing.**
  The entry's own last sentence had become "what `stack` still wants is `stack`"
  once [ADR-0244](adr/0244-a-child-may-say-where-it-sits.md) took its last
  blocker. It is nine lines: the **first child stays in flow** so the box has a
  size — a stack whose children are all out of flow is a box of nothing, and this
  is what makes wrapping an existing widget in one a change that cannot move it —
  and every child after it is `position: absolute` so an overlay cannot resize
  what it sits on. §1's "positioned by alignment or absolute insets" needed no
  code at all: both already worked, and this is the case
  `ComputedStyle.INITIAL`'s inset comment has been describing since before
  anything could reach it — *"the difference only shows on an absolute node,
  where zero would stretch it and undefined leaves it where the alignment put
  it"*. Z-order is document order, which is the painter's existing rule for
  siblings. —
  [ADR-0250](adr/0250-a-stack-is-one-child-in-flow.md),
  [ADR-0244](adr/0244-a-child-may-say-where-it-sits.md),
  [ADR-0100](adr/0100-a-window-has-a-layer-above-its-application.md)
- ~~**A style that really changes still re-resolves its whole subtree, and only
  the inherited properties can matter.**~~ **It compares the inherited half now,
  and the notion the entry wanted already existed.** `ComputedStyle` does have a
  list of what inherits — `inheritingFrom` is two lines, `color` and
  `typography`, and its comment even enumerates what is deliberately *not* there.
  What was missing was reading it twice. **The difficulty was not the
  comparison**: `stableStyle`'s return did two unrelated jobs, the children's
  cache key *and* what the node paints, so loosening it would have handed back an
  older instance with last frame's transform and then painted with it — a
  scrolling viewport frozen at its first offset while every child cached happily.
  Two variables, because there are two jobs. The test for that is the one that
  matters and it was written against the mistake: folding the roles back together
  fails it. —
  [ADR-0248](adr/0248-only-the-inherited-half-is-handed-down.md),
  [ADR-0142](adr/0142-a-style-handed-down-keeps-its-identity.md)
- ~~**The rule buckets are only as good as the stylesheet, and nothing enforces
  that the toolkit's own stay type-first.**~~ **They are enforced, and measuring
  found the assumption was already half wrong.** 16 of 340 rules named no type,
  and they were two families rather than a scattering. **Seven were `tour`'s
  parts** — built from plain `Text` and `Button` widgets carrying a class, so
  every one *was* matching a known type and simply not saying so; `text.tour-title`
  matches exactly what `.tour-title` matched and lands in a bucket. Seven rules,
  one word each, and **no golden moved**, which is the evidence that it changed
  what the cascade looks at rather than what it finds. The other eight cannot be
  qualified and should not be: the typography scale is ranks an application puts
  on whatever it likes, and `:root` is the theme's token layer. `RuleBucketTest`
  holds those eight as an **exact set** — a threshold is a number somebody
  raises. —
  [ADR-0249](adr/0249-a-rule-that-can-name-a-type-does.md),
  [ADR-0152](adr/0152-the-cascade-looks-at-rules-that-could-match.md)
- ~~**`--gb-surface-2` has been mistaken for an elevation three times, and it is
  unresolved whether it should keep existing.**~~ **It stays, and the trap is an
  asserted fact now.** The look the entry asked for found **five** readers and
  not one of them wants a direction: a default `badge`'s fill, a `scrollbar` on
  hover, a `group-box-title` band, a `skeleton-bar` and a collapsed
  `split-divider`. Every one wants a plate merely *distinct* from what is under
  it, which is what the token promises — the three that were wrong wanted
  "raised" or "sunken" and have their own tokens now. Renaming it would not have
  stopped a single one of them. What does is `ThemeTest`: `--gb-surface-raised`
  is never darker than `--gb-surface` and `--gb-surface-sunken` never lighter, on
  both themes — and **`--gb-surface-2` takes opposite directions in the two
  files**, a step up on dark and down on light, which is exactly why each
  consumer looked right to whoever wrote it and wrong to everybody on the other
  theme. The theme files carry the same sentence at the definition. —
  [ADR-0245](adr/0245-the-second-surface-stays-and-says-so.md),
  [ADR-0168](adr/0168-a-field-is-a-well-and-a-drag-is-a-selection.md)
- ~~**A `select`'s typeahead works closed and not open.**~~ **It works open, and
  the condition the entry set for adding a capture phase was met.** The entry
  named the fix — `Handles` had an `onKeyCapture` and no `onTextCapture` — and
  refused to add it on spec, "because a capture phase is a routing rule and
  inventing one for a single consumer is how a router grows two". There is a
  consumer now. `textInput` captures root-first then bubbles, which is
  `dispatchKey`'s shape exactly and removes an asymmetry nobody had written down:
  one event kind had a phase the other did not. `SelectList` reads the letters on
  the way down and calls the **same** `typeahead` the closed control calls, so
  `n`, `n`, `n` cycles the same options in the same order either way. It consumes
  what it acted on, leaves blank text alone — a space means "pick this one"
  everywhere else — and a `tree` gets none, which is [above](#the-catalog-specified-and-unbuilt). —
  [ADR-0246](adr/0246-text-has-a-capture-phase-now-that-something-wants-one.md),
  [ADR-0141](adr/0141-a-select-is-a-closed-control-and-a-list.md)
- ~~**Whether to accept CSS's alignment aliases is open.**~~ **They are taken,
  because they are not aliases.** `align-items: start` is **CSS** — Box Alignment
  Level 3 — and Yoga has only `flex-start`, so the toolkit was dropping a
  declaration the specification allows and telling the author they had made a
  typo. It filled the Panels screen's console for long enough to need
  deduplicating before anybody asked whether the declaration was actually wrong.
  Two entries in one map, applied in `keyword` **after** the enum's own lookup so
  a constant named `START` could never be shadowed by it. `left` and `right` stay
  refused: they are `justify-content` only and are *not* `start`/`end` under RTL,
  so §2.4's bidi support means the toolkit cannot promise they stay equivalent.
  Two tests that encoded the old decision were rewritten, both in the group that
  exists because of this typo. —
  [ADR-0247](adr/0247-start-is-css-and-flex-start-is-yoga.md),
  [ADR-0216](adr/0216-a-corner-is-four-numbers-and-a-lint-reads-values-too.md)
- ~~**`align-self` is not in §8's subset.**~~ **It is built, and the entry was
  wrong twice in the toolkit's favour.** §8 had listed
  `align-items/self/content` all along and named only `flex-basis` as
  unimplemented — so the document claimed this worked, and what was missing was
  the implementation rather than the sanction. And `Align.AUTO` was already
  waiting for it: the enum's own comment says "`AUTO` only means anything for
  `align-self`", a value that existed for a property that did not. The price the
  entry quoted was real — 47 positional argument lists across two records, with
  `alignItems` and `alignSelf` **the same type**, so a swap between them
  compiles and runs — and it was already insured. `RecordWitherTest` has existed
  since ADR-0181 for exactly this: it asks every wither to set its component to
  what it already holds and requires the record back unchanged, which no
  transposition survives. The clean sites were scripted and the four carrying
  inline commas edited by hand. **`stack` is one blocker lighter**; what it still
  wants is `stack` itself. —
  [ADR-0244](adr/0244-a-child-may-say-where-it-sits.md),
  [ADR-0181](adr/0181-a-box-may-say-how-small-and-how-large.md),
  [ADR-0111](adr/0111-a-text-box-is-painted-inside-its-padding.md)
- ~~**Nothing warns that a `var()` resolved to nothing — it logs, per node, per
  frame.**~~ **It says it once, and the field is the resolver's rather than a
  static.** The entry named the fix — "once per property per stylesheet would
  make it a diagnostic" — and `ComputedStyle` had already met the same problem one
  stage later and answered it (ADR-0216): a stylesheet is static, so a
  declaration that cannot resolve cannot resolve next frame either, and *"that is
  not a louder warning, it is a quieter log"*. The difference worth having is
  where the set lives. `ComputedStyle`'s is static and needs a public
  `forgetReportedDrops()` for tests; a `StyleResolver` is built per stylesheet
  set and lives as long as its renderer, so **once per resolver is once per
  stylesheet** — a theme swap builds a new one and legitimately reports what the
  *new* theme is missing, and a test is isolated by constructing its own rather
  than by remembering a static hook. Keyed by property **and** element type,
  which is a refinement of the entry: the same token failing on `button` and on
  `text` is two facts, and which types it reaches is the blast radius somebody
  debugging it wants. A cycle is keyed by the property alone, because that is a
  fact about the property. The **drop** is unchanged and still happens every
  time; only the report is once. —
  [ADR-0243](adr/0243-a-missing-token-is-a-message-not-a-stream.md),
  [ADR-0216](adr/0216-a-corner-is-four-numbers-and-a-lint-reads-values-too.md),
  [ADR-0121](adr/0121-a-tour-is-a-veil-and-a-sequence.md)
- ~~**`em` and `rem` do not resolve against the node's own `font-size`.**~~
  **`em` does now, in two passes, and the fix needed no plumbing at all.**
  `CssLength.Context` was always the right shape; what was missing is that
  nothing built one per element — `WidgetRenderer` holds one for the whole tree
  and handed the same instance to every node, so `em` was one constant at every
  depth. The two passes are CSS's own rule rather than a refinement: on
  `font-size` an `em` is the **parent's** size, because the value being computed
  cannot be its own input, and on everything else it is the element's **own**.
  Both were already in hand — `parent.typography().size()` is passed for
  inheritance anyway. **Measuring it turned up a number the entry did not
  mention**: `Context.DEFAULT` is 16 and `Typography.INITIAL` is **13**, so `1em`
  was not the parent's size, not the element's own, and not any size the toolkit
  renders text at. `Transform` was the same bug in a second place and said so in
  a comment; it takes a `Context` now. What is left is `rem`, and it is
  [above](#style-colour-and-motion). —
  [ADR-0242](adr/0242-em-is-the-elements-own-size.md),
  [ADR-0066](adr/0066-a-weight-is-a-face-and-color-inherits.md)
- ~~**Nothing validates an application's own theme.**~~ **`ThemeAudit` does, and
  the pairs are found by convention rather than listed.** The arithmetic was nine
  private lines in `ContrastTest`; it is `css.contrast.Contrast` now, in `:core`
  and exported, because a theme is core and an application should not need the
  widget catalog to learn its colours are unreadable. The part the entry did not
  anticipate is what makes it worth having: a hard-coded list of the toolkit's
  own pairs would check a custom theme's *overrides* and miss everything it
  added, so the rule is **every `--gb-<name>-bg` with a matching
  `--gb-<name>-text`** — which the design system already follows, and which
  audits `--gb-mycard-bg` for free. Two details decide whether it works on a real
  theme: values are **substituted**, so `--gb-badge-warning-bg: var(--gb-warning)`
  is measured rather than skipped as "not a colour"; and a **translucent** pair is
  skipped rather than scored, because what it composites over decides the answer
  — `--gb-hud-bg` is `#1c212ae6` and is the shipped example. `ContrastTest` now
  calls the same code, so the number CI asserts and the number an application
  audits against cannot drift. It does **not** cover the non-text floor: which
  token is a *mark* is not something a naming convention can tell, and those
  sixteen are [above](#style-colour-and-motion). —
  [ADR-0241](adr/0241-a-theme-can-be-audited-by-whoever-wrote-it.md),
  [ADR-0239](adr/0239-a-mark-is-measured-against-the-box-it-is-drawn-in.md),
  [ADR-0087](adr/0087-a-semantic-fill-brings-its-own-foreground.md)
- ~~**§2.2's focus ring is below §1.2's floor on every surface of the light
  theme.**~~ **It follows the accent now, which is what the dark theme always
  did.** Opened by [ADR-0239](adr/0239-a-mark-is-measured-against-the-box-it-is-drawn-in.md)
  the moment the non-text floor was first measured, and the cause was a **ramp
  left behind** rather than a colour anyone chose: both themes set the ring to
  their accent, except that the light theme's accent had moved down the Frost
  ramp to `--nord10` for contrast and the ring kept the pale `--nord8`. One
  token, 1.64:1 → 3.31:1 at worst, and a palette value rather than an invented
  one. What it exposed is the more useful half and is [above](#style-colour-and-motion):
  changing a shipped colour moved **no golden**, because every focus golden in
  the catalog was `NORD_DARK`. —
  [ADR-0240](adr/0240-the-ring-follows-the-accent.md),
  [ADR-0239](adr/0239-a-mark-is-measured-against-the-box-it-is-drawn-in.md)
- ~~**Non-text contrast is not checked at all.**~~ **It is measured now, and the
  question the entry could not answer had a simpler answer than it looked.** What
  counts as the background of a mark drawn onto its own box is **its own box**: a
  mark is coloured by the `color` of the element it is drawn in and that element
  supplies its own `background`, and for every mark in the catalog *the same rule
  sets both* — a checked tick is `--gb-checkbox-mark-checked` on
  `--gb-checkbox-bg-checked`, both from `check-indicator:checked`. So the pair is
  one `ComputedStyle`'s two properties and nothing needs the painted frame. Three
  sweeps: a mark against its box, a ring against the surface behind it, and a
  control against that surface **by the better of its fill and its edge** — a
  maximum rather than two measurements, because §1.2 asks that *some* means
  identifies a component, and measuring separately reported `--gb-border` failing
  everywhere when a decorative divider is supposed to be subtle. What the sweeps
  find is [above](#style-colour-and-motion) and is not this entry's any more. —
  [ADR-0239](adr/0239-a-mark-is-measured-against-the-box-it-is-drawn-in.md),
  [ADR-0088](adr/0088-a-fill-that-carries-text-moves-away-from-it.md)
- ~~**A wheel over a *disabled* control is swallowed outright, and the scroll
  view above it never gets a turn.**~~ **It chains now, and the answer was "per
  event kind" — for one kind.** The entry stated the question correctly and the
  resolution is the narrow half of it: `dispatch` still refuses a press, a
  release and a click aimed into a disabled subtree, for ADR-0059's unchanged
  reason — that argument is about *the thing being aimed at*, and a click on a
  disabled button must not become a click on the row holding it. A wheel is not
  aimed at a control; it is aimed at whatever scrolls, which is what every
  platform does with one. So for a wheel the chain is built and its **disabled
  prefix dropped** rather than the whole dispatch abandoned: the dead subtree
  still handles nothing, and what changes is only who gets a turn afterwards.
  `isInput` is untouched — taking `WHEEL` out of "the user *doing* something"
  would have made the disabled knob start turning. What this also records is why
  nothing caught it: `DisabledPropagationTest`'s tree had **nothing above** the
  disabled container, so "refused" and "swallowed" logged identically. —
  [ADR-0238](adr/0238-a-wheel-chains-past-a-dead-control.md),
  [ADR-0236](adr/0236-a-wheel-is-consumed-by-whatever-it-moved.md),
  [ADR-0059](adr/0059-a-control-is-a-record-a-node-and-a-rule.md)
- ~~**Nothing recomputes the cursor when the tree changes under a still
  pointer.**~~ **It does now, and the entry named half of it.** The cursor half is
  exactly as written — a fourth position field, remembered from every entry point
  that carries one rather than from `pointerMoved` alone, and `cursorAt` re-run
  from `updateRegions` after each paint. `NaN` is the whole of "we do not know",
  and it means it twice: before the pointer has arrived and after it has left,
  which is another window's pointer and not a place to ask about. The capture
  freeze is reached *through* rather than around, so a repaint during a drag does
  not thaw the shape. What measuring it turned up is that **`:hover` and
  `:active` had the same staleness**, and that `mark`'s own comment denied it —
  "a control that was hovered before it became disabled does not keep the state"
  was describing an intention as an achievement, because clearing is not
  suppressed but nothing called it. Fixing only the cursor would have shipped a
  control drawing its hover wash while its cursor said `not-allowed`, so both
  halves went together; §2.1 left no decision to defer. —
  [ADR-0237](adr/0237-the-pointer-state-follows-the-frame.md),
  [ADR-0057](adr/0057-the-cursor-rides-on-the-painted-box.md),
  [ADR-0059](adr/0059-a-control-is-a-record-a-node-and-a-rule.md)
- ~~**A knob inside a scroll view is still untested, and `Kind.WHEEL` had
  exactly one consumer for a long time.**~~ **Both cases are tested, and the one
  the entry predicted would fail did.** These were two entries saying the same
  thing from either end, and they close together. The wheel *route* had been
  covered since ADR-0061 — a fabricated `SDL_MouseWheelEvent` through the real
  translate and the real sink — but until `scroll` shipped there was nothing above
  a knob for an unconsumed wheel to reach, so the half of the contract that is
  about **not** consuming had never been run. `Knob.wheel` consumed
  unconditionally; it now consumes what it moved, which is `ScrollViewport`'s
  existing rule applied to a second widget rather than a new one invented for it.
  `KnobChainingTest` is the first test in the catalog to drive a wheel through a
  real bubble **between two widgets**, and the arrangement is what took the work:
  the list has to be scrolled off its top first, or the viewport's own edge rule
  refuses the wheel and the test passes before the fix. What the exercise turned
  up is one entry it did not close, above: a *disabled* control swallows a wheel
  for a reason that is the router's rather than the knob's. —
  [ADR-0236](adr/0236-a-wheel-is-consumed-by-whatever-it-moved.md),
  [ADR-0089](adr/0089-a-knobs-gesture-is-a-rate.md),
  [ADR-0116](adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)
- ~~**The overlay enter/exit lifecycle is a specification without a subject, and
  the imperative `AnimationController` has now lost all three of its own.**~~
  **The survey is done, and the answer is two objects rather than one
  controller.** The *arrival* needs nothing shared — `Phase` is already the whole
  of it, and six widgets use it without wanting more. The *departure* was the same
  code twice: `dialog` and `message` each held two flags, a timer and six lines,
  and independently got the same four rules right — idempotence, two flags that
  mean different things, stop-drawing-before-telling, and gone-at-once under
  reduced motion. That is a mechanism waiting to be named, and it is `Departure`.
  It is still not an `AnimationController`: it drives no value, interpolates
  nothing and owns no clock. What the survey also settles is that `toast` and
  `tab` must **not** be converted — a toast's departure ends when its stack's
  queue says so and a tab's ends inside `render` — which is ADR-0092's rule about
  generalising from examples that already agree. —
  [ADR-0234](adr/0234-the-overlay-lifecycle-is-a-departure-and-a-phase.md),
  [ADR-0178](adr/0178-a-stack-closes-its-own-hole.md),
  [ADR-0081](adr/0081-a-perpetual-loop-has-no-state.md)
- ~~**Overlays do not animate in or out.**~~ **They do, and this was fixed by the
  widgets rather than by the layer — which is what the entry itself predicted.**
  §1.7's overlay curve wanted "a toast to arrive rather than appear, which is a
  transition on the widget and not on the layer": a toast slides 16px from its
  edge and reflows when a sibling goes ([ADR-0177], [ADR-0178]), a dialog scales
  from 0.96 and fades ([ADR-0176]), a banner rises 2px ([ADR-0175]). The `stack`
  half of this entry stays open above, because it is a layout widget and this was
  never about one. —
  [ADR-0100](adr/0100-a-window-has-a-layer-above-its-application.md)
- ~~**Two popups do not know about each other.**~~ **They still do not, and they
  do not have to: what was wrong is that `Escape` and a press outside were the
  same code.** A press that lands somewhere else is the user pointing at something
  other than the menu, and the whole chain goes; `Escape` is the user stepping
  back out of what they opened, one menu at a time. The launcher ran
  `dismissPopups()` for both, so opening `File → Recent` and pressing `Escape`
  took the parent with the submenu. Finding *which* handler was at fault was most
  of the work — a `Popup` watches its own window and closes only itself, which is
  correct and never runs, because since ADR-0189 no popup holds the platform
  keyboard and the key reaches the **owner**. One more thing the entry did not
  say: the innermost popup is not always the one that goes, because a tooltip
  refuses light dismissal — so the walk looks past it rather than stopping. —
  [ADR-0233](adr/0233-escape-steps-out-of-one-menu.md),
  [ADR-0103](adr/0103-a-popup-is-a-second-tree-in-a-second-window.md)
- ~~**A tooltip is plain text, has no maximum width of its own and does not
  follow the pointer.**~~ **All three are what §7 specifies for v1, so they are
  boundaries and not gaps** — and an entry that records a boundary belongs here
  rather than on a list of what is missing. All three are what "rich content"
  would change, and none of them has a caller asking. The **delay** is a different
  matter and stays open above: it is a number this toolkit chose rather than one
  §7 gave, and the token that would let an application change it cannot be read.
  —
  [ADR-0105](adr/0105-a-tooltip-is-an-attribute-not-a-widget.md)
- ~~**`Placement` still clamps, and now two callers have stopped asking it
  to.**~~ **That is the design, stated, and there is nothing here to build.** A
  popup taller than the work area is clamped to the near edge; `menu` and `select`
  cap their own content first, from a measurement rather than a guess ([ADR-0179]).
  Any other caller that opens an oversized popup and offers no `Host.Fit` gets the
  clamp — which is right for a facility that cannot know what its content means: a
  tooltip that scrolled would be a tooltip that should have been a dialog. The
  entry read as a gap because it opens with "still", and what follows it is a
  division of responsibility rather than a shortfall. —
  [ADR-0179](adr/0179-a-popup-says-what-it-measured.md),
  [ADR-0104](adr/0104-a-popup-is-measured-then-placed.md),
  [ADR-0118](adr/0118-a-popup-that-does-not-fit-scrolls.md)
- ~~**Nothing hit-tests an overlay by rule.**~~ **Both halves are rules now, and
  the second was two mechanisms pretending to be one.** The topmost painted region
  taking the pointer was already true — the capture is in paint order and is
  scanned backwards — and is written on `elementAt` with a test that fails if it
  stops being. The modal half was worse than unwritten: `Handles.isModal` said in
  as many words that "the pointer is not this flag's business", because a dialog
  is unreachable by mouse through its *scrim*. That is modality by geometry, and a
  widget that declared itself modal without a scrim trapped the keyboard and let
  every click through — the two halves of "modal" disagreeing in an accessibility
  feature. It is one flag now: the pointer reaches the modal's **subtree** and its
  **ancestors**, and nothing else. The ancestors are the point rather than a
  loophole, because a scrim is the panel's parent and a click on it is what closes
  the dialog. —
  [ADR-0232](adr/0232-modality-is-one-flag-and-not-a-scrim.md)
- ~~**`PointerRouter` has one listener slot, not a list.**~~ **It is a list, and
  the decision the entry was waiting for is that there is nothing to decide.**
  What is delivered is a **notification and not an event**: nothing is passed,
  nothing can be consumed, and each listener reads `hovered()` or `focused()` from
  the router for itself — so no listener can change what another sees and order is
  not a policy. An *event* — one carrying a target, or consumable — would be the
  shape worth refusing, and is the one ADR-0105's objection was aimed at. The slot
  also had a bug the entry had not noticed: a setter named `onPointingChanged`
  reads like a registration and behaved like an assignment, so a second caller
  silently dropped the first and a tooltip simply stopped appearing. It hands back
  a `Subscription` now, which the slot could not express at all. —
  [ADR-0230](adr/0230-a-notification-has-listeners-and-an-event-has-one.md)
- ~~**`--gb-*-line` has one consumer, and the widgets that should be next have
  not been looked at.**~~ **The survey is done, and it found a rank missing rather
  than a rank unused.** Six rules drew ink in a bare semantic hue and only **one**
  of them was a line — the `field:invalid` edge the entry named. The other four
  were *words*, and §1.2's floor for words is 4.5:1 where `-line` is derived
  against 3:1, so pointing them at `-line` would have moved them from clearly
  wrong to quietly wrong: `--gb-danger-line` is 3.53:1 on the dark theme's
  surface. So a hue has a fourth rank, `--gb-<hue>-text`, and the HUD has two
  tokens of its own because its plate is the same in both themes and a
  theme-varying red on a near-black plate is an absence rather than a warning. The
  `badge` half of the entry was a false memory: a badge is a filled chip with its
  own pair and has no border. Eight golden images changed, every one of which had
  been recording a colour below §1.2's floor. And the survey is a **lint** now —
  `noBareHueDrawsInk` reads the stylesheet, which is the one question a contrast
  measurement cannot answer. —
  [ADR-0229](adr/0229-a-hue-has-a-rank-for-words-as-well-as-for-lines.md),
  [ADR-0175](adr/0175-a-banner-says-its-kind-twice.md)
- ~~**`collapse` and `carousel` never stop asking for frames.**~~ **They ask
  their `Phase` now, and it was the same three lines the entry predicted.** The
  question the frame loop asks is *are you still moving*, and both were answering
  *were you built in a state where you could move* — a `carousel`'s was never even
  conditional, so every window with one on it repainted at the refresh rate for
  ever. A phase settles itself on the frame that finishes it; a `DoubleUnaryOperator`
  closing over one cannot say whether it has. Two things the entry did not
  predict: a section **shut half way through an arrival** keeps a phase nothing
  will ever settle, so `CollapseSection` guards on `open`; and `CarouselTest`'s own
  `animating()` case asserted the bug, because it had been written against the
  implementation rather than against §1.7. —
  [ADR-0228](adr/0228-a-phase-is-asked-whether-it-is-still-running.md)
- ~~**Every clock-driven arrival costs one wasted frame.**~~ **It does not, and
  "harmless and worth writing down" was one line short.** The entry had the
  diagnosis exactly right — the renderer asked whether a node was animating
  *before* it drew it — and drew the wrong conclusion from it: a phase learns it
  has finished by being **read**, and the only place a widget is handed the frame
  clock is `render`. Asked afterwards, the answer is current. One line moved, and
  it is worth one frame of every animation in the toolkit. `TabMotionTest`
  documented the waste in a comment and now asserts its absence. —
  [ADR-0228](adr/0228-a-phase-is-asked-whether-it-is-still-running.md)
- ~~**A `message` takes no `bind=`, so a banner whose text comes from a model has
  to be described away rather than emptied.**~~ **It takes one, and the thing that
  was missing was a *name* rather than a mechanism.** The entry had already named
  the fix — "a way for a widget to describe nothing, which the element tree has no
  word for" — and what looking at the renderer showed is that the tree could
  always do it: a node that is neither `Styled` nor `Paints` and has no children
  contributes no box, which is how every composition node already works. So
  `Widget.nothing()` is a singleton leaf and **no new branch anywhere**. A bound
  banner whose value is blank is not there, and comes back when the value does —
  the same element, the same subscription, the same arrival, which is what makes
  this a value change rather than a node being rebuilt. The dismissed case
  converged on it and stopped leaving a gap. What is **not** converted is
  `field-message`: doing so changes the spacing of every form, which five golden
  images say is a design decision about §4's "message slot" rather than a bug fix.
  —
  [ADR-0227](adr/0227-a-widget-may-describe-nothing.md),
  [ADR-0175](adr/0175-a-banner-says-its-kind-twice.md)
- ~~**A closing overlay used not to animate at all**, and every golden
  passed.~~ **The lesson has somewhere to live now, and it is a test rather than a
  paragraph.** The entry was right twice — the corpus cannot catch this by
  construction, and an assertion on `isAnimating` is the only thing that can — and
  stopped one step short: the second half is a *specification for a test*, and
  left as prose it is read by people who already know. `AnimationSweepTest` is
  that test, in two rules: a widget holding a `Phase` declares `isAnimating`
  (structural, scoped to things that actually paint, or it names six false
  positives and gets deleted), and every declaration of `isAnimating` has a test
  beside it that names the method (which catches the animations a `Phase` does not
  describe — a tab's number, a scrollbar's idle clock). It found a real gap on its
  first run: `ScrollViewport` and `ScrollFade` had no such assertion anywhere. —
  [ADR-0226](adr/0226-a-golden-cannot-see-an-animation-that-never-ran.md),
  [ADR-0176](adr/0176-a-dialog-is-a-widget-and-showing-one-is-not.md)
- ~~**A right-click does not select what it is over.**~~ **It does, and the
  premise was half right.** The toolkit still has no notion of what "select"
  means for an arbitrary widget — but the widget *under the pointer* does, and
  what was missing was never a concept of selection: it was a **moment** at which
  a row could be told the gesture had happened to it. The launcher's existing walk
  supplies one, because it already goes from what was clicked up to whatever named
  the menu; it now remembers the deepest widget on that walk that can answer and
  asks it, once, immediately before opening. The rule the entry did not state is
  the one that makes it worth having: a row already **in** the selection leaves it
  alone, so right-clicking one of five chosen files opens a menu about the five. —
  [ADR-0224](adr/0224-a-right-click-selects-what-it-is-over.md)
- ~~**A bare `Alt` tap does not activate the menu bar, and `F10` does.**~~ **It
  does, and the entry had already written the design.** "Key-release tracking with
  a nothing-happened-in-between rule, at the window level" is exactly what shipped
  — the part it did not predict is *where the keycode has to be read*. `Key` names
  no modifier on purpose, so `Alt` reaches the router as `Key.UNKNOWN` and is
  indistinguishable there from every letter that arrives as text; the platform
  keycode is the only place the distinction survives, and `Window` is the last
  component that holds one. What is bound is not a `Shortcut` at all but a
  gesture, with its own tiny vocabulary and an exhaustive list of what spoils it —
  another key, a repeat, a second modifier, a press, a wheel, a focus change, and
  deliberately *not* pointer motion. `F10` stays beside it as the binding that
  survives a compositor which eats `Alt`, and both toggle now. —
  [ADR-0223](adr/0223-a-tap-is-a-gesture-and-a-shortcut-is-a-value.md)
- ~~**`menubar` is not built, and it wants a menu that outlives one opening.**~~
  **Both halves ship, and the model that outlives an opening turned out to be the
  one the author already wrote.** What is built and discarded per opening is the
  *popup*; a `Menu` is a value, so a `menubar` holding one holds it for as long as
  the bar is mounted. `Accelerators` walks that description and binds every
  command with a key on it, with no menu on screen and none needed. A bar's
  children are `item`s and a nested `item` is a heading, so no markup was added. —
  [ADR-0163](adr/0163-a-menu-bar-owns-its-menus.md),
  [ADR-0106](adr/0106-a-menu-is-a-widget-and-opening-one-is-not.md)
- ~~**`Left` and `Right` do not move between menus while one is showing.**~~
  **They do, and fixing either did fix both.** The missing item-to-popup callback
  is `MenuSignals`, and a bar hands its root menu a `Menus.Siblings` saying what
  the two arrows that leave it mean — wrapping at the ends and skipping a
  separator or a disabled heading. A submenu gets none, which is what keeps
  `Left` in one going back a level rather than leaping along the bar. —
  [ADR-0219](adr/0219-an-item-tells-its-menu-what-the-keyboard-did.md),
  [ADR-0163](adr/0163-a-menu-bar-owns-its-menus.md)
- ~~**An accelerator is unbound by key, so a `menubar` going away can take
  somebody else's binding with it.**~~ **The map remembers owners now, and
  `menubar` is the only thing that uses it** — which is what the entry predicted.
  A binding is `(action, owner)` compared by identity; `removeShortcut(key)`
  still removes whatever is there, and `removeShortcut(key, owner)` is a no-op
  when somebody else has taken the key since. The bind side is unchanged: two
  commands on one key is still last-writer-wins, and what changed is that the
  loser cannot unbind the winner. **A displaced binding is still not restored** —
  that needs a stack per key, and nothing has asked for one. —
  [ADR-0220](adr/0220-an-accelerator-is-given-back-by-whoever-took-it.md),
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
- ~~**A keyboard `Right` into a submenu waits 150ms.**~~ **It opens in the same
  frame.** `Item` can tell a hover from a keypress now: `hovered()` is what the
  pointer did and `open()` is what a key did, and the delay is for the pointer —
  it stops a submenu dropping out of one travelling past three rows, and a
  keypress has travelled past nothing. —
  [ADR-0219](adr/0219-an-item-tells-its-menu-what-the-keyboard-did.md),
  [ADR-0112](adr/0112-a-menu-follows-the-pointer-and-lights-for-the-keyboard.md)
- ~~**`Left` does not close a submenu.**~~ **It does, and it is the arrow that
  opened it, undone.** In a submenu it closes back to the menu above; at the root
  of a bar's menu it moves along the bar; at the root of a context menu it does
  nothing, deliberately — a menu that vanished on an arrow key would be a menu
  nobody could navigate, and `Escape` is the key that means "put this away". —
  [ADR-0219](adr/0219-an-item-tells-its-menu-what-the-keyboard-did.md),
  [ADR-0112](adr/0112-a-menu-follows-the-pointer-and-lights-for-the-keyboard.md)
- ~~**Nothing marks the row whose submenu is showing.**~~ **`item.open` does.**
  The row keeps `:hover`'s wash for as long as its branch is on screen, which is
  what the pointer moving *into* the submenu made visible: the row it came from
  went plain while its submenu was still showing. A class rather than a
  pseudo-class, because "the branch that is showing" is a menu's own bookkeeping
  and not a state the element tree tracks — the shape `menu-title.open` already
  used. —
  [ADR-0219](adr/0219-an-item-tells-its-menu-what-the-keyboard-did.md),
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
- ~~**A field refuses right-to-left text outright.**~~ **It takes it, and draws
  it mirrored.** The interim this entry asked for is chosen: a paragraph shapes
  bidi text with the direction forced to `LTR`, so the glyphs are right, their
  order is not, and every width, caret and hit test agrees with what is on
  screen. It says so — `Paragraph.isBidiApproximate()` and one warning per
  distinct string — because the alternative to a crash should not be a silence.
  **What is still ahead is the real thing**: `java.text.Bidi` run splitting,
  which is several runs per line, visual reordering within a line, and a caret
  that knows which run it is in and which side of it. That last part is why the
  half-measure of handling *uniformly* right-to-left paragraphs was not taken —
  it changes what `widthBetween` means to every caller, which is the same change
  full bidi needs. M5, with the IME preedit it sits beside. —
  [ADR-0218](adr/0218-a-paragraph-approximates-bidi-rather-than-refusing-it.md),
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
- ~~**A `select` opened from the keyboard does not give focus back to the
  field.**~~ **The field never loses it.** Measured rather than reasoned about:
  the owner window's router is not touched by a popup opening or closing, so the
  field keeps both its focus and its ring for as long as the list is up. What
  remains is the platform-level question above, which is not a control's problem
  and not this control's in particular. —
  [ADR-0180](adr/0180-the-keyboard-goes-back-where-it-was.md),
  [ADR-0104](adr/0104-a-popup-is-measured-then-placed.md)
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
- ~~**`collapse`'s `accordion=` is not built.**~~ **It ships, as a widget rather
  than as a flag on `column`.** The flag belongs on the container — "one open at a
  time" is a rule about siblings — but honouring it needs state, and statefulness
  is a property of the *type*: putting it on `column` would give every column in
  every document a `State` it never uses. `column accordion=#true` inflates to an
  `Accordion` that reports `column` as its own CSS type, so the document writes
  what §5 says and an ordinary column pays nothing. —
  [ADR-0166](adr/0166-a-raised-thing-is-told-apart-by-its-edge.md)

- ~~**Per-corner radii do not exist, and `segmented` is the second control that
  wanted one.**~~ **They exist, `segmented` uses them, and the fourth asking is
  what built them.**
  `button.square` asked first, `segmented` second — both went round the outside,
  the bar keeping the radius and the segment inset. `group-box-title` could not:
  its top corners meet a rounded frame and its bottom ones meet the body, and no
  arrangement of nodes fakes that. It had been writing `border-radius: 7px 7px 0
  0` since it shipped, and the engine had been dropping the declaration with a
  warning nobody read. `Corners` is four numbers over CSS's 1-4 shorthand, the
  uniform case emits the drawing it always did, and elliptical corners are still
  refused. `SegmentedTest`'s pinned numbers did what they were pinned for: the
  bar is drawn joined again, with §3's hairline between its cells, and the design
  system's row is amended back. `button.square`'s joined buttons and `tabs` are
  the two callers of `Corners.inRow` that have not arrived yet. —
  [ADR-0217](adr/0217-a-segmented-control-is-joined-again.md),
  [ADR-0216](adr/0216-a-corner-is-four-numbers-and-a-lint-reads-values-too.md),
  [ADR-0097](adr/0097-a-selection-that-travels-needs-a-geometry.md)
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
  **(earlier)**
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
- ~~**`flex-wrap` is not in §8's subset.**~~ **It is, and it took the shape this
  entry predicted** — one component on `Box`, one on `ComputedStyle`, one line in
  the render tree, and 48 positional reconstructions. What it did not predict is
  the half that mattered: putting the property on the *field* wraps the chevron
  onto a second line under the chips, so the chips needed a box of their own. A
  golden image is what said so; nothing in the CSS looked wrong. —
  [ADR-0192](adr/0192-a-row-of-chips-wraps-and-the-chevron-does-not.md)
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
- ~~**There is no italic face, and an application has asked for one.**~~ **Built, as two
  files rather than one.** `docs/gaps.md` G27 wanted italic beside underline and
  strikethrough; the other two came with
  [ADR-0321](adr/0321-a-rule-under-text-belongs-to-the-face.md) and the faces with
  [ADR-0323](adr/0323-an-italic-is-a-face-and-the-matrix-closes.md). It was ADR-0066's
  question one step on — an italic is a *face*, because Inter's italic is drawn rather
  than slanted, and shearing the upright glyphs is a type-design decision rather than a
  workaround. **Two faces, so the matrix closes**: one would have left semibold italic
  resolving to the nearest of three, which is how a design system acquires a weight
  nobody chose. Matching is CSS's order (family, style, weight), so italic code stays
  upright code; `oblique` is dropped with a warning. The variable-axis answer ADR-0066
  deferred stays deferred, and stays the right change the day an *intermediate weight* is
  specified — which is still nothing. — [ADR-0323](adr/0323-an-italic-is-a-face-and-the-matrix-closes.md)
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
  shaped in **logical** order and therefore drawn mirrored, because HarfBuzz returns
  those glyphs in visual order and prefix sums taken in logical order would otherwise
  measure the wrong ones (it was refused outright until ADR-0218, which cost a window
  every time somebody pasted Arabic into a field). Font fallback between the UI and
  emoji slots — the thing that makes a paragraph several runs rather than one — **is
  built**: the itemizer splits emoji out by UTS #51's sequence rules and a paragraph
  takes one measurement over up to two shapings, with the emoji face's advances
  rescaled into the base font's design units. —
  [ADR-0218](adr/0218-a-paragraph-approximates-bidi-rather-than-refusing-it.md),
  [ADR-0393](adr/0393-an-emoji-is-routed-by-the-text-and-drawn-in-layers.md),
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
