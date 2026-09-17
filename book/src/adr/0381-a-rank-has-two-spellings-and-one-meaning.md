# 381. A rank has two spellings and one meaning

Date: 2026-09-17

## Status

Accepted. Closes `docs/ARCHITECTURE.md` §17.1's "`text style="body"`" and
`book/src/TODO.md`'s "`text` has no `style="body"` attribute".

## Context

`core-widgets.md` §2 gives `text` a `style=` attribute for §1.4's token styles —
`text style="title"`. What shipped is `class="title"`: the same thing spelled
the way CSS already spells it, resolved by rules in `controls.css` that a theme
can move all at once.

§17.1 recorded it as a spelling disagreement and noted that a second spelling
"may still earn its keep". Two things say it does. The design documents are the
authority, and this is a sentence in one of them that the code simply does not
implement. And the two spellings are not equivalent in one respect that matters:
a class is an open vocabulary — an application writes its own — while a rank is
a closed one, so `class="titel"` is a rule that has not been written yet and
`style="titel"` is a mistake.

## Decision

**Both spellings, one mechanism: `style=` names a [TextRank] and resolves to the
class.**

- `TextRank` is §1.4's scale as an enum, and `cssClass()` is its name as CSS
  spells it — `BODY_STRONG` is `body-strong`.
- `text style="title"` adds `title` to the element's classes at inflate time, so
  a rule written `text.title`, an application's own `.title` override and the
  theme's own tokens all apply to it unchanged. A rank the widget carried
  privately would be a second mechanism that looked like the first.
- Classes written beside it survive: `text style="title" class="muted"` is both.
- An unknown rank is refused where it is written, with the six named in the
  message. That is the difference between the two vocabularies, stated.
- `Text.style(TextRank)` is the same thing from Java.

## Consequences

- §2's sentence is implemented rather than recorded as a departure, and §17.1
  loses an entry.
- The rank is still not a size: what `heading` is worth stays in `controls.css`,
  which is what lets a large-text theme move every rank without a widget hearing
  about it.
- `TextRankTest` asserts that the seven constants are the seven rules the
  stylesheet has, so a rank added to one has to be added to the other.
