# ADR-0625: Showcase search runs over an index a test writes

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** ADR-0624 (the shell); `docs/showcase-redesign.md`

## Context

The user asked for a search that finds anything by entry: a screen, a card on
one, or something the window does. A card exists only once its screen is built.
Many are built inside a chapter's `build`, and the documents' cards once a
`Documents` inflation runs. The window builds one screen at a time. Building
all thirty screens to search them would mean the media players, the GPU canvas
and a web view on the first `Ctrl+K`.

## Decision

**The cards are listed ahead of time in `search/index.tsv`, a resource written
and checked by `SearchIndexTest`, and the palette ranks screens, cards and
commands over it.**

- **The index** holds the screen, the card's id, its title and its summary for
  every `wall-card`, and each screen's header summary. They come in the order
  each screen's element tree holds them. `SearchIndexTest` builds every screen
  the way `GalleryDocsTest` does. It fails when the file differs, and
  `-Dgoldberry.golden.update=true` rewrites it, the same as a golden. A test
  holds the file to the screens, so it cannot drift. The test also fails on a
  card without an id, because a card that cannot be found by id cannot be
  scrolled to.
- **Entries are a sealed interface:** `Screen`, `Card` and `Command`. The
  palette's icon, its tag and what `Enter` does are exhaustive switches over
  them.
- **Matching** (`FuzzyMatcher`) has five tiers: the whole text, a prefix, a
  word start, anywhere, and initials (each letter at a word start, against a
  title only). Letters scattered through the middle of words are not a match.
  **Ranking** (`SearchRanking`) needs every word of the query to match
  somewhere. A word counts three times in the title. Screens get a small lead
  and commands a smaller one, and ties keep the gallery's order.
- **The palette** (`CommandPalette` in a `Dialog`, opened by `SearchPalette`)
  has a field, a filter by kind, and up to sixty results. A wrapper takes the
  arrows, `Enter` and `Tab` in the capture phase, so the field keeps the
  keyboard and the dialog never sees an `Enter` it has no button for. With
  nothing typed, the palette lists recent picks and then every screen.
- **Picking a card** opens its screen. After the frame that builds it, the
  card is scrolled to the top and ringed for a moment.
- `Ctrl+K` opens search. "March a league", which had it, moved to `Ctrl+L`.

## Consequences

- A change to a card's title or summary, or a new card, means rerunning
  `SearchIndexTest` with the update flag. The failure says so.
- The index's card order follows the masonry's columns, so a wall whose
  column count changes reorders its entries. That is a rewrite, not a defect.
- The native image declares `search/*.tsv` in the manual reachability file.
- The Icons and Emoji screens keep their own per-sheet search; the palette
  finds the screens.
