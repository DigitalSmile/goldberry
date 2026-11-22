package dev.goldberry.widget.attr;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.StyleElement;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Element;

/// HTML's `spellcheck` and `lang` on a node: whether the text fields inside it
/// check their spelling, and in which language.
///
/// ```kdl
/// panel spellcheck=true lang="en-GB" {
///     text-area id="notes"
///     text-input id="code" spellcheck=false
/// }
/// ```
///
/// Both pass down the tree, as in HTML: a field takes the nearest node, itself
/// included, that says either. A field checks only when one says
/// `spellcheck=true`. A password field never checks, whatever the tree says.
///
/// @param check whether to check, or null to leave it to the nodes above
/// @param lang  the language, or null to leave it to the nodes above
public record Spelling(@Nullable Boolean check, @Nullable Locale lang) {

    /// Says nothing either way: what every node has unless it is told.
    public static final Spelling NONE = new Spelling(null, null);

    /// This, checking or not.
    public Spelling check(boolean value) {
        return new Spelling(value, lang);
    }

    /// This, in `value`.
    public Spelling lang(@Nullable Locale value) {
        return new Spelling(check, value);
    }

    /// Whether it says nothing.
    public boolean isEmpty() {
        return check == null && lang == null;
    }

    /// What the field at `context` is told: each half from the nearest node that
    /// says it, starting at the field's own. Checking is off and the language
    /// unset when nothing says.
    public static Spelling of(BuildContext context) {
        Boolean check = null;
        Locale lang = null;
        StyleElement at = context instanceof Element element ? element : null;
        while (at instanceof Element element && (check == null || lang == null)) {
            if (element.widget() instanceof Attributed<?> attributed) {
                var own = attributed.attributes().spelling();
                if (check == null) {
                    check = own.check();
                }
                if (lang == null) {
                    lang = own.lang();
                }
            }
            at = element.parent();
        }
        return new Spelling(check != null && check, lang);
    }

    /// Whether the resolved answer says to check.
    public boolean checks() {
        return Boolean.TRUE.equals(check);
    }
}
