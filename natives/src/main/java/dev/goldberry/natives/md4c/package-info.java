/// md4c, the Markdown parser, as one call that hands back a list of events.
///
/// The events are encoded natively into one buffer and read once, so nothing
/// crosses the FFM boundary per block and md4c's detail structs are never modelled
/// in Java; what leaves is records that carry no foreign memory (ADR-0294).
///
/// Exported to `:html` and to nobody else — `:core` cannot see it.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.natives.md4c;

import org.jspecify.annotations.NullMarked;
