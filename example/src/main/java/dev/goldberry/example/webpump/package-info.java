/// A probe, not part of the showcase: it measures how fast an embedded web engine's
/// animation actually runs, by having the page count its own frames and report them
/// through a binding.
///
/// On Linux the engine's GLib main context is serviced by Goldberry's event loop,
/// and this is how the constants of that pump are measured rather than guessed. Run
/// by `:example:webPumpProbe`.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.example.webpump;

import org.jspecify.annotations.NullMarked;
