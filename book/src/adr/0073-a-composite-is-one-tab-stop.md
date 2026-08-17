# ADR-0073: A composite is one Tab stop, and the selection is the roving position

- **Status:** Accepted
- **Date:** 2026-08-17
- **Relates to:** `docs/ARCHITECTURE.md` §7.2, §9, §11;
  `docs/core-widgets.md` §3; `docs/design-system.md` §3, §7.2; extends
  [ADR-0059](0059-a-control-is-a-record-a-node-and-a-rule.md); applies
  [ADR-0063](0063-data-flows-down-events-flow-up.md); confirms
  [ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)

## Context

`button` and `checkbox` are single controls: one node, one Tab stop, one value.
`radio-group` is the first widget that is **a set**, and three things that were
trivially true for a single control stop being true for it.

**Traversal.** `docs/design-system.md` §7.2 is explicit: "composites (radio
groups, menus, lists, tabs) are one Tab stop with roving arrow-key focus inside".
Six options that take six Tab presses to cross is the thing that rule exists to
prevent. Nothing in the toolkit could express it — `PointerRouter.moveFocus`
collected every focusable node in the tree in document order, and a radio is a
focusable node.

**The invariant.** "Exactly one of these is on" is a fact about the set. No radio
can hold it. A radio that owned its own checked state would let a document
describe two selected options, or none, and every consumer of the group would
then need a rule for what that means.

**The action.** `button press="save"` needs no argument — there is one thing to
say and the button is it. `radio-group change="pickTheme"` is the first case
where the handler is useless without knowing *which* option was picked, and
`Actions` mapped a name to a `Runnable`.

## Decision

### Traversal is the router's, and a composite says so with one method

`Handles.focusScope()` — false by default. A scope contributes **exactly one**
entry to the Tab order, and the arrow keys move focus within it.

Both halves are the router's, not the widget's, by the same argument already
written on Tab: *which node an arrow key reaches is a property of the group's
shape, and the radio the focus is currently on cannot see its siblings.* A widget
also has no route to the router — `Handles` receives events, not the object that
dispatched them — so a widget-side implementation would have needed a new
back-channel before it could have been wrong for the right reason.

Arrow keys are handled **after the focused chain has declined the key**, in the
same place accelerators are. A slider stepping its value and a text field moving
its caret both consume the arrow and keep it, and neither has to know it is
inside a group. `Home` and `End` reach the ends. A modified arrow is not
traversal.

A widget that is **both** focusable and a scope contributes one stop, not two:
the scope is asked first and its entry is what goes into the order. Asked the
other way round such a widget would be reachable twice by Tab, and the second
arrival would have no arrow keys at all, because a scope is found strictly
upwards from the focused node. `radio-group` is not focusable — the ring belongs
on the option the user is about to pick — but a toolbar plausibly is, and this is
the kind of thing that is free to get right now and expensive to notice later.

**Both axes rove.** A group's direction is the stylesheet's — `flex-direction` on
`radio-group`, which `.inline` flips — so input cannot know which pair of arrows
the user is looking at, and answering to only one pair would be wrong half the
time. That is also ARIA's rule for a radio group. A composite that genuinely has
an axis (a tab list along the top, a menu bar) will have to say so; nothing needs
that yet.

### The entry point is derived from `:checked`, not remembered

Tab into a group lands on the focusable descendant matching `:checked`, or on the
first if none does. Computed fresh on every traversal.

This is the decision worth the record. The obvious implementation of "roving
focus" is a stored roving position — a map from scope to last-focused child —
and it is wrong in a way that only shows up later: it is a **second piece of
state beside the selection**, and the two disagree the first time an application
sets the value itself. Tab would then return the user to the option they last
*looked at* rather than the one that is *on*. There is no event that would fix
it, because the application setting a property does not know a router exists.

Deriving it means the selection **is** the roving position. Nothing to
invalidate, nothing to leak when an element unmounts, and no way for the two to
drift, because there is only one. A composite whose items are not selectable — a
toolbar — has no `:checked` anywhere and always enters at the first, which is the
right answer for it too.

`FocusScopeTest.selectionIsTheMemory` is the test that would fail for the stored
version: focus leaves the group, the model changes underneath, and Tab comes back
to the option that is now selected.

### Selection follows focus, through the application

`Handles.onFocusChanged(focused, fromKeyboard)`. A radio raises its change the
moment keyboard focus lands on it.

It does **not** move its own tick. The value goes up as an event, the application
sets the property, and the tick comes back down through the group's binding —
straight [ADR-0063](0063-data-flows-down-events-flow-up.md). So an arrow key on a
group whose handler does nothing moves the focus ring and leaves the selection
where it was, which is the visible form of "the state did not change" and is
where the bug is.

The `fromKeyboard` half is load-bearing and not decoration. A **mouse** focus
deliberately does not select, because a press moves focus and the click that
follows it activates: a radio that acted on both would fire its change twice for
one click. That is the same distinction `:focus-visible` already draws, reused
rather than reinvented.

Re-picking the option already on is a no-op rather than a toggle, which is what
makes Tab returning into a group harmless — the entry raises a change for the
value already held, and `Property.set` swallows a value it already has.

### The group holds the invariant, on every build

`RadioGroup.children()` rewrites each `Radio` with whether its `value` matches
the resolved one, what picking it does, and whether the group is disabled.
Nothing is stored, so there is no path by which two options are on at once.

`selected` is therefore **not a KDL attribute**. A document that could mark an
option selected could mark two. A `radio` inflated from markup starts unselected
and unwired, which is exactly the value a Java caller writes — so the parity
invariant holds without an exception.

A bound value that is not a `String` is compared by its `toString`, so an enum or
an `Integer` in the model works against the strings a document wrote. That is a
coercion and it is the narrow kind: it never guesses what an object *means*, only
how the author would have spelled it. A null — a model that has not loaded, or a
value from a newer document — selects **nothing** rather than falling back to the
first option, because a group that guessed would report a choice the user never
made.

A child that is not a `Radio` is laid out and left alone, so a group can carry a
heading. Silently dropping it would be a document whose text disappeared with no
error.

### An action can be told which one

`Actions.bind(String, Consumer<String>)`, resolved by
`Actions.resolveValued(name)`. The argument is the picked option's `value` — the
string the document already wrote down, so it crosses no type boundary and needs
no coercion rule. An application that wants an enum parses it in Java, where a
bad value is a bug it can see.

A plain `Runnable` resolves against `change` too, adapted to ignore the value:
`change="refresh"` is reasonable when the handler reads the model itself, and
making an author pick the matching `bind` overload would be a distinction only
the registry cares about. The reverse is **refused** — a valued action named by a
`press=` throws, naming which half of the registry it is in, because calling it
would mean inventing an argument here.

The alternative was one action per option, which would make adding an option an
edit in Java as well as in markup, and would put a name in the registry for every
value in the model.

### `:active` reaches the whole ancestor chain

A bug found by trying to write §2.1's pressed state, not by a test.

`:hover` walked the ancestor chain from the beginning — `.card:hover .title` has
to work. `:active` did not: it was set on the single deepest element the press
landed on. So pressing a checkbox's 16px glyph lit up `check-indicator`, pressing
its label lit up `text`, and `checkbox` itself matched only in the sliver of
padding between them. `checkbox:active` has been in `controls.css` since the
control shipped and was very nearly a dead rule.

§2.1 requires every control to render a pressed state, and a control whose
pressed state depends on which of its own parts you happened to hit does not have
one. `setPressed` now moves `:active` across chains exactly as `updateHover`
does, comparing them so a press that moves within one widget does not invalidate
its ancestors.

This is why `radio:active radio-indicator` and `checkbox:active check-indicator`
are written against the *control* rather than the glyph: pressing the label now
darkens the glyph, which is what a user pressing anything in a 32px row means.

### The mark stops being a mark, so it can scale

§3.1 gives `checkbox` and `radio` one row: *"check/dot: scale 0.6→1 + `opacity`,
base · color fast"*. The opacity half shipped with
[ADR-0067](0067-motion-is-an-overlay-on-a-frame-clock.md); the scale half did
not arrive with `transform` in
[ADR-0068](0068-the-transform-stack-is-java-side.md) and has been an open
question since.

`transform` was never what was missing. A `Box.Mark` is drawn **onto** the box
that carries it, so scaling the indicator scales the 16px glyph along with the
tick — the ring grows with the dot, which is not the animation and reads as a
bug. The mark needs a transform of its own, a transform belongs to a
`ComputedStyle`, and a `ComputedStyle` belongs to an element.

So `check-mark` and `radio-dot` are elements — the third and fourth parts, and
the first two justified by something other than "two surfaces need two
backgrounds". The argument is the same one in the **animation** dimension: two
things have to move independently, and the unit of independent movement is a
cascade node. That is what §1.7's whitelist is for.

Two consequences worth stating because neither is obvious:

- **The mark is built in every state, including unchecked.** A node that appears
  along with the value has no previous style to move from, and a newly built
  element deliberately starts no transition (ADR-0067's "a control appearing is
  not a control changing") — so a mark that came into existence checked would
  snap. It is present throughout and hidden with `opacity: 0`. An unchecked
  control therefore costs one fully transparent box, which is the price of the
  specified animation.
- **Unchecked draws a tick, not nothing.** `CheckMark` has to pick a shape for a
  state where none is visible, and it picks `CHECK` because unchecked → checked
  is the common transition. Going to `MIXED` swaps to the dash instantly and then
  fades it in, which is right: the *kind* of mark is not on the whitelist, and a
  tick that morphed into a dash is not what §3.1 asks for.

`radio-group-scaling.png` is the frame at 80 ms of a 160 ms transition, with one
dot growing in and the one it replaced shrinking out. The assertion it carries is
that **all three rings are the same 16px circle** — which is exactly what the
naive fix gets wrong, and which no still frame of a settled control can show.

### `radio-indicator` is the second part

[ADR-0065](0065-a-part-is-styleable-and-not-constructible.md) asked that the
part argument be **made again** rather than assumed, on the grounds that the
second instance is where a pattern either holds or turns out to have been a
special case. It holds, for the same two reasons and no new ones: a radio has two
surfaces a theme must style separately (the 32-tall row, the 16-square glyph) and
a `ComputedStyle` carries one background; and a `radio-indicator` outside a
`radio` is a circle that means nothing, so registering the node would let a
document create exactly that.

The circle needed no new drawing code. `border-radius: 8px` on a 16px box is a
circle, rounded by the four cubics
[ADR-0064](0064-a-rounded-rectangle-is-four-cubics.md) already ships — so a theme
can square a radio off without a Java change, and **no native symbol was added**.
`Box.Mark.Kind.DOT` was put in the enum with `CHECK` and `DASH` and painted then;
this is its first caller.

There is no mixed state. A group's "nothing selected yet" is *no option
matching*, not an option in a third state, which is why `:indeterminate` appears
nowhere in the radio rules.

### The rest of the design system's numbers, checked rather than assumed

Reading §1.3, §1.5, §2.1 and §2.2 against what had shipped turned up four more
divergences, all now closed and all applied to `checkbox` as well — §3 gives the
two controls **one metrics row**, so a rule that holds for one and not the other
is a spec that has stopped being true:

- **`border-radius: 4px`** on both controls. §1.5 puts small controls at 4 and
  §2.2 says the focus ring follows the control's radius; neither carried one, so
  both drew a square ring beside `button`'s 8px one.
- **Hover changes a surface**, not only a border. §2.1: "hover states change
  surface (one surface step)". The glyph *is* the control's surface at 16px, so
  the step lands on its background and its border together.
- **A pressed appearance at all** — see the `:active` section above for why there
  effectively was none. Checked controls press from their filled state, so the
  accent darkens rather than reverting to the empty surface.
- **`radio-group` gap 8, `.inline` gap 16.** It shipped at 4, which is on §1.3's
  ramp but is not what §1.3 *says*: options are related controls, and those are
  8. The inline variant takes "between groups 16" because side by side each
  glyph-plus-label is a unit — at 8 the previous label sits as close to the next
  glyph as to its own, and reads as belonging to the wrong option. Stacked, no
  such ambiguity exists, which is why the two directions legitimately differ.

### Two bugs the work uncovered, neither of them about radio

**An unnamed key crashed the window.** `keyPressed` built a `Shortcut` from every
key that reached it, to use as a map key. `Shortcut` refuses to hold
`Key.UNKNOWN` — an accelerator on it could never fire, so the constructor is
right to say so — and the resulting `IllegalArgumentException` went up the UI
thread with nothing above it to catch it. This was not an edge case: `Key` names
the keys a *shortcut* might use, so every letter, digit and punctuation mark that
arrives as text is `UNKNOWN`, and the crash was one keystroke away at all times.
The accelerator tests never saw it because they only ever pressed keys that had
names. The lookup is now skipped for an unnamed key rather than attempted.

**The glyph's rest colour was a surface token.** `--gb-checkbox-bg` was `nord1`,
which is `--gb-surface` — the exact colour of the panel a control normally sits
on — so an unchecked checkbox was invisible in the place it is most often put.
The light theme had the identical defect with `#ffffff`. The token's own comment
("one step up from the window so an unchecked box reads as a well") explains it:
it was measured against `--gb-bg`, and almost nothing sits directly on the
window.

Both glyphs now take **the button's ramp** — `--gb-button-bg` / `-hover` /
`-active` values on each theme — rather than a ramp of their own. That is the
scale §2.1's "one surface step" is already defined by, it has somewhere to go in
both directions, and it is one ramp to keep correct instead of two.

The reason CI never caught it is worth more than the fix: **every golden image in
the repository paints on `--gb-bg`.** A control that vanishes on `--gb-surface`
was invisible to the whole suite. `controls-on-surface-{dark,light}.png` put a
checkbox and a radio group on a surface panel in both themes, which is the
missing axis rather than one more scene.

## Consequences

- `radio` and `radio-group` ship: records, nodes, CSS types, the invariant,
  `bind` + valued `change`, keyboard, `:disabled`, and five golden images across
  both themes. Four of thirteen controls.
- **§7.2's group-navigation gap is closed as a mechanism, not as a special case.**
  `tabs`, `menu`, `select`'s popup list and a toolbar all get one Tab stop and
  arrow keys by returning `true` from one method. `FocusScopeTest` is written
  against bare widgets in `:core` rather than against `radio`, because the next
  three users will look nothing like a radio.
- Options are **content-sized**, not stretched: `align-items: flex-start` on the
  group. A column's flex children stretch on the cross axis by default, which
  would have run the focus ring and the click target out across empty space while
  `.inline` — a row, whose cross axis is height — kept hugging its label. The same
  widget would then have had two different hit targets depending on a class. The
  golden image is what showed it; no value assertion would have.
- `Actions.bound()` now returns `Map<String, Object>` rather than
  `Map<String, Runnable>`, because the registry holds two kinds of action. It had
  no callers. It also keeps insertion order now, which its own documentation had
  always claimed and `Map.copyOf` had never provided.
- **Open: a scope has no axis.** Both arrow pairs rove, which is right for a radio
  group and will be wrong for a menu bar, where `Down` should open a menu rather
  than move along the bar. That is a decision for `menu`, and it will need to
  distinguish the two rather than adding a second mechanism beside this one.
- **§3.1 is now satisfied for every control in `controls.css`.** The check/dot
  scale was the last row with a half missing, and closing it for `radio` closed
  it for `checkbox` too, because the mechanism is one mechanism. Four parts exist
  where there was one.
- **`checkbox` moved**, and deliberately: it gained the radius, the hover surface
  step, a working pressed state and the scaling tick. `checkbox-states-dark` and
  `-light` are **pixel-identical** — the mark refactor changes nothing at rest,
  which is the check that it was a refactor — and only `checkbox-interaction`
  moved, by exactly the ring radius and the hover step.
- **Open: a disabled group fades correctly only by an explicit undo.** The group
  is 45% and passes `disabled` down to every option, so without
  `radio-group:disabled radio:disabled { opacity: 1 }` the fade would apply twice
  and land at 20%. The general fix is the one
  [ADR-0065](0065-a-part-is-styleable-and-not-constructible.md) left open —
  `docs/core-widgets.md`'s "a disabled container disables its descendants" — and
  `form` and `group-box` are where it will have to be faced properly.
