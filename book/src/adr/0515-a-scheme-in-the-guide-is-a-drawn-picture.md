# ADR-0515: A scheme in the guide is a drawn picture

- **Status:** Accepted
- **Date:** 2026-10-01
- **Relates to:** [ADR-0513](0513-the-guides-pictures-are-taken-from-its-own-samples-in-both-themes.md),
  [ADR-0511](0511-the-book-is-a-guide-first-and-the-log-is-its-last-part.md), `docs/book.md`

## Context

The guide drew its schemes in box characters inside code fences: the layers of
the toolkit, the frame loop, the flow of values and events, the three nodes of
a viewport. They render in a monospace font at code size, cannot be read by a
screen reader as anything but a wall of symbols, break when a label is one
character too long, and look like code to a reader skimming for code.

## Decision

- **A scheme is a picture**, drawn by `book/diagrams/draw.py` from a
  description in `book/diagrams/diagrams.py`: sticky notes, lanes and arrows
  on a dotted board, the way a whiteboard tool draws them. Notes are pastel
  with dark ink in both themes; the board, the lanes, the connectors and the
  captions take the theme's own colours. The faces are the toolkit's own,
  Inter and JetBrains Mono, read from the asset cache.
- **Two files per scheme, at 2×**, `images/diagram-<name>-light.webp` and
  `-dark.webp`, shown with the same `gb-shot` block as every other picture
  (ADR-0513), so the page switches them with the theme and `BookTest` holds
  them to the same rules. The alt text says what the boxes and arrows are.
- **The script is run by hand** and the pictures are committed, as the
  screenshots are. It needs Python 3 and Pillow, which this repository did
  not depend on before and the Pages workflow still does not.
- **A listing stays text.** A directory tree or a start-up timeline is
  something a reader copies or compares against their own, and a code fence is
  the right form for it.

## Alternatives considered

- **SVG by hand.** Reviewable as text and sharp at any scale, and the two
  themes would mean two files to keep in step by hand, or a stylesheet inside
  each SVG that mdBook's theme class cannot reach from outside the image.
- **A browser rasterizing SVG.** Headless Firefox refuses to run beside an
  open Firefox under snap confinement on the machine that builds these, and
  no other browser is installed. Pillow draws the same shapes with no browser.
- **Mermaid.** A preprocessor to install (ADR-0514's objection) and a style
  that is not the page's; its output also has to be rasterized or shipped as
  a script.
- **Drawing with the toolkit's own canvas.** Tempting, and the text would come
  out through the same shaper the widgets use. The painting API has no text
  entry point a script could call without building a widget tree, and the
  diagrams would then depend on the native library to regenerate.

## Consequences

- Four schemes are pictures in both themes; the box drawings are gone from
  the guide. New schemes follow the same path: a function in `diagrams.py`,
  one run of `draw.py`, a `gb-shot` block.
- The diagrams are not held to the code the way the screenshots are. They
  describe architecture, and a change to the architecture is a change to the
  description.
- A machine that renders them needs Pillow and the asset cache. Neither is
  needed to build the site or to read it.
