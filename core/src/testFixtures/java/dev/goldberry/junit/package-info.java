/// Test fixtures, not shipped API: JUnit 5 extensions shared by the tests of
/// every module.
///
/// One so far. [dev.goldberry.junit.HeadlessRuntime] gives a test the running
/// runtime a widget expects to find, on the `headless` backend, with no window
/// and no native library (ADR-0517).
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.junit;

import org.jspecify.annotations.NullMarked;
