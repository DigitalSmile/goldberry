/// What this build of the platform layer can actually do, whatever its API says
/// (ADR-0325).
///
/// Every platform method exists everywhere; the implementation behind it may have
/// been compiled out of the native library this process loaded. Exported because
/// an application that follows the desktop has to tell "the desktop says nothing"
/// from "this build cannot ask": the first is a default and the second a bug
/// report (`docs/gaps.md` G32). `Goldberry.capabilities()` is the door, and this is
/// the one place `:natives`' word for it becomes the toolkit's (ADR-0174).
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.platform;

import org.jspecify.annotations.NullMarked;
