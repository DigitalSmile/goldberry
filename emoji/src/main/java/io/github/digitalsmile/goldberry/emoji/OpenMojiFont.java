package io.github.digitalsmile.goldberry.emoji;

import java.io.IOException;
import java.io.UncheckedIOException;

import io.github.digitalsmile.goldberry.assets.EmojiFont;

/// OpenMoji's monochrome face, handed to `:core` through the
/// [EmojiFont] service.
///
/// ## The attribution this carries
///
/// OpenMoji is **CC BY-SA 4.0**, which asks for credit *where the work is seen*
/// — an about box, a credits screen, a help page. A notice file in a jar does
/// not meet it, which is why this is an artifact an application adds on purpose
/// rather than a file it inherits ([ADR-0384]).
///
/// The credit reads: **Emoji artwork by [OpenMoji](https://openmoji.org) — the
/// open-source emoji and icon project. Licence: CC BY-SA 4.0.** [#CREDIT] is
/// that sentence as a constant, so an application can put it on screen without
/// transcribing it.
///
/// The **black** build is what ships, per `docs/ARCHITECTURE.md` §6.2: it is a
/// fifth of the size of the colour one, and nothing in the toolkit can draw
/// layered colour outlines yet.
public final class OpenMojiFont implements EmojiFont {

    /// The credit to put on screen, as CC BY-SA asks.
    public static final String CREDIT =
            "Emoji artwork by OpenMoji (https://openmoji.org) — the open-source emoji and icon project."
                    + " Licence: CC BY-SA 4.0.";

    /// Where the face sits in this jar. The same path `:core` used to read it
    /// from, because it is the same asset step that puts it there.
    private static final String RESOURCE = "/io/github/digitalsmile/goldberry/assets/fonts/OpenMoji-black.ttf";

    /// Required by [java.util.ServiceLoader]: a provider is instantiated by the
    /// module system, which needs a constructor it can call.
    public OpenMojiFont() {}

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
        try (var in = OpenMojiFont.class.getResourceAsStream(resource)) {
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
