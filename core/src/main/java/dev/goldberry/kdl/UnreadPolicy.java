package dev.goldberry.kdl;

/// What a [KdlInflater] does with a property nothing read.
///
/// A factory asks its node for the properties it understands, and a property it
/// never asks for has no effect at all: a misspelt `gpa=8`, a `gap=8` on a
/// widget whose gap is the stylesheet's, a `value=` on a control bound with
/// `bind=`. The inflater notes every property asked for while a document is
/// built, by whichever factory asks, and compares that with what the document
/// wrote once the whole tree is built.
///
/// Read more: [Markup](https://goldberry.dev/docs/guide/markup.html#parsing-and-inflating).
public enum UnreadPolicy {

    /// Refused, as an unknown node is: a [KdlSyntaxException] at the first
    /// unread property's node, listing every other one.
    REFUSE,

    /// Logged, one warning per property, and the document is built.
    WARN,

    /// Neither noted nor reported.
    IGNORE
}
