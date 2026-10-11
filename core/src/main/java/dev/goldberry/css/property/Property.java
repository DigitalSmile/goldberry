package dev.goldberry.css.property;

/// One name in the property table.
///
/// Three kinds, because CSS has three: a [Longhand] holds a value of its own, a
/// [Shorthand] writes several longhands at once, and an [Alias] is another
/// spelling of one property (`word-wrap` for `overflow-wrap`). A fourth,
/// [Inert], is read and changes nothing, as CSS says for these boxes. A reader
/// that switches over them handles all four or does not compile.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#properties).
public sealed interface Property permits Longhand, Shorthand, Alias, Inert {

    /// The name a declaration is written with, lowercased.
    String name();

    /// Whether a declaration of this name is read by the engine.
    ///
    /// False for a longhand the engine stores and no declaration can write on
    /// its own yet, which only a shorthand sets. There is none today; the flag
    /// is how one is added without the property table claiming the engine
    /// reads it.
    boolean readable();
}
