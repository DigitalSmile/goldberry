package dev.goldberry.html.style;

import java.util.ArrayList;
import java.util.List;

/// A mail's CSS cut into the pieces the rest of this package reads one at a time:
/// the rules of a `<style>`, and the declarations of a rule or a `style=`.
///
/// **Cut by hand rather than handed whole to the toolkit's parser**, because the
/// two jobs differ. The toolkit's parser reads a stylesheet somebody wrote for
/// the toolkit, and refuses or warns about what it cannot do. A mail's CSS was
/// written for a dozen mail clients, so most of it is outside any subset —
/// `mso-` properties, `@media` queries for phones, `@font-face`, conditional
/// comments — and none of that is worth a warning in the application's log. So
/// the text is cut here into rules and declarations, each is parsed on its own,
/// and one that fails is dropped without taking its neighbours with it.
///
/// Comments, `<!--` and `-->` (which old mail wraps a `<style>` in), strings and
/// nested brackets are stepped over. **Every at-rule is skipped whole**, block and
/// all: `@media`, `@font-face`, `@import`, `@keyframes`, `@supports`, `@page`. A
/// mail's `@media` is for a phone's width rather than a pane's, and the rest
/// either fetches something or animates something.
final class StyleText {

    private StyleText() {}

    /// One qualified rule: the text before its `{` and the text inside it.
    record Rule(String prelude, String body) {}

    /// The rules of `css`, in order, with every at-rule left out.
    ///
    /// A rule with no closing brace, which is a truncated mail, ends the text.
    static List<Rule> rules(String css) {
        var rules = new ArrayList<Rule>();
        var text = withoutComments(css);
        var at = 0;
        while (at < text.length()) {
            at = skipSpace(text, at);
            if (at >= text.length()) {
                break;
            }
            if (text.charAt(at) == '@') {
                at = skipAtRule(text, at);
                continue;
            }
            var open = find(text, at, '{');
            if (open < 0) {
                break;
            }
            var close = matching(text, open);
            var prelude = text.substring(at, open).strip();
            var body = text.substring(open + 1, close < 0 ? text.length() : close);
            if (!prelude.isEmpty()) {
                rules.add(new Rule(prelude, body));
            }
            if (close < 0) {
                break;
            }
            at = close + 1;
        }
        return rules;
    }

    /// The declarations of `body`, each `property: value` as written, with the
    /// separators and empty entries gone.
    static List<String> declarations(String body) {
        return split(withoutComments(body), ';');
    }

    /// The selectors of `prelude`, split at its top-level commas: `a, b:is(c, d)`
    /// is two.
    static List<String> selectors(String prelude) {
        return split(prelude, ',');
    }

    // --- stepping --------------------------------------------------------------

    /// `text` with every `/* … */` comment and every `<!--` and `-->` replaced by a
    /// space, so that nothing else has to know about them.
    static String withoutComments(String text) {
        var out = new StringBuilder(text.length());
        var i = 0;
        while (i < text.length()) {
            var c = text.charAt(i);
            if (c == '"' || c == '\'') {
                var end = endOfString(text, i);
                out.append(text, i, end);
                i = end;
            } else if (text.startsWith("/*", i)) {
                var end = text.indexOf("*/", i + 2);
                out.append(' ');
                i = end < 0 ? text.length() : end + 2;
            } else if (text.startsWith("<!--", i)) {
                out.append(' ');
                i += 4;
            } else if (text.startsWith("-->", i)) {
                out.append(' ');
                i += 3;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static List<String> split(String text, char separator) {
        var parts = new ArrayList<String>();
        var depth = 0;
        var start = 0;
        var i = 0;
        while (i < text.length()) {
            var c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = endOfString(text, i);
                continue;
            }
            if (c == '(' || c == '[') {
                depth++;
            } else if ((c == ')' || c == ']') && depth > 0) {
                depth--;
            } else if (c == separator && depth == 0) {
                add(parts, text.substring(start, i));
                start = i + 1;
            }
            i++;
        }
        add(parts, text.substring(start));
        return parts;
    }

    private static void add(List<String> parts, String part) {
        var stripped = part.strip();
        if (!stripped.isEmpty()) {
            parts.add(stripped);
        }
    }

    private static int skipSpace(String text, int at) {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
        return at;
    }

    /// Past an at-rule: to its `;` when it has no block, or past its block's `}`.
    private static int skipAtRule(String text, int at) {
        var i = at;
        while (i < text.length()) {
            var c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = endOfString(text, i);
                continue;
            }
            if (c == ';') {
                return i + 1;
            }
            if (c == '{') {
                var close = matching(text, i);
                return close < 0 ? text.length() : close + 1;
            }
            i++;
        }
        return text.length();
    }

    /// The first `target` at or after `from` outside a string, or -1.
    private static int find(String text, int from, char target) {
        var i = from;
        while (i < text.length()) {
            var c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = endOfString(text, i);
                continue;
            }
            if (c == target) {
                return i;
            }
            i++;
        }
        return -1;
    }

    /// The `}` that closes the `{` at `open`, or -1 when the text ends first.
    private static int matching(String text, int open) {
        var depth = 0;
        var i = open;
        while (i < text.length()) {
            var c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = endOfString(text, i);
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
            i++;
        }
        return -1;
    }

    /// Just past the string opening at `at`, honouring a backslash escape; the end
    /// of the text for a string nobody closed.
    private static int endOfString(String text, int at) {
        var quote = text.charAt(at);
        var i = at + 1;
        while (i < text.length()) {
            var c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote || c == '\n') {
                return i + 1;
            }
            i++;
        }
        return text.length();
    }
}
