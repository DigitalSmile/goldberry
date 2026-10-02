/// Test fixtures, not shipped API: golden-image comparison shared by the tests of
/// `:core`, `:widgets` and `:gpu`, and its PNG reader by `:media`'s picture goldens.
///
/// A scene is rendered into memory and compared against a committed PNG, within a
/// tolerance because Blend2D's JIT-compiled pipelines may differ in the last bit
/// across CPUs. Beside it sit the checks a golden
/// cannot make: the same scene at another display scale, and an audit of 150% text
/// now that some ellipsis is correct. PNGs are read and written in `java.base`, so
/// no test pulls in AWT.
///
/// Null-marked, as every package is.
///
/// Read more: [Pictures](https://goldberry.dev/docs/guide/testing.html#pictures).
@NullMarked
package dev.goldberry.golden;

import org.jspecify.annotations.NullMarked;
