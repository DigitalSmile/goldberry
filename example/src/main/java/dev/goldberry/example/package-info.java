/// The Goldberry showcase: the application, what it knows, and the commands that
/// belong to its window rather than to its model.
///
/// Not exported; the module is an application. Opened to `:core`, which reads
/// `showcase.css` from it and binds the model's fields and actions reflectively at
/// run time rather than through the weaver. The screens are in
/// `…example.ui`.
///
/// Marked for NullAway, as every package in the repository is.
///
/// Read more: [Building an application](https://goldberry.dev/docs/applications.html).
@NullMarked
package dev.goldberry.example;

import org.jspecify.annotations.NullMarked;
