package dev.goldberry.css.conformance;

import java.util.Map;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Declaration;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.css.value.CssLength;

/// Asks the engine what it does with a [Row], the way the support analysis did.
///
/// A sheet is parsed lenient: refused whole is `MALFORMED`, a rule dropped is
/// `REFUSED`, anything else `PARSED`. A declaration is parsed inside a rule of
/// its own and handed to the engine: `ComputedStyle.isProperty` says whether the
/// property exists, and `ComputedStyle.applies` whether the value was read. Both
/// run the engine rather than consult a list, so this cannot fall out of step
/// with it.
final class Probe {

    static final String ORIGIN = "the conformance table";

    /// What a declaration is read under: the default font sizes, in a window,
    /// as every frame of a running application is. Without one a viewport unit
    /// is not yet a length.
    private static final CssLength.Context CONTEXT = CssLength.Context.DEFAULT.withViewport(1280, 800);

    private Probe() {}

    static Outcome of(Row row) {
        return switch (row.kind()) {
            case DECLARATION -> declaration(row);
            case SHEET -> sheet(row.css());
        };
    }

    /// Whether a strict parse refuses `css` as outside the subset rather than as
    /// malformed. Asked of every `REFUSED` row, so that the two modes agree on
    /// what the subset is.
    static boolean strictRefusesAsUnsupported(String css) {
        try {
            CssParser.parseSheet(css, ParseMode.STRICT, ORIGIN);
            return false;
        } catch (CssSyntaxException e) {
            return e.isUnsupportedFeature();
        }
    }

    private static Outcome sheet(String css) {
        try {
            var parsed = CssParser.parseSheet(css, ParseMode.LENIENT, ORIGIN);
            return parsed.dropped().isEmpty() ? Outcome.PARSED : Outcome.REFUSED;
        } catch (CssSyntaxException _) {
            return Outcome.MALFORMED;
        }
    }

    private static Outcome declaration(Row row) {
        Declaration declaration;
        try {
            declaration = only(row.css());
        } catch (CssSyntaxException _) {
            return Outcome.MALFORMED;
        }
        if (!ComputedStyle.isProperty(declaration.property())) {
            return Outcome.UNKNOWN;
        }
        if (!ComputedStyle.applies(declaration.property(), declaration.value(), CONTEXT)) {
            return Outcome.DROPPED;
        }
        var reference = row.reference();
        if (reference != null && style(declaration).equals(style(only(reference)))) {
            return Outcome.IGNORED;
        }
        return Outcome.APPLIED;
    }

    /// The one declaration in `css`, read from a rule of its own.
    private static Declaration only(String css) {
        var parsed = CssParser.parseSheet("probe { " + css + " }", ParseMode.LENIENT, ORIGIN);
        var declarations = parsed.rules().getFirst().declarations();
        if (declarations.size() != 1) {
            throw new IllegalArgumentException("a decl row holds one declaration, not " + declarations.size());
        }
        return declarations.getFirst();
    }

    private static ComputedStyle style(Declaration declaration) {
        return ComputedStyle.of(Map.of(declaration.property(), declaration.value()), CONTEXT);
    }
}
