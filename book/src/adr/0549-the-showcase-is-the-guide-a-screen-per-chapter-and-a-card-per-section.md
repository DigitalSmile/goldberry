# ADR-0549: The showcase is the guide, a screen per chapter and a card per section

- **Status:** Accepted
- **Date:** 2026-10-03
- **Amends:** [ADR-0222](0222-a-showcase-is-a-window-a-bar-and-seven-screens.md),
  whose "each screen a question rather than a widget family" this replaces
- **Relates to:** `docs/showcase-refactor-2026-10-03.md`,
  [ADR-0511](0511-the-book-is-a-guide-first-and-the-log-is-its-last-part.md)

## Context

The guide at goldberry.dev/docs is now the documentation, and it is organised
one way: a Layout part, a Components part with a chapter per family, and a
developer guide with a chapter per subject. The showcase was organised another.
ADR-0222 made its screens *questions* (`Basic`, `Panels`, `Overlays`…) and the
gallery grew to seventeen of them, so a reader with a chapter of the guide open
could not tell which screen showed it, and a reader in the showcase had no way
back to the chapter.

The two had also drifted apart in content. Of the guide's sections that describe
something a window does, many had no card at all: `pressable`, `slot`, the size
tokens, most of the developer guide. Cards that did exist carried long captions
about how the showcase itself was built (`basic.kdl` cannot express this, the
document has no Java behind it), and the card helper was written six times over.

## Decision

**The gallery mirrors the guide.** One screen per chapter, in the summary's
order, from Layout to the developer guide, then a **Guide** screen. A chapter
whose cards each own a viewport is split from its part so the screen can fill its
tab: `scroll` and `affix` are the **Scrolling** screen, beside **Layout**. The
list is `Gallery.TABS`, and the strip, `Ctrl+1`…, Edit ▸ Go to and the tray all
read it.

**A card is one section of the guide.** It has a head holding its title and a
**Docs** link to that section, then a summary of one or two sentences, then the
demonstration. `ShowcaseCard` builds it in Java, and a document writes the same
tree by hand. A screen opens with a `ScreenHeader` that links its chapter.

**A section with nothing to click still has a card.** A reference card is the
head and the summary only. The Guide screen is made of them, one per chapter about
installing, building, testing, weaving, releasing or contributing, so every
chapter of the book has a place in the showcase.

**A summary says what the thing does.** It is held to 280 characters and may not
cite a decision record: the record is for maintainers, and the link goes to the
guide, which is where a reader of the showcase wants to land.

**The coverage is a test.** `GalleryDocsTest` mounts every screen the way the
window does and fails, chapter by chapter, on:

- a section that needs a card and has none;
- a link to a heading the chapter does not have;
- a card without its head, link or summary;
- any text on screen that cites a record.

A widget chapter needs a card per `##` heading. A developer-guide chapter that
describes what a window does needs one per `##` and `###`. Every other chapter
needs a link. `DocumentCardsTest` checks the cards the documents write without a
renderer.

## Consequences

- A section added to the guide turns `GalleryDocsTest` red until the showcase
  shows it. That is the point, and it costs the author of a chapter a card.
- A heading renamed in the guide breaks a card's link at build time rather than
  leaving it opening the top of a page.
- The gallery is twenty-nine screens. Ten have a digit; the strip pages from its
  ends and Edit ▸ Go to reaches the rest.
- The screen goldens and the guide's `screen-*` pictures are retaken. A picture
  the guide shows of a screen that no longer exists moves to the screen that
  replaced it.
