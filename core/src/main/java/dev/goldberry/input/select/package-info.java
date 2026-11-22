/// Selecting the words of an ordinary text box: what `user-select: text` and
/// `user-select: all` turn on for a `text` and any other box that draws words
/// and does not handle the pointer itself.
///
/// One selection per tree, as a browser keeps one per document. A press on a
/// selectable box starts it, a drag extends it, a double click takes a word and
/// a triple click all of it; `Ctrl+C` copies it when nothing focused took the
/// key, and on a desktop with a primary selection the finished selection goes
/// there too. A press anywhere else clears it.
///
/// Internal to the toolkit: the renderer registers what each frame drew, and
/// the router drives the gesture.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#box-and-layout).
///
/// `@NullMarked`, which puts this package under NullAway.
@NullMarked
package dev.goldberry.input.select;

import org.jspecify.annotations.NullMarked;
