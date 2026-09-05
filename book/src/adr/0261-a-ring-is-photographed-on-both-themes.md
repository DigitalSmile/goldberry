# 261. A ring is photographed on both themes

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry on focus goldens by answering the question
it asked for rather than the one it looked like.

## Context

The entry did not ask for images:

> **A focus ring is only ever pictured on the dark theme, apart from one.**
> `segmented-focus-light` is new and is the catalog's first; `menu-focus` and
> `menubar-focus` are still `NORD_DARK` only, and so is every other state golden
> in the catalog. That asymmetry is what let §2.2's ring sit below §1.2's floor on
> the light theme without anything noticing — the fix for it moved **no golden at
> all**. What would close this is **a rule about which states are worth a second
> theme** rather than one more image.

The evidence behind it is the strongest kind there is: §2.2's ring measured
1.74:1, 2.00:1 and 1.64:1 on the light theme's three surfaces
([ADR-0239](0239-a-mark-is-measured-against-the-box-it-is-drawn-in.md)), and the
change that fixed it
([ADR-0240](0240-the-ring-follows-the-accent.md)) moved **no golden at all** —
because every focus image in the catalog was `NORD_DARK`.

## Decision

**Every `*-focus` golden has a `-light` twin, and a test says so.**

### Why the rule is only about focus

§2.2's ring is the one mark in the system with **no second means of being seen**.
A hover has a wash, a checked control has a fill, a disabled one has its opacity
— and each of those is drawn in colours some other golden already covers, so a
second image of it buys a second file and no new question.

A ring is only a ring, and `--gb-focus` resolves differently per theme. So a ring
photographed on one theme is a ring nothing is watching on the other, which is
not a hypothetical here but a thing that already happened.

Doubling the whole corpus was the obvious reading of "state goldens are
dark-only" and is the wrong rule: it is thirty more files to answer one question,
and the one question is answerable with three.

### It discovers its subject

`FocusGoldenPairTest` reads the resource **directory** rather than a list, so a
focus golden added next month is checked next month and nothing has to be edited
to know about it. That is `ExportedSurfaceTest`'s and `SupportedPropertyTest`'s
property, and it is the difference between a rule and a list.

Three assertions, because a discovering test has a failure mode of its own:

- every `X-focus` has an `X-focus-light`;
- the sweep **finds** at least the four rings the catalog has, so renaming the
  convention or moving the directory cannot make it pass by seeing nothing;
- no `-light` twin is orphaned, since a light ring with no dark one to compare it
  against is an image of nothing.

Checked against a deliberate break: with `tabs-focus-light.png` moved aside, the
first assertion fails and names the missing file.

### The naming convention is what it leans on

Every focus golden is `<widget>-focus`, because a `PseudoState` golden is named
after the state it captures. A ring photographed under some other name would be
invisible to this — a real limit, and a smaller one than enumerating them by
hand.

## Alternatives considered

- **Three more images and a comment.** What the entry explicitly did not want,
  and the reason is that the fourth ring would be added without one. A comment
  is not a rule; the next widget's author does not read this file.
- **A light twin of every state golden.** Thirty-odd files, most of them
  answering a question their siblings already answer. The rule would be "photograph
  everything twice", which is not a rule so much as an absence of one.
- **Asserting the ring's contrast instead.** `ContrastTest` already does, and it
  is the check that *found* this — but a ratio says the colour clears the floor,
  not that it is drawn where a reader expects it. `tabs-focus` exists because the
  ring is inside the header there rather than at §2.2's usual offset, which no
  ratio would ever say.

## Consequences

- **Three new goldens**: `menu-focus-light`, `menubar-focus-light`,
  `tabs-focus-light`. `segmented-focus-light` already existed and is what the
  entry called the catalog's first.
- **The rule is a test**, so the entry's own request is satisfied rather than its
  symptom. A widget that grows a focus golden and forgets the twin fails on a
  message that says which file is missing.
- **The convention became load-bearing.** `-focus` in a golden's name is now
  checked rather than merely conventional, and the test's javadoc says so, because
  a name that carries meaning silently is the thing this repository keeps finding.
- **It does not check the images are different.** Two identical pictures would
  pass, which is the case where a theme swap changed nothing — and that is
  precisely what `ContrastTest` is for. The pair of checks is the coverage; either
  alone is not.
