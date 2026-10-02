package dev.goldberry.assets;

import java.io.Serial;

/// Thrown when something asks for the emoji face and the `goldberry-emoji`
/// artifact is not on the module path.
///
/// Its own type rather than an `IllegalStateException`, because it is a thing an
/// application can reasonably catch: a chat window that draws a reaction bar
/// when it can and a plain button when it cannot is doing the right thing, and
/// [BundledAssets#hasEmojiFont()] is the cheaper way to ask the same question.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#emoji).
public final class MissingEmojiFontException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    MissingEmojiFontException() {
        super("the emoji face is not on the module path. Noto Color Emoji ships as its own artifact, so an"
                + " application that never draws an emoji does not carry it: add"
                + " dev.goldberry:goldberry-emoji to draw emoji.");
    }
}
