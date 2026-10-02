package dev.goldberry.assets;

import java.util.ServiceLoader;

import org.jspecify.annotations.Nullable;

/// The service through which the emoji face reaches the toolkit, because the
/// font is not in this artifact.
///
/// The face is Noto Color Emoji, and it ships as `goldberry-emoji`: 5 MB of paint
/// graphs that an application which never draws an emoji should not carry. An
/// application that wants emoji adds the artifact to its module path and nothing
/// else; one that does not carries none of it, and
/// [dev.goldberry.text.font.Font#bundled] says so by name when it is asked for a
/// face that is not there.
///
/// A service rather than a resource, because a resource in another named module
/// is encapsulated: `getResourceAsStream` across a module boundary reads nothing
/// unless the package is opened, and opening a package to read one file is a
/// wider hole than a provider. A `ServiceLoader` is the module system's own
/// answer to "somebody may have brought this", and it is the same mechanism the
/// widget catalogs use to announce themselves.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#emoji).
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
