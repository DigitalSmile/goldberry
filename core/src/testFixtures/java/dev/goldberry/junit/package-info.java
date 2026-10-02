/// Test fixtures, not shipped API: JUnit 5 extensions shared by the tests of
/// every module.
///
/// One so far. [dev.goldberry.junit.HeadlessRuntime] gives a test the running
/// runtime a widget expects to find, on the `headless` backend, with no window
/// and no native library, so a lane without `libgoldberry` stays green.
///
/// Null-marked, as every package is.
///
/// Read more: [Running the launcher
/// without a display](https://goldberry.dev/docs/guide/testing.html#running-the-launcher-without-a-display).
@NullMarked
package dev.goldberry.junit;

import org.jspecify.annotations.NullMarked;
