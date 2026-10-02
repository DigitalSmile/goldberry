package dev.goldberry.media.io;

import java.io.IOException;
import java.io.Serial;

/// No provider and no built-in protocol opens a [Source]'s URI scheme.
///
/// An [IOException], because to the caller it is one more way a source fails to
/// open. It has its own type because it is the one failure that retrying will not
/// fix. The fix is to install a [MediaIOProvider].
///
/// Read more:
/// [Tracks and the network](https://goldberry.dev/docs/components/media.html#tracks-subtitles-and-the-network).
public final class UnsupportedSchemeException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String scheme;

    public UnsupportedSchemeException(String scheme) {
        super("no MediaIO opens the scheme '" + scheme + "'; install a MediaIOProvider for it");
        this.scheme = scheme;
    }

    /// The scheme nothing opens.
    public String scheme() {
        return scheme;
    }
}
