/// SDL's log categories and priorities, and the logger name and SLF4J level each one
/// becomes when SDL's own messages are routed into the application's log.
///
/// Constants that touch no foreign memory, checked against the compiled library.
/// Not exported; the log bridge in `…natives.sdl` is what reads them.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.natives.sdl.log;

import org.jspecify.annotations.NullMarked;
