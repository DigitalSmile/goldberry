package io.github.digitalsmile.goldberry.media.io;

import java.io.IOException;
import java.util.Set;

/// A protocol an application brings: opens [Source]s of the URI schemes it names.
///
/// Found by [java.util.ServiceLoader]. A module declares
/// `provides io.github.digitalsmile.goldberry.media.io.MediaIOProvider with …`,
/// and a class-path jar lists itself in
/// `META-INF/services/io.github.digitalsmile.goldberry.media.io.MediaIOProvider`.
/// This is how a protocol is added without touching the natives
/// (`docs/goldberry-media.md` §5): an `s3:` bucket, a content-addressed store, an
/// encrypted archive.
///
/// Providers are consulted before the built-in protocols, highest [#priority()]
/// first, so an application may also *replace* `file:` — to read through a
/// sandbox, for instance.
public interface MediaIOProvider {

    /// The URI schemes this provider opens, in lower case.
    Set<String> schemes();

    /// Opens `source`. Called only for a scheme in [#schemes()].
    ///
    /// @throws IOException when the resource cannot be opened
    MediaIO open(Source source) throws IOException;

    /// Higher wins when two providers claim one scheme. The built-in protocols
    /// rank below every provider, whatever it answers here.
    default int priority() {
        return 0;
    }
}
