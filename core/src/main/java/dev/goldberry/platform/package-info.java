/// What this build of the platform layer can actually do, whatever its API says.
///
/// Every platform method exists everywhere; the implementation behind it may have
/// been compiled out of the native library this process loaded. Exported because
/// an application that follows the desktop has to tell "the desktop says nothing"
/// from "this build cannot ask": the first is a default and the second a bug
/// report. `Goldberry.capabilities()` is the door, and this is the one place the
/// native module's word for it becomes the toolkit's.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more:
/// [Logging and diagnostics](https://goldberry.dev/docs/guide/logging.html#what-this-build-can-do).
@NullMarked
package dev.goldberry.platform;

import org.jspecify.annotations.NullMarked;
