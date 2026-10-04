/// Test fixtures, not shipped API: what a test in every module shares with JUnit.
///
/// [dev.goldberry.junit.HeadlessRuntime] gives a test the running runtime a
/// widget expects to find, on the `headless` backend, with no window and no
/// native library, so a lane without `libgoldberry` stays green.
/// [dev.goldberry.junit.TimeBudget] is the bound a test that has to read a clock
/// compares with, with room for a loaded machine and never enough to let the
/// defect it guards against pass. [dev.goldberry.junit.WallClock] marks the
/// test that has to, so it runs in the wall-clock lane, alone, and is run
/// again before a failure is believed. [dev.goldberry.junit.DrivenRuntime] is
/// for the test that does not have to: the launcher over a clock it moves, so a
/// delay is read at an exact time rather than slept past.
///
/// Null-marked, as every package is.
///
/// Read more: [Running the launcher
/// without a display](https://goldberry.dev/docs/guide/testing.html#running-the-launcher-without-a-display).
@NullMarked
package dev.goldberry.junit;

import org.jspecify.annotations.NullMarked;
