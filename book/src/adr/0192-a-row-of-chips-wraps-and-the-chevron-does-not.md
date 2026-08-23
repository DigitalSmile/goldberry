# 192. A row of chips wraps, and the chevron does not

Date: 2026-08-23

## Status

Accepted. Adds `flex-wrap` to `docs/ARCHITECTURE.md` §8's subset, which
[ADR-0187](0187-a-panel-takes-the-pointer-and-leaves-the-keyboard.md) filed and
`select multiple=` had been living without.

## Context

§8 has listed `flex-wrap` from the beginning and nothing had needed it: every row
in the catalog was a row that fitted. `select multiple=` was the first that did
not ([ADR-0182](0182-a-select-may-hold-more-than-one.md)) — a field holding five
chosen values is wider than the field — and what happened instead was that the
chips **shrank**, because Yoga's default is one line however much it overflows
and shrinking is what a flex item does when there is nowhere else to go. So a
field with more values than it could show displayed all of them squeezed and none
of them whole.

The gap was in exactly one place. Yoga has `YGNodeStyleSetFlexWrap` bound and
`YogaNode.setFlexWrap` wraps it; `Wrap` has existed in `natives.yoga.style` since
the enums were written. Nothing above the native boundary could say it: `Box` had
no component, `ComputedStyle` had no component, and the parser had no case — so
`flex-wrap: wrap` in `controls.css` logged "ignoring unsupported property" once
per frame and did nothing. `controls.css` said so in a comment, which is the
honest form of a gap and not a substitute for closing it.

## Decision

### One component each, and the same shape `min-width` took

`Wrap wrap` on `Box` and on `ComputedStyle`, beside `alignItems`, applied to the
Yoga node in `RenderObject.apply` under the same "only when it changed" guard as
every other style. [ADR-0181](0181-a-box-may-say-how-small-and-how-large.md)
grouped four properties into `Limits` because they were one question asked four
ways; this is one property and gets one component, like `overflow` and `position`
before it.

### `nowrap` is spelled with no hyphen, and that needs a line of code

The generic keyword parser upper-cases a CSS ident and turns `-` into `_`, which
maps `wrap-reverse` onto `WRAP_REVERSE` and `wrap` onto `WRAP` correctly. It maps
`nowrap` onto `NOWRAP`, and `YGWrap`'s constant is `NoWrap` — two words. So
`flex-wrap` gets a parser of its own that special-cases the one keyword and
defers for the other two, which is the shape `overflow` already has for `auto`.

### The wrapping is on the chips, not on the field

This is the part that had to be seen rather than reasoned about. Putting
`flex-wrap: wrap` on `select.multiple` is the obvious move and produces a worse
picture than the shrinking it fixes: a field is a row of the chips **and the
chevron**, so the row wraps by dropping the *chevron* onto a second line
underneath them, where it reads as a stray mark in the bottom-left corner. The
golden image is what said so; nothing in the CSS looked wrong.

So the chips get a box of their own — `select-chips`, a **part** in
[ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)'s sense: a CSS type
selector, not a widget in the catalog, because it has no meaning outside its
parent. That box wraps and grows; the field stays the one-line row it always was.
The `flex-grow` also takes over the job the field used to give a `spacer` — a box
that grows is what pushes the chevron to the far edge, and there is now one that
does.

## Consequences

- **48 positional reconstructions gained an argument**, 26 in `Box` and 22 in
  `ComputedStyle`, which is the churn ADR-0181 described and the reason it
  grouped four properties into one value. Two of them were inserted in the wrong
  slot by the mechanical pass and were caught by the compiler, because `Wrap` is
  a type no other component has — the same protection `RecordWitherTest` provides
  for components that *are* same-typed, and the reason its fixture now holds
  `WRAP_REVERSE` rather than a default.
- **`select multiple=` shows whole chips on as many lines as it needs.** The
  control grows, which `height: auto; min-height: 32px` had been written for
  since ADR-0182 without anything able to make it happen.
- **A second golden covers it.** The existing one has three chips, which *fit* —
  and its javadoc claimed they "wrap onto a second row", which was never true and
  is the kind of sentence a picture is supposed to prevent. `select-multiple-wraps`
  has five in the same 220px field, so the corpus now holds the case the property
  exists for rather than a case that never exercised it.
- **`flex-wrap` is available to every widget and stylesheet**, and nothing else
  uses it yet. `wrap-reverse` parses and is untested beyond the parser: no rule
  in the canon asks for it, and inventing a golden for a keyword nobody writes
  would be covering the toolkit rather than the design system.
- **`align-content` is still absent**, which is what decides how wrapped *lines*
  share the cross axis. It does not matter to a chip row, whose height is its
  content; it would matter to a wrapped row in a box with a fixed height, where
  Yoga's default spreads the lines. Filed rather than added, on the rule that has
  held all through §8: a property arrives when something in the catalog needs it.
