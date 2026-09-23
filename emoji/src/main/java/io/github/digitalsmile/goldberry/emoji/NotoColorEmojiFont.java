package io.github.digitalsmile.goldberry.emoji;

import java.io.IOException;
import java.io.UncheckedIOException;

import io.github.digitalsmile.goldberry.assets.EmojiFont;

/// Noto Color Emoji, handed to `:core` through the [EmojiFont] service.
///
/// ## Which build
///
/// Google's **COLRv1** build — the one whose glyphs are paint graphs of
/// outlines, gradients and transforms — and not the `CBDT` build of 136-pixel
/// bitmaps. The bitmaps are twice the size and blur at every size larger than
/// the one they were drawn at; the graphs are drawn by the toolkit at whatever
/// size and scale the text is, and are as sharp at 400% as at 100%
/// ([ADR-0456]).
///
/// ## The licence
///
/// The font is under the **SIL Open Font License 1.1**, which asks for the
/// licence to travel with the font and for a modified version not to be called
/// Noto. It does not ask for credit on screen, which is what put the previous
/// emoji face, OpenMoji (CC BY-SA), in an artifact of its own. This stays an
/// artifact of its own for the other reason ADR-0384 gave: 5 MB is a lot to
/// inherit for an application that never draws an emoji.
///
/// [#CREDIT] is kept for an application that wants to name the face in an about
/// box anyway, which the licence welcomes and does not require.
public final class NotoColorEmojiFont implements EmojiFont {

    /// A credit an application may put on screen. Optional under the OFL.
    public static final String CREDIT = "Emoji: Noto Color Emoji by Google (https://github.com/googlefonts/noto-emoji)."
            + " Licence: SIL Open Font License 1.1.";

    /// Where the face sits in this jar — under **this module's** package, and
    /// not under `:core`'s.
    ///
    /// A resource directory is a package to the module system, so a font under
    /// `…goldberry.assets.fonts`, beside the faces `:core` ships, would make one
    /// package exist in two modules. That is a `LayerInstantiationException`
    /// before the first frame, and it is invisible on a class path, which is why
    /// every test passed and the application would not open the last time
    /// ([ADR-0387]).
    private static final String RESOURCE = "/io/github/digitalsmile/goldberry/emoji/fonts/NotoColorEmoji.ttf";

    /// Required by [java.util.ServiceLoader]: a provider is instantiated by the
    /// module system, which needs a constructor it can call.
    public NotoColorEmojiFont() {}

    @Override
    public byte[] bytes() {
        return read(RESOURCE);
    }

    /// One resource out of this jar, or a failure that says the asset step did
    /// not run.
    ///
    /// Package-private and parameterised so the absent case is reachable from a
    /// test: "the jar was assembled without its font" is exactly the failure
    /// nobody discovers until a user does.
    static byte[] read(String resource) {
        try (var in = NotoColorEmojiFont.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException(
                        resource + " is missing from goldberry-emoji, which means this jar was assembled"
                                + " without the asset step"));
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("the emoji face could not be read", e);
        }
    }
}
