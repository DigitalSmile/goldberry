/// The Goldberry showcase: the application, what it knows, and the commands that
/// belong to its window rather than to its model.
///
/// Not exported; the module is an application. Opened to `:core`, which reads
/// `showcase.css` from it and binds the model's fields and actions reflectively at
/// run time rather than through the weaver (ADR-0093, ADR-0155). The screens are in
/// `…example.ui`.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.example;

import org.jspecify.annotations.NullMarked;
