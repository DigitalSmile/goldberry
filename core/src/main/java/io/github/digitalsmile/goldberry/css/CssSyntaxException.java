package io.github.digitalsmile.goldberry.css;

/// A stylesheet that could not be read, with the place that stopped it.
///
/// Thrown rather than recovered from. The CSS spec's own error handling is to
/// discard the offending construct and carry on, which is right for a browser
/// rendering somebody else's page and wrong for a toolkit loading a stylesheet
/// the application shipped: a silently dropped rule is a widget that is the
/// wrong colour with nothing in the log to say why.
///
/// Hot reload (§8) is the one place that changes — a stylesheet saved mid-edit
/// is *expected* to be broken, so the reload path catches this, keeps the last
/// good stylesheet and reports, rather than tearing the window down.
public final class CssSyntaxException extends RuntimeException {

    private final int line;
    private final int column;

    public CssSyntaxException(String message, int line, int column) {
        super(message + " (line " + line + ", column " + column + ")");
        this.line = line;
        this.column = column;
    }

    /// 1-based line the error was found on.
    public int line() {
        return line;
    }

    /// 1-based column the error was found at.
    public int column() {
        return column;
    }
}
