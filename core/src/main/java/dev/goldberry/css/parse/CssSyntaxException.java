package dev.goldberry.css.parse;

/// A stylesheet that could not be read, with the place that stopped it.
///
/// Thrown rather than recovered from in a [ParseMode#STRICT] parse, which is
/// how the toolkit's own sheets are read: a rule in them that matched nothing
/// would be a control drawn wrong in every application. An application's sheet
/// is parsed [ParseMode#LENIENT], where a construct outside the subset drops
/// its rule with a warning instead, and only a malformed sheet throws this.
///
/// Hot reload is the one place that changes — a stylesheet saved mid-edit
/// is *expected* to be broken, so the reload path catches this, keeps the last
/// good stylesheet and reports, rather than tearing the window down.
///
/// Read more: [Markup](https://goldberry.dev/docs/guide/markup.html#hot-reload).
public final class CssSyntaxException extends RuntimeException {

    private final int line;
    private final int column;
    private final boolean unsupported;

    public CssSyntaxException(String message, int line, int column) {
        this(message, line, column, false);
    }

    /// @param unsupported whether the sheet asked for something outside the
    ///                    subset, rather than being malformed
    CssSyntaxException(String message, int line, int column, boolean unsupported) {
        super(message + " (line " + line + ", column " + column + ")");
        this.line = line;
        this.column = column;
        this.unsupported = unsupported;
    }

    /// Whether this is a construct the subset has not got, such as `::before`
    /// or `@supports`, rather than a mistake such as an unclosed block.
    ///
    /// The difference [ParseMode#LENIENT] turns on: the first drops one rule
    /// with a warning there, and the second refuses the sheet in either mode.
    public boolean isUnsupportedFeature() {
        return unsupported;
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
