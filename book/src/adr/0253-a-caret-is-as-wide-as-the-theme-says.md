# 253. A caret is as wide as the theme says

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0167](0167-a-field-owns-its-caret-and-the-model-is-told.md), which
[ADR-0251](0251-a-widget-may-read-a-token-and-a-nested-scroller-is-named.md)
unblocked.

## Context

> `--gb-caret-width` is not a token and the caret is one logical pixel. The width
> is set in the same call that sets the caret's position, so a stylesheet that
> disagreed would move it rather than resize it. A theme that wants a fat caret
> is a design-system decision and a token, which is Principle 3's order.

The diagnosis is right and the phrasing understates it slightly: the width is set
by `Box.size` and the position by `Box.inset`, two calls rather than one — but
both run **after** the cascade, so a `caret { width: 3px }` is *overwritten*
rather than honoured. Either way the conclusion holds: `width` is the wrong
spelling for this, and the right one is a token.

The token was not shipped because nothing could read one. That is what ADR-0251
changed.

**And it is not a preference.** A thicker caret is a low-vision aid, which is why
§13 lists that kind of switch — so the number being unreachable was an
accessibility gap wearing a styling question's clothes.

## Decision

**`--gb-caret-width`, defaulting to 1px, read through
`Paints.Context.length`.**

### One constant, in a package both controls can see

`text-input` and `text-area` each had their own `CARET_WIDTH = 1`, and the
second's comment said it was the first's. Two constants that must agree and
cannot see each other is one constant with a comment where the compiler should
be. `widgets.form.Carets` holds the number and the token name; both controls read
it, and a test asserts they agree.

### It is read once and used in three places, not two

The site the entry did not mention is the one that would have made a fat caret
wrong. `TextInputState.laidOut` computes the scroll offset with

```java
var offset = Math.max(scrollOffset, caretAt - room + 1);
```

— where the `1` is *the caret's own width of room*, so the field does not scroll
one pixel short of showing it. A three-pixel caret against a one-pixel reserve is
a caret clipped at the end of the text. `laidOut` takes the width now, which is
possible because it is already called from `render`, where the context is.

## Alternatives considered

- **Ship the token and leave the scroll reserve at 1.** It is the smaller diff
  and it makes the feature quietly wrong at exactly the width somebody would set
  it to. A token that is honoured in two places out of three is worse than no
  token, because the failure looks like a text-rendering bug.
- **Make the caret a real element with a `width` the cascade resolves.** Its box
  is computed from the shaped paragraph in the same `render` that positions it;
  splitting that so the cascade could own one dimension means the field measuring
  text in one pass and placing the caret in another.
- **Put the constant in `text-input` and have `text-area` import it.** It is
  package-private and in another package, so it would have to become public API
  of a control — a widget exporting a number for another widget.
- **Bank it into the state**, as `scroll` does with `--gb-scroll-line`
  (ADR-0251). Unnecessary here: every consumer of this number is reached from
  `render`, so nothing has to survive until an input arrives.

## Consequences

- **Four tests, two of which fail against the old code**, and the two that fail
  are the ones that matter: an override is honoured, and `text-area` honours the
  same one. The default case passes either way, which is the point of keeping it.
- **The tests find the caret by its width rather than by counting children**,
  because a field's anatomy changes and an index into it is a test that breaks
  for an unrelated reason.
- **`TextEditor.laidOut` gained a parameter**, which is an interface with one
  implementor and one caller.
- **Two dangling doc comments were left behind** when the constants moved, and
  `-Werror` with `dangling-doc-comments` refused the build until they went. Worth
  recording as the check doing its job on a refactor rather than on new code.
- **`--gb-caret-width` is the third component-token default to ship since a
  widget could read one**, after `--gb-scroll-line`. `--gb-list-row-height` is
  still waiting, and still on the other door: its number is an API argument
  consumed in `children()`, not in `render`.
