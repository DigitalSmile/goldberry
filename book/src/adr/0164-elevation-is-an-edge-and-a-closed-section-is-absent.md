# 164. Elevation is an edge, and a closed section is absent

Date: 2026-08-20

## Status

Accepted. Builds five of §5's seven remaining containers — `card`, `group-box`,
`statistic`, `skeleton` and `collapse`. `split-pane` and `carousel` are not built.

## Context

`docs/core-widgets.md` §5 lists nine containers. `panel` and `tabs` were built
([ADR-0107](0107-a-tab-strip-is-a-model-a-header-and-a-panel.md)); the other
seven were untouched. Most of them are ordinary composition work with no design
question in front of them — but three of the five taken here run straight into a
limit of §10's CSS subset or of §1.7's motion rules, and each needs the answer
written down rather than discovered again by the next person.

The supported property list is the whole of it:

> `align-items background background-color bold border border-color
> border-radius border-width bottom color cursor flex-direction flex-grow
> flex-shrink font-family font-size font-weight gap height inset justify-content
> left line-height opacity outline outline-color outline-offset outline-width
> overflow padding padding-* position right top transform transform-origin
> transition width`

There is no `box-shadow`, no `display`, no per-edge border longhand, and no
`min-width`. Three of §5's descriptions ask for something in that gap.

## Decision

### `card`: elevation is an edge

§5 asks for "elevated surface: **shadow tokens**". There are none, and nothing in
this toolkit paints outside a box's own rectangle.

So a card is raised by **contrast**: `--gb-surface-2` where the page is
`--gb-bg`, plus a border. `popover` reached the same answer first and its
stylesheet says so — "the elevation is a border rather than a shadow: §8's subset
has no `box-shadow`, and a floating panel with no edge at all disappears into a
[page]".

This is not only a workaround. A shadow says "nearer" by faking a light source; a
lift in tone and a defined edge say it by contrast, and contrast is what a
rasterizer with no shadow pass can actually express. `PanelsGoldenTest` carries
the check that it *works*, on both themes, because "does this read as raised" is
not something an assertion can answer.

`class="interactive"` is §5's optional hover-elevation, opt-in because a card
that lit up under the pointer would promise it does something and most cards do
not.

### `group-box`: the title is above the frame, not through it

A `fieldset` puts its legend **on** the border, with the frame broken behind the
words. Reproducing that needs either a notch in a border — nothing in the subset
expresses one — or the title absolutely positioned over the frame with the page's
own background painted behind it, which is wrong the moment a `group-box` sits on
anything but the page.

So the title sits **above** a bordered body. That is what a settings cluster looks
like in every desktop written this decade, and it is better at small widths
besides: a legend through a border must fit on one line or the frame breaks, and a
heading above one simply wraps.

The frame is a widget of its own (`group-box-body`) because a border on the outer
box would enclose the title as well — `tab-rule`'s argument
([ADR-0107](0107-a-tab-strip-is-a-model-a-header-and-a-panel.md)), one widget
later.

### `collapse`: a closed body is absent, not hidden

§5 is explicit and the reason is the whole argument for a widget tree: "a
collapsed section that kept a live subtree would keep its subscriptions, its
images and its scroll position alive for content nobody can see, and 'cheap to
rebuild' is what the widget tree is for"
([ADR-0004](0004-three-tree-retained-declarative-model.md)).

So a closed `collapse` **describes one child**. Not a child with `display: none`,
which the subset has not got; not a child of zero height, which would still be
built, still be subscribed and still be laid out. `CollapseTest`'s central
assertion is therefore an absence — no body node, and the author's own widgets
never constructed either.

The height does not animate, and never will: §1.7's whitelist is `opacity` and
`transform` precisely so that a transition can never cost a reflow. The chevron
turning is what says the section opened — and it is `CHEVRON_END` **rotated by the
stylesheet** rather than `CHEVRON_DOWN` swapped in, because a mark that changed
kind would jump where §5 wants it to travel. That forced the chevron to be a real
node: a transform is resolved for an element, so a mark drawn inline by the header
could never turn.

`Left` and `Right` are **absolute** rather than toggles — `Right` on an open
section leaves it open — which is what §5 asks for and what lets somebody hold
`Right` down a list of sections and open all of them.

### `skeleton`: the one loop in the canon, computed from the clock

§5 makes this the single exception to §1.7 rule 4. A loop is **not a transition**:
a transition runs between two states and a skeleton has one. So the pulse is a
pure function of `Context.nowMillis()`, which is `spinner`'s arrangement exactly
and for the same reason
([ADR-0081](0081-a-perpetual-loop-has-no-state.md)) — no controller, no start, no
stop, every skeleton on screen in step by construction, and one that unmounts
leaves nothing behind.

A **triangle wave**, folding at the halfway point, so the two ends meet and the
pulse does not snap once a second. Between 0.45 and 1.0 rather than 0 and 1: a
placeholder that fades to nothing is a layout that flickers empty, and one at full
strength is indistinguishable from content.

**Reduced motion holds it at its dimmest**, not its brightest and not the average.
A placeholder frozen at full strength reads as content that arrived and was blank.

### `statistic`: the toolkit never formats, and a direction is a sentiment

§5's reason for taking a string: "a locale-aware number formatted inside the
toolkit makes a golden image that cannot be reproduced on another machine".
`12,480` is `12.480` in half of Europe.

`direction` names the **sentiment**, not the arithmetic — latency falling is
success — so the caller picks it and the widget never infers it from a leading
`-`. Inferring would colour a latency improvement red. The colour itself is a
class on the delta (`.up`, `.down`) and therefore the stylesheet's; a widget that
looked up `--gb-success` itself would be the only one in the catalog doing so.

## Alternatives considered

**Add `box-shadow` to the subset.** It is one property and it would settle `card`,
`popover` and eventually `dialog` together. It is also a second rasterization pass
per shadowed box — a blurred alpha mask composited under the box — on a CPU
rasterizer whose whole frame currently costs about 320 µs. §10's subset is small
on purpose, and this is exactly the kind of addition that is cheap to write and
permanent to pay for.

**Give `card` a title.** §5 says "group with optional label", and it reads like a
field. It is the *accessible* name, which arrives with the AccessKit bridge in M5
along with every other widget's — and a card with a title is a `group-box` with
different tokens, which would leave two widgets doing one job.

**Animate a `collapse`'s height.** Every other toolkit does it and it looks good.
It is a layout pass per frame for the whole subtree, which is what §1.7's
whitelist exists to prevent.

**Make the shimmer a CSS transition between two opacities.** No transition loops,
and adding a loop to the transition engine would put §1.7 rule 4's one exception
inside the mechanism every ordinary animation goes through — where the next widget
to want a loop would find it already built.

## Consequences

**A record component cannot be called `children` when `children()` is
overridden.** `GroupBox` described its *parts* — the heading and the frame — from
`children()`, which is also the record accessor for the author's widgets, so
asking a group box what was in it returned its own chrome. Caught by a test that
inflated one node and was told it had two. The component is `content` now, and the
same trap is waiting for any widget that has both.

**The skeleton goldens needed a virtual clock, and found out the hard way.** A
widget that draws from `nowMillis()` renders differently every run, so its first
two goldens could never have matched — `ProgressGoldenTest` already had the answer
(`Clock.virtual()`), and this is the second widget to need it. The pinned instant
is the fold at 500 ms; the first guess was 250, which is a quarter of the way in
and not the peak.

**The five widgets add eleven CSS-selectable node types.** `card`, `group-box`,
`group-box-title`, `group-box-body`, `statistic`, `statistic-label`,
`statistic-value`, `statistic-unit`, `statistic-delta`, `skeleton`,
`skeleton-bar`, `collapse`, `collapse-header`, `collapse-chevron`,
`collapse-body`. Most are parts in the ADR-0065 sense: styleable and not
constructible.

**`split-pane` and `carousel` are not built.** Both need something the five here
did not: a drag with a retained position and a keyboard equivalent for the first,
and a timed rotation that pauses on hover, on focus and under reduced motion for
the second. Neither has a design question outstanding — they are the remaining
work, and they are in `TODO.md`.

**A `skeleton` keeps the frame loop awake even under reduced motion.**
`Paints.isAnimating()` is a property of the description and takes no `Context`, so
it cannot see the preference. `spinner` has had the same shape since ADR-0081 and
pays the same cost: a frame's worth of paint on a still image. Closing it means
giving `isAnimating` the context, which is an SPI change for two widgets.
