package io.github.digitalsmile.goldberry.assets;

/// Thrown when something asks for the emoji face and nobody brought it.
///
/// Its own type rather than an `IllegalStateException`, because it is a thing an
/// application can reasonably catch: a chat window that draws a reaction bar
/// when it can and a plain button when it cannot is doing the right thing, and
/// [BundledAssets#hasEmojiFont()] is the cheaper way to ask the same question
/// ([ADR-0384]).
public final class MissingEmojiFontException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    MissingEmojiFontException() {
        super("the emoji face is not on the module path. OpenMoji is CC BY-SA, which wants attribution a"
                + " notice file cannot give, so it ships as its own artifact: add"
                + " io.github.digitalsmile:goldberry-emoji and put its credit somewhere a reader can see it"
                + " (docs/content-widgets.md §11.1, ADR-0384).");
    }
}
