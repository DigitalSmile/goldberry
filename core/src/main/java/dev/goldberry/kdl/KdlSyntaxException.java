package dev.goldberry.kdl;

/// Markup that could not be read, with the place that stopped it.
///
/// Markup is strict by default: an unknown node is a hard error with a source
/// position. The same reasoning as
/// [dev.goldberry.css.parse.CssSyntaxException] —
/// a silently dropped node is a widget that is not on screen with nothing in the
/// log to say why — and the same exception for hot reload to catch: markup saved
/// mid-edit is *expected* to be broken, so the reload path keeps the last good
/// document and reports rather than tearing the window down.
///
/// Read more: [Strict by default](https://goldberry.dev/docs/guide/markup.html#strict-by-default).
public final class KdlSyntaxException extends RuntimeException {

    private final int line;
    private final int column;

    public KdlSyntaxException(String message, int line, int column) {
        super(line > 0 ? message + " (line " + line + ", column " + column + ")" : message);
        this.line = line;
        this.column = column;
    }

    /// 1-based line, or 0 if the error has no position.
    public int line() {
        return line;
    }

    /// 1-based column, or 0.
    public int column() {
        return column;
    }
}
