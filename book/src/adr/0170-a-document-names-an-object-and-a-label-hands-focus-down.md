# 170. A document names an object, and a label hands focus down

Date: 2026-08-21

## Status

Accepted. Closes three gaps
[ADR-0169](0169-a-field-is-silent-until-you-leave-it.md) recorded, adds a fourth
registry, and fixes a CSS shorthand bug that had been silently deleting
[ADR-0166](0166-a-raised-thing-is-told-apart-by-its-edge.md)'s card edge since it
shipped.

## Context

ADR-0169 shipped `field` and `form` and recorded what they could not do:

> Markup cannot hand a controller to a widget, and `form` is the second to want
> one. […] Nothing can ask for focus, so `field` has no click-to-focus.

Both are reported by the showcase's own screen, which had a form nothing could
submit and a label nothing happened when you clicked. §4 asks for both.

## Decision

### A fourth registry, because the other three each refused the job

`Named` — what `controller=` and `validator=` resolve against.

The first attempt was the **binding** registry: a `@Bind` field holding the
controller, resolved through the path syntax `bind=` already uses, on the
argument that a value is named one way
([ADR-0129](0129-a-value-is-named-one-way.md)). The binding machinery refused it,
in as many words:

> `@Bind` field … is final; a value that cannot change is not something to
> subscribe to, and binding one shows up as a control that never moves.

Which is right, and is the whole answer. A binding is a **subscription**; a
controller is a handle and a validator is a function, and neither ever changes.
The check that caught it was written for a different mistake and turns out to
describe this one exactly.

So the set is now four, and each is a different kind of thing markup can *name*
and cannot describe:

| Registry | What `…=` names | What it is |
|---|---|---|
| `ActionRegistry` | `press=`, `change=`, `submit=` | a method |
| `BindingRegistry` | `bind=` | a value that **changes** |
| `Icons` | `icon=` | a resource with a lifetime |
| `Named` | `controller=`, `validator=` | an object that does neither |

Strict by default, for `ActionRegistry`'s reason: `controller="signip"` is a
typo, and a form that silently cannot be submitted is the hardest kind of bug to
notice. A name registered as the wrong type is refused where it is written rather
than resolving to null — a `controller=` that quietly became nothing would be a
form that cannot be submitted and says so nowhere.

This is what keeps §9's rule intact rather than bending it. `validator="app.port-rule"`
says *which* rule; a document that could say what the rule **is** would be code
with a different syntax, and hot-reloading it would mean hot-reloading code —
which is the sentence `Icons` and `ActionRegistry` both already turn on.

### A container can hand focus down

`Handles.delegatesFocus()`. A press focuses the nearest focusable **ancestor** of
whatever it hit, which is what makes clicking a button's label press the button.
A `field`'s label is not an ancestor of its control — it is its **sibling** — so
that rule cannot reach it, and clicking a label did nothing.

A container that answers true says "the focusable thing here is one of my
children", and the walk turns round and goes down to the first focusable
descendant. It is consulted **on the way up**, so it never overrides a real
target: a press on the control finds the control first, and so does a press on a
button inside the field. What it catches is the label, the message, and the gap —
the places where "the user aimed at this field" is the only sensible reading.

`PointerRouter.focusFromPress` is now public, because the rule is worth naming: a
test that wants to know what a press would focus should be able to ask, rather
than paint a frame and synthesize a press to find out.

### A form decides the label column, not each field

ADR-0169 shipped `field.horizontal`. A form is where somebody decides that *this
form* has a label column, and writing the class on every field is the same
decision repeated once per row and wrong the moment a row is added. So
`form.horizontal field` carries it, `field.horizontal` stays for a field standing
on its own, and `form.horizontal field.vertical` is the way back for the one
field — a `text-area`, usually — that wants the width.

A horizontal field's **control has to grow**, and that is not decoration: a
`text-input` is as wide as it is told to be, so beside a fixed-width label in a
row it takes nothing at all. The controls are enumerated rather than selected as
"everything that is not the label", because §8's subset has no `:not()` — the
same reason a disabled control is kept from lighting up in the router rather than
in a stylesheet ([ADR-0064](0064-input-is-a-service-a-widget-opts-into.md)).

### A shorthand keeps the spaces inside a function

`ComputedStyle`'s shorthand splitter broke on **any** whitespace, so
`border: 1px solid rgba(255, 255, 255, 0.2)` split into seven fragments instead
of three. `CssColor.parse` was handed `rgba(255,` — which is not a colour — and
the whole declaration was dropped.

**This was live.** `--gb-border-strong` is `rgba(…)` by design: an alpha over
whatever is underneath is the only way to say "lighter than its own surface" in a
subset with no colour functions (ADR-0166). `card`'s edge is the whole of how a
raised thing is told apart from a panel, which is what that ADR is *called* — and
it has had no edge on either theme since the day it shipped.

The warning was there. `dropping "border": … is not a valid value` was printed on
every run of the showcase, and nothing was reading it. It was found by running
the application and looking at the log, which is the same way ADR-0166's own
defects were found: by looking.

## Consequences

- **A raised card has an edge again**, on both themes. Three golden images moved
  and the change in them is the fix.
- **Any shorthand with a function in it now works** — `outline`, and whatever
  else `split` serves. The bug was general and the token that exposed it was the
  first to have spaces in a function.
- **`Wiring` has a fourth component.** Its old three-argument constructor stays
  and defaults the new one to `Named.none()`, so nothing that built one has to
  change; `Widgets.inflater(named, icons, models…)` is the new overload.
- **The showcase's Forms screen is a document with a working form in it** — a
  controller it names, a validator the application wrote, a Save button that
  refuses, and a label you can click. Which was the point of recording the gaps:
  the screen said what was missing, and now it demonstrates what replaced it.
- **`delegatesFocus` has one consumer** and is the kind of thing that should have
  two. `group-box` and `card` are candidates and neither has asked; it ships
  because §4 asks for click-to-focus by name and a `<label for>` is not a widget
  this toolkit has.
- **Three to a row.** A fourth column on a 900-point screen gives every field
  about 200 points, which is narrower than what people type into them, and §10's
  `wrap` is not built — so a row that outgrows its width squeezes rather than
  folding.
