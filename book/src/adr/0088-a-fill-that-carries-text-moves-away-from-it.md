# ADR-0088: A fill that carries text moves away from it

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/design-system.md` §1.2 §2.1, `docs/ARCHITECTURE.md` §10

## Context

[ADR-0087](0087-a-semantic-fill-brings-its-own-foreground.md) built `badge` and,
in the process, built the contrast check `docs/design-system.md` §1.2 had always
claimed CI ran and which had never existed. Its first run found seven shipped
`button` colour pairs below §1.2's 4.5:1 floor:

| pair | measured |
|---|---|
| `nord-dark button.danger` | 3.55 |
| `nord-dark button.danger:hover` | **2.95** |
| `nord-dark button.danger:active` | 4.38 |
| `nord-light button.primary` | 3.50 |
| `nord-light button.primary:hover` | 4.18 |
| `nord-light button.danger` | 3.55 |
| `nord-light button.danger:hover` | 4.23 |

ADR-0087 held them in an enumerated `KNOWN_FAILURES` list rather than fixing
them, because the fix repaints the most-used control in the toolkit and a change
that adds a badge should not also be that. This is that change.

Two things stand out in the table, and both are the shape of the fix rather than
the size of it.

**Every ramp's darkest step already passed.** `button.danger:active` is 5.11:1 on
light, `button.primary:active` is 5.06:1. Nothing needed a new colour system —
the ramps needed *sliding*, and the value that was `:active` is roughly where
rest belongs.

**The worst pair in the toolkit was a hover state, and it was worse than the rest
state it was one step from.** `nord-dark button.danger:hover` at 2.95:1 is below
`button.danger` at 3.55. That is not a colour that was picked slightly wrong; it
is a rule applied where it does not hold. The dark theme lightens on hover —
correctly, for a *surface* moving one step toward the light. A danger button is
not a surface. It is a saturated fill carrying `--nord6`, and lightening moved it
**toward its own text**.

## Decision

**A filled control's hover moves the fill away from its own text colour, not in
the theme's usual direction.** Stated that way it is one rule, and it already
described three of the four filled variants:

| variant | text | hover | correct? |
|---|---|---|---|
| dark `button.primary` | `--nord0` (dark) | lightens | ✓ 6.24 → 7.00 |
| dark `button.danger` | `--nord6` (light) | *lightened* | ✗ 3.55 → 2.95 |
| light `button.primary` | `--nord6` (light) | darkens | ✓ |
| light `button.danger` | `--nord6` (light) | darkens | ✓ |

So `button.danger` on the dark theme now darkens on hover, against that theme's
usual direction, and it is the only place in the toolkit that does.

**The ramps slide down to sit inside the legible band.** `--gb-button-danger-bg`
and `--gb-button-primary-bg` stop aliasing `--nord11` and `--nord10` and take two
new tokens, `--gb-danger-fill` and `--gb-accent-fill` — ADR-0087's split between
*what a hue is* and *what you may put words on*. The danger ramp is now
**identical on both themes**, because the hue is and the text on it is.

**`--gb-accent-bg-hover` / `--gb-accent-bg-active` are split out of the button's
ramp.** `--gb-checkbox-bg-checked-hover` and its radio and toggle counterparts
aliased `--gb-button-primary-bg-hover`, on the argument that a checked control
and a primary button share the accent ramp. They did — until now. A button's fill
is chosen so its *label* clears 4.5:1; a checked glyph carries a mark, which
§1.2 asks 3:1 of, and darkening every checkbox, radio and switch in the toolkit
for a rule that does not apply to them would have been the fix escaping its
scope. The accent ramp keeps the values the button's ramp used to hold, and every
checkbox, radio and toggle golden is byte-identical.

**`KNOWN_FAILURES` is now empty, and stays.** An empty list asserted equal to the
measured failures says *nothing is exempt*, and a second test asserts the
emptiness by name — so re-exempting a pair fails a test that says what happened
rather than turning a green run into a differently-green run.

## Alternatives considered

**Keep the fills and darken the text instead.** `--nord11` needs something near
black to carry 4.5:1, and the palette's darkest entry is `--nord0`, which gives
3.05. It would mean a non-Nord foreground on a Nord fill — the derivation has to
happen somewhere, and doing it on the fill keeps the text token a palette entry.

**Keep the dark theme's "hover lightens" rule and start the danger ramp low
enough that even the lightest step passes.** This works arithmetically: rest at
44% lightness, hover at 46%, active at 38% all clear the floor. Rejected because
it preserves a rule that is wrong for this case and leaves the ramp with almost
no headroom — hover would sit at 5.09:1 with the next step up failing, so the
next person to nudge it breaks §1.2 again and the code says nothing about why it
is tight. The rule that a fill moves away from its text is the thing worth
writing down.

**Let the checked-control ramps follow the button's down.** One fewer token pair,
and it is what the alias already said. Rejected on the numbers: a checked
checkbox carries a mark and not text, so it is under §1.2's 3:1 non-text rule
with room to spare, and moving it would repaint three controls across both themes
to satisfy a rule they are not subject to.

**Recolour by instancing the palette through a solver at theme load.** Considered
and rejected in ADR-0087 for determinism; nothing here changes that argument.

**Leave them, and document the failures.** They were documented, in
`KNOWN_FAILURES` and in `status.md`, which is what made this change a
five-token edit instead of an investigation. But `button.danger` is the control a
user reaches for when something is about to be destroyed, and at 2.95:1 its
hover state is the least readable thing in the toolkit.

## Consequences

**Every text-on-fill pair the toolkit ships now meets §1.2, on both themes,
enforced.** The exemption list is empty and asserted empty.

**`button.primary` on light and `button.danger` on both themes look different.**
Deeper and more saturated; `button-variants-dark.png` and
`button-variants-light.png` are the two goldens that moved, and they are the only
two — the checkbox, radio, toggle, slider, progress and spinner images are
byte-identical, which is what says the ramp split landed where it was aimed.

**One control now hovers against its theme's direction**, and that is a thing a
future reader will find surprising. It is commented at the token and the rule is
here; the alternative was a ramp that is correct by arithmetic and unexplained.

**Two more derived hex values exist** — `--gb-accent-fill` on light joins
ADR-0087's `--gb-danger-fill` — and a future Nord revision has to re-derive them.
Each is commented with the palette entry it came from, its lightness and its
measured ratio.

**A theme is a larger surface again.** An application supplying its own theme now
also supplies `--gb-accent-fill`, `--gb-danger-fill`, `--gb-accent-bg-hover` and
`--gb-accent-bg-active`. As in ADR-0087, nothing validates a third-party theme.

**Non-text contrast is still unchecked.** `ContrastTest` measures text against
its fill. A checked checkbox's mark, a slider's thumb against its groove, and the
focus ring against the surface behind it are all under §1.2's 3:1 non-text rule
and nothing measures them — the argument that the accent ramp did not need to
move rests on a number nobody is enforcing. Recorded in `status.md`.

**`ButtonTest.fadesRatherThanRemaps` no longer pins a hex.** It asserted the
disabled danger background equalled `0xFFBF616A`, so it also asserted *which*
red — and failed here for a reason it was not about. It now compares the disabled
button against the enabled one, which is the claim it was making.
