# ADR-0624: The showcase is a sidebar of sections, not a strip of tabs

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** ADR-0110 (the showcase is a gallery of screens); ADR-0623
  (the Lookalike screen); ADR-0625 (search); ADR-0626 (the type scale);
  `docs/showcase-redesign.md` (the working notes)

## Context

The gallery had grown to thirty screens in one horizontal `tabs` strip,
under a menu bar and a status bar. The strip ran off the window, so most
screens were reached through `Ctrl+digit`, the arrow keys inside the strip, or
Edit ▸ Go to. The chrome took three bands, about 130px, before any screen began.
The user called it "monstrous" on 2026-10-11 and asked for a UI that is pretty
and efficient to use, with any navigation pattern that makes it easy.

## Decision

**The window is one top bar, a sidebar of seven sections beside the showing
screen, and a slim footer.**

- **Sections.** `Section` groups the screens: Basics, Controls, Structure,
  Graphics & media, Content, Platform, Demos. The Guide screen, a wall of
  reference cards for the chapters with nothing to try, is gone. Its chapters
  are links on Home (below). `GalleryTab` carries its section
  and a bundled icon, and `Gallery.TABS` lists the screens section by section,
  so the next screen down the sidebar is the next one in the list. `Ctrl+1`…
  follow that order, so they moved: `Ctrl+0` is Panels now.
- **The sidebar** (`ui/shell/Sidebar`) lists Home, then each section with its
  screens and the number of cards each holds. A section's heading folds it.
  `Ctrl+B` turns the sidebar into a rail of icons with the names as tooltips.
  Folding is the sidebar's own state; the rail is the model's (`app.sidebar`),
  because the View menu ticks it.
- **The top bar** (`ui/shell/TopBar`) holds the sidebar's switch, the name,
  a search field that opens the palette, a sun or moon for the light, and the
  menu. The menu bar folds into one overflow heading, **Menu**, holding File,
  Edit, View and Help. `F10` and every accelerator still work. View is new: Search, Home,
  Back, Forward, the next and previous screen, the rail, and the reading sizes.
- **The page head** (`ui/shell/PageHead`) puts Back and Forward
  (`Alt+←`/`Alt+→`), a trail (Home › section › screen) and Previous/Next
  (`Ctrl+PgUp`/`Ctrl+PgDn`) above the screen. History lives on the model
  (`NavHistory`), fed by `pickScreen`. Every route to a screen goes through
  `pickScreen`, so every route lands in the history.
- **On this page** (`ui/shell/JumpBar`) is one sideways-scrolling line of the
  screen's cards by title. A press scrolls the card to the top and rings it
  (`CardSpotlight`, a fill overlay drawn only where the ring is).
- **Home** (`ui/home/HomeScreen`) is the first screen. It has a search field,
  the tour, the screens visited lately, a tile per section listing its screens,
  and **Read the guide**: the book's reading chapters as a contents list, part
  by part, each a link with a line under it. They are links and not cards,
  because they are places to read rather than to try. `GalleryDocsTest`
  counts them towards the guide being shown, and search offers each one as
  "Guide: …", which opens the browser. Home is not one of
  the gallery's screens: it mirrors no chapter, so it has no digit and no row in
  Edit ▸ Go to, and `GalleryDocsTest` does not ask it for a header.
- **The footer** is `statusbar.kdl`, cut to the run's readings. Its light
  switch moved to the Choices screen's theme card, which already showed the
  same fact three other ways. The markup demonstration of a `toggle` reading a
  boolean beside pickers reading a name is kept there.
- **The content area builds one screen.** It is a keyed `scroll`, or a keyed
  column for a screen that owns its viewport. This is the strip's old
  behaviour without the strip: only the showing screen is built, and a screen
  that comes back starts fresh.

A card a reader types several lines into (a text area, a sheet or document
editor) is **wide**: `ShowcaseCard.wide()`, the class `wide-card` in a
document, or a `WideCard` for a stateful card that builds its own. `Wall` deals
these into a second wall under the first, with columns no narrower than 560px:
one full-width column beside the sidebar at the restored size, two on a wide
screen. Forms, Text, Styling and Application have them.

Walls that hold rows which do not wrap (Collections, Navigation) use
`Wall.wide`: as many columns as fit, none narrower than 560px. A fixed two
columns was right while the gallery had the window to itself, and cut the rows
once the sidebar took a quarter of it. Scrolling, which has no viewport of its
own to scroll its three, takes 300px columns so they sit side by side.

## Consequences

- Every gallery golden and the guide's screen pictures were retaken, because
  every screen is drawn inside the new shell. `ShellGoldenTest` pins Home, the
  rail and the palette.
- `Screen`'s record grew an `openSearch` handler and `AppMenu.Handlers` a
  seventh slot. `ShowcaseShellTest` presses all seven and asserts which one ran.
- The tour's last stop is the sidebar, not the strip.
- There are 29 gallery screens, not 30, and `Ctrl+0` is still Panels.
- `IconsScreen.DEFAULT_COLUMNS` is six, which is what fits beside the sidebar
  at the restored size, so the first frame is right there.
