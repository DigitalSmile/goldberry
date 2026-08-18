# ADR-0095: A shortcut is built from enums

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/design-system.md` §2.3, `docs/ARCHITECTURE.md` §7.2

## Context

An accelerator had one way in: `Shortcut.of("Ctrl+S")`. The string was parsed at
runtime against a tolerant table of spellings, and a typo — `"Crtl+S"`, `"Ctrl-S"`,
`"Ctrl+Save"` — threw when the line ran, which for a shortcut bound at start-up
means at start-up and for one bound lazily means whenever.

`Modifiers` had the matching problem from the other direction: four positional
booleans. `new Modifiers(true, false, false, false)` is four chances to get the
order wrong and nothing to catch it, and 23 call sites wrote them out.

The request was `Mod.CTRL | Key.A`.

## Decision

**A `Mod` enum with a real bitmask, composed with `and`.**

```java
Mod.CTRL.and(Key.S)                    // Ctrl+S
Mod.CTRL.and(Mod.SHIFT).and(Key.Z)     // Ctrl+Shift+Z
Shortcut.of(Key.F5)                    // F5
```

**`|` is not available and the alternative that is would be worse.** `|` is
defined for the integral types and `boolean` and Java does not allow overloading
it. The spelling that *would* compile — `Mod.CTRL.bit() | Mod.SHIFT.bit()` passed
to a method taking an `int` — is a mask with nothing checking it, and
`Key.A.ordinal() | Mod.CTRL.bit()` would compile and mean nothing. So the mask is
real and private to the arithmetic: `bit()` exists for the SDL boundary and for
tests, and an application composes with `and`, which can only produce `Modifiers`
or a `Shortcut`.

**`Modifiers` is one `int` instead of four booleans**, with `has(Mod)`, `only(Mod)`
and `set()` on top and the four boolean accessors kept so no call site changed.
The four-boolean constructor stays as a *secondary* one — it reads fine where all
four are literals and is a trap where they are computed, which is exactly the
distinction between a secondary constructor and a canonical one.

**`Shortcut.of(String)` stays.** An accelerator has to be printed beside a menu
item anyway, and a configuration file has nothing but text. What changed is that
it is no longer the *only* way in.

**`Host.shortcut` and `PointerRouter.shortcut` take both forms**, and the
showcase uses the enum one.

## Alternatives considered

**An `int` mask parameter — the literal reading of the request.** Rejected above:
it accepts any `int`, including ones that mean nothing.

**`EnumSet<Mod>`.** Type-safe and idiomatic, and heavier than the thing it
describes: a `Shortcut` is a map key on the keyboard path, and an `EnumSet`
allocation per comparison for four possible bits is a poor trade. `set()` returns
one for callers that want it.

**Keep the four booleans and only add `Mod` for shortcuts.** Half the change, and
it leaves `new Modifiers(true, false, false, false)` in 23 places — the exact
thing the enum was asked for.

**Translate `Cmd` to `Ctrl` on macOS.** Unchanged from before and still refused:
a toolkit that silently remapped them would make `Ctrl+C` mean two different
things depending on where it ran. `docs/ARCHITECTURE.md` §17.1 still records that
`design-system.md` §2.3 wants the opposite, and this ADR does not settle it.

## Consequences

**A shortcut built in Java cannot be misspelled.** A parsed one still can, and
that is the price of keeping the string form for menus and config.

**`Modifiers` is a different record shape**, so anything pattern-matching its
four components would break. Nothing did — checked before the change.

**One mask layout is now load-bearing in two places**: `Mod.bit()` and
`Modifiers.fromSdl`. They are in the same file as each other, and the constructor
rejects bits no `Mod` owns, so a third layout cannot appear quietly.

**`Key` is untouched.** It is already an enum and already the type `Shortcut`
holds; only the modifier half needed the work.
