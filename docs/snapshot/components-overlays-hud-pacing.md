# Guide addition held back until the release: the `pacing` reading

For `book/src/components/overlays.md`, the `hud` section's list of readings.
Replace the sentence "The readings are `fps`, `refresh`, `late`, …" with:

The readings are `fps`, `refresh`, `pacing`, `late`, `paint`, `build`, `style`,
`layout`, `raster`, `upload`, `acquire` and `submit`. `pacing` shows the
window's present mode and frame cap, `pacing vsync` or `pacing mailbox, cap
144`, and is never coloured. `stages` is `fps`, `refresh`, `late` and the five
stages, which is what `Hud.stages()` builds. `present` is the GPU canvas's set.
A word that is not one of them is refused.

And in **Styling**, after "passes the frame budget": the budget is a share of
the display's frame, or of the cap's where the cap is slower or the window
does not wait for the display.
