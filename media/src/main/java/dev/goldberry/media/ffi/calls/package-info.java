/// The holders: one final class per bound FFmpeg function, grouped by library into
/// `…Calls` records.
///
/// A package of its own so that a native image can initialise it at build time
/// by naming it, which is what makes each `FD_…` handle a constant rather than a
/// 450× slower lambda form (ADR-0161, ADR-0173). Nothing else belongs here.
@NullMarked
package dev.goldberry.media.ffi.calls;

import org.jspecify.annotations.NullMarked;
