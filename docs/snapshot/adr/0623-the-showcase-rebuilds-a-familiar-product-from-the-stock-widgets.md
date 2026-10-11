# ADR-0623: The showcase rebuilds a familiar product from the stock widgets

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** ADR-0110 (the showcase is a gallery of screens);
  `docs/lookalike-screen.md` (the working notes)

## Context

Every screen in the gallery mirrors a chapter of the guide: a header that
links it and a card per section. That shows each widget on its own, in the
theme, and it never shows what an application looks like when it is not the
theme — when a sheet of its own takes the stock rows, pressables, icons and
sliders and makes them into something a user would recognise.

The user asked on 2026-10-11 for a tab with a custom-made interface that looks
like a popular product. The product was left open.

## Decision

**A screen called Lookalike rebuilds a music streaming app — a sidebar of
playlists, a tinted playlist page with a track table and an album shelf, a
now-playing panel and a player bar — from stock widgets and one stylesheet.**

- **It mirrors no chapter.** It sits after Application and before Guide, and
  its header links the styling chapter, because what it demonstrates is how far
  a sheet takes the stock widgets. `GalleryDocsTest` asks for one header with a
  link that resolves, and it has one; it has no gallery cards, so there is no
  section of the guide it is responsible for.
- **No widget is written for it**, other than the records that arrange the
  stock ones. Every box is a `row`, `column`, `text`, `icon`, `pressable`,
  `scroll` or `slider`. The sliders are restyled through the theme's own
  tokens (`--gb-slider-fill-bg` and the thumb's), set under `#stream-app`, so
  their structure is the control's and only their colours are the product's.
- **The sheet has its own palette** under one id, `#stream-app`, as an
  application's sheet would. Literal colours are declared once as custom
  properties at that root; nothing outside the id is touched.
- **The state is one immutable `Session`, and every control sends one of a
  sealed set of `Command`s.** `Session.apply` is an exhaustive `switch` over
  the commands, so the player's rules are pure and tested without a window, and
  the one stateful widget holds the session and a once-a-second timer through
  `Host.after`.
- **Three packages** under `dev.goldberry.example.ui.lookalike`, in the order
  they depend on each other: `library` (tracks, playlists, albums and how a
  player writes time), `player` (the rules) and `view` (the widgets).
- **Invented content.** The artists, songs and the service's own name
  (Goldtune) are made up; the covers are CSS gradients rather than bitmaps.
  The look is the product's; nothing in it is the product's.
- **It opens paused, part-way into a track**, so the golden image is one
  picture rather than a different one every second, and the player bar still
  reads as a player.
- **The shuffle order is seeded by the queue's id**, so the same playlist
  shuffles the same way and a test can say what `Next` plays.

## Consequences

- The gallery has thirty screens; Lookalike has no digit (`Ctrl+1` to
  `Ctrl+0` are the first ten) and is reached by the strip and Edit ▸ Go to.
- `ShowcaseShellTest.theGallery` pins it in the order, `GalleryGoldenTest`
  photographs it (`gallery-lookalike.png`), and `GalleryTextScaleTest` holds it
  to 150%: the library's filter chips wrap rather than overrun, which is the
  one change that check asked for.
- The golden harness draws with one face at one size, so the picture shows
  the layout and the colours and not the type scale. The interaction test
  draws it with the bundled font book.
- A second product, if one is wanted, is a sibling of `library`/`player`/`view`
  under the same screen or a screen of its own; nothing here is shared with the
  other screens to stop either.
