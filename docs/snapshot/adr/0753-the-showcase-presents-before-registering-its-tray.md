# ADR-0753: The Showcase presents before registering its tray

- **Status:** Accepted
- **Date:** 2026-11-28
- **Relates to:** ADR-0028, ADR-0750

## Decision

Schedule the Showcase tray after the first successful presentation. The frame
listener unregisters itself and queues the tray on the UI thread after the
event pump. Stopping the application cancels the pending listener or timer.
The launcher uses the same stylesheet list to build and lint each renderer.

The startup timeline now records the window icon, cursors, font catalogue,
application initialization, widget mount, first rasterization and first
presentation. The macOS Showcase workflow prints that timeline in its job
summary. `-Dgoldberry.startup.log.level=TRACE` enables the timeline on its own.

## Verification

Headless tests check that the tray opens once, after the first presentation,
and stays closed if the loop stops before painting. A launcher test checks
that the first renderer requests stylesheets once.

Fifteen paired native Linux x64 runs at 1280 × 800 with SDL's dummy driver
put median first presentation at 248.5 ms before and 229.7 ms after. The
macOS timing still needs a run on the Cocoa driver.
