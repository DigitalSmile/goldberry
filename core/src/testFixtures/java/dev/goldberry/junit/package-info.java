/// Test fixtures, not shipped API: what a test in every module shares with JUnit.
///
/// [dev.goldberry.junit.HeadlessRuntime] gives a test the running runtime a
/// widget expects to find, on the `headless` backend, with no window and no
/// native library, so a lane without `libgoldberry` stays green.
/// [dev.goldberry.junit.TimeBudget] is the bound a test that has to read a clock
/// compares with, with room for a loaded machine and never enough to let the
/// defect it guards against pass.
///
/// Null-marked, as every package is.
///
/// Read more: [Running the launcher
/// without a display](https://goldberry.dev/docs/guide/testing.html#running-the-launcher-without-a-display).
@NullMarked
package dev.goldberry.junit;

import org.jspecify.annotations.NullMarked;
