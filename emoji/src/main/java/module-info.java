/// Noto Color Emoji — the emoji face, as an artifact an application adds on
/// purpose.
///
/// One class and one font. The face is 5 MB of paint graphs, and an application
/// that never draws an emoji should not carry it; one that does adds this
/// artifact and gets emoji in every paragraph it draws
/// ([ADR-0384](../book/src/adr/0384-the-emoji-face-is-an-artifact-an-application-opts-into.md),
/// [ADR-0456](../book/src/adr/0456-the-emoji-face-is-noto-drawn-from-its-paint-graphs.md)).
///
/// Nothing in `:core` or `:widgets` knows this module exists. The face arrives
/// through a service, which is how the module system says "somebody may have
/// brought this".
module io.github.digitalsmile.goldberry.emoji {

    /// The service this implements, and the font stack that asks for it.
    requires transitive io.github.digitalsmile.goldberry.core;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    requires transitive static org.jspecify;

    /// The provider, and an optional credit as a constant.
    exports io.github.digitalsmile.goldberry.emoji;

    /// What makes `Font.bundled(BundledFont.EMOJI, …)` work in a build that has
    /// this module on its path — and, by its absence, what makes the failure a
    /// sentence naming this artifact rather than a missing resource.
    provides io.github.digitalsmile.goldberry.assets.EmojiFont
            with io.github.digitalsmile.goldberry.emoji.NotoColorEmojiFont;
}
