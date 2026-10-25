/// The Goldberry showcase: the application, and the commands that belong to its
/// window rather than to its model. What it knows is in `…example.model`, a
/// package per part of the window.
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
