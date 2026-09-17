/// The OpenMoji face, and the attribution obligation that comes with it.
///
/// One class and one font. `docs/content-widgets.md` §11.1 quarantines anything
/// with an attribution obligation into an artifact an application opts into, and
/// this is that artifact: CC BY-SA wants credit *where the work is seen*, so an
/// application that draws emoji takes the dependency and puts
/// [io.github.digitalsmile.goldberry.emoji.OpenMojiFont#CREDIT] somewhere a
/// reader can see it ([ADR-0384](../book/src/adr/0384-the-emoji-face-is-an-artifact-an-application-opts-into.md)).
///
/// Nothing in `:core` or `:widgets` knows this module exists. The face arrives
/// through a service, which is how the module system says "somebody may have
/// brought this".
module io.github.digitalsmile.goldberry.emoji {

    /// The service this implements, and the font stack that asks for it.
    requires transitive io.github.digitalsmile.goldberry.core;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    requires transitive static org.jspecify;

    /// The provider, and the credit as a constant.
    exports io.github.digitalsmile.goldberry.emoji;

    /// What makes `Font.bundled(BundledFont.EMOJI, …)` work in a build that has
    /// this module on its path — and, by its absence, what makes the failure a
    /// sentence naming this artifact rather than a missing resource.
    provides io.github.digitalsmile.goldberry.assets.EmojiFont
            with io.github.digitalsmile.goldberry.emoji.OpenMojiFont;
}
