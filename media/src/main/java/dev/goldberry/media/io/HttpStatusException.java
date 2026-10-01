package dev.goldberry.media.io;

import java.io.IOException;
import java.io.Serial;
import java.net.URI;

/// An HTTP server answered a request for media with a status that carries none:
/// `404`, `401`, `503` and the like.
///
/// An [IOException], because to the caller it is one more way a source fails to
/// open. It has its own type so that an application can tell "not found" from
/// "not allowed" by [#status()] rather than by parsing a message.
public final class HttpStatusException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int status;
    private final URI uri;

    public HttpStatusException(int status, URI uri) {
        super("HTTP " + status + " from " + uri);
        this.status = status;
        this.uri = uri;
    }

    /// The status code the server answered with.
    public int status() {
        return status;
    }

    /// What was asked for.
    public URI uri() {
        return uri;
    }
}
