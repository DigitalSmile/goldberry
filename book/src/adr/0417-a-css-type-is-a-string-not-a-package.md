# 417. A CSS type is a string, not a package

Date: 2026-09-19

## Status

Accepted. Closes the fourth consequence of
[ADR-0182](0182-a-select-may-hold-more-than-one.md) — "`SelectList` is public and
in the wrong package" — and corrects the reason that record gave for leaving it,
which was false when it was written.

## Context

ADR-0182 filed the move and priced it:

> `Option` was moved into a package of its own the day it had two callers, and
> this now has two; the CSS type it carries is `select-list`, so moving it means
> renaming a type in every stylesheet and every golden rather than editing one
> file. Filed rather than done.

That price is wrong, and it is wrong in a way worth recording rather than merely
fixing, because it is the only reason the move sat for two hundred and thirty-six
records.

A CSS type in this toolkit is the string `Styled.cssType()` returns. `SelectList`
returned a literal:

```java
@Override
public String cssType() {
    return "select-list";
}
```

Nothing derives it from the class. Not its simple name — `SelectList` is not
`select-list` and never was; not its package; not anything a compiler knows. The
cascade matches on the string, `controls.css` writes the string, and
`SelectGoldenTest` names its image `select-list-dark` because somebody typed that
too. Moving the `.java` file could not have touched any of them, and did not: the
whole of the stylesheet change in this record is one property on an unrelated
widget, and the goldens that moved belong to [ADR-0419](0419-the-indeterminate-bar-runs-off-both-edges.md).

So the entry was not a cost/benefit judgement that came out the wrong way. It was
an estimate nobody re-derived, and it survived because the thing it protected —
not touching every stylesheet — was frightening enough that the estimate was never
worth checking. That is the general shape worth naming: **a filed item's stated
cost is an assertion about the code, and it decays like any other comment.**

The *other* half of ADR-0182's sentence was true and is the real work here.
`SelectList`'s own first paragraph says it is

> A **part** — CSS-selectable and not constructible (ADR-0065)

while sitting `public` in `…controls.select`, which is exported. An application
could build a dropdown's panel with no dropdown around it, keyboard scope and all.
`…form.parts` had already answered this for `text-input` and `text-area`'s shared
caret: public to the module, in a package nothing outside it can see.

## Decision

**`SelectList` moves to `io.github.digitalsmile.goldberry.widgets.controls.selectlist`,
which is not exported.**

A package of its own rather than a share of somebody's, because that is what every
other widget in the catalog has and because its two owners are in different groups
— `…controls.select` and `…form.textinput` — so there is no package they share
below `widgets`. The name follows the catalog's own rule that turns a hyphenated
CSS type into a package: `progress` lives in `…controls.progressbar`, `text-input`
in `…form.textinput`, and `select-list` in `…controls.selectlist`.

Not exported, because ADR-0065's rule is not advice. A part is styleable and not
constructible, and for a one-owner part that has always meant package-private.
This one has two owners, so the enforcement that costs nothing is the module
boundary instead of the package one.

**The cost is named in the class.** The paragraph that carried the false estimate
is replaced by one that says what was actually true, and `cssType()` now carries a
comment saying the string did not change when the file did — so the next person to
read it is told the answer before they have to re-derive it.

## Consequences

**`SelectList` leaves the public API.** This is a real break, pre-1.0, and it is
the only thing in this record that costs anybody anything. An application that was
constructing one was building a `select`'s panel by hand, which is the thing
ADR-0065 says must not be possible; there is no supported way to do it and there
never was a reason to.

**Nothing in any stylesheet, golden or test *text* moved.** Every `select-list`
in `controls.css` (nine rules and four comments), the `select-list-dark` golden,
and `SelectTest`'s `assertEquals("select-list", list.cssType())` are byte-for-byte
what they were. What changed is eight Java files: the moved class, its two callers,
four tests that now import it instead of naming it, and `module-info`.

**Two fully-qualified names became imports on the way**, in `SelectState.panel()`
and `TextInputState.syncSuggestions` — both were inlining the old package, so the
move forced the issue. `SelectState` also picked up a `Tree` import it should have
had.

**`SelectListTest` exists to hold down a claim rather than a behaviour**, which is
unusual and deliberate. It asserts that the type is the literal, that the package
and the type are different strings, and — the one that matters — that a rule
written `select-list` still matches the class from its new home. If somebody ever
makes `cssType()` a function of the class, those fail together and ADR-0182's
estimate becomes true after the fact, which is the only way it ever could.

**The module path is the proof the suite cannot give.** Tests run on the classpath,
where a non-exported package is visible and this change is invisible. `:example:run`
with the dummy driver is what actually resolves the modules, and it is the step
that would catch an export removed one line too eagerly.

**This does not make `select-list` unstyleable.** It never could: the type is a
string, which is the whole point of the record.
