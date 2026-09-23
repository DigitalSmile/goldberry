package io.github.digitalsmile.goldberry.assets;

import java.util.ServiceLoader;

import org.jspecify.annotations.Nullable;

/// Where the emoji face comes from — a **service**, because the font is not in
/// this artifact.
///
/// ## Why it is not
///
/// The face moved out when it was OpenMoji, which is CC BY-SA and wants
/// *visible* attribution — so every application that shipped Goldberry
/// inherited an obligation whether or not it ever drew an emoji ([ADR-0384]).
/// The face is Noto Color Emoji now, under the SIL OFL, which asks for no such
/// thing; it stays out because it is 5 MB of paint graphs that an application
/// that never draws an emoji should not carry ([ADR-0456]).
///
/// It is `goldberry-emoji`. An application that wants emoji adds the artifact;
/// one that does not carries none of it, and
/// [io.github.digitalsmile.goldberry.text.font.Font#bundled] says so by name
/// when it is asked for a face that is not there.
///
/// ## Why a service and not a resource
///
/// A resource in another named module is encapsulated: `getResourceAsStream`
/// across a module boundary reads nothing unless the package is opened, and
/// opening a package to read one file is a wider hole than a provider. A
/// `ServiceLoader` is the module system's own answer to "somebody may have
/// brought this", and it is the same mechanism the widget catalogs use
/// (ADR-0131).
public interface EmojiFont {

    /// The face's bytes, as a TrueType file.
    ///
    /// A fresh array per call, like every other bundled face: the caller hands it
    /// to HarfBuzz and to Blend2D, and a shared mutable array of font bytes is a
    /// footgun for the sake of a megabyte.
    byte[] bytes();

    /// The provider on the module path, or null when nobody brought one.
    ///
    /// Looked up on each call rather than cached here: the answer is cached by
    /// the thing that opens faces, and a static cache in an interface is a
    /// lifetime nobody can reason about in a test.
    static @Nullable EmojiFont provider() {
        return ServiceLoader.load(EmojiFont.class).findFirst().orElse(null);
    }
}
