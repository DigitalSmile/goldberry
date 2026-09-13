package io.github.digitalsmile.goldberry.html;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import io.github.digitalsmile.goldberry.content.entity.Entities;
import io.github.digitalsmile.goldberry.html.model.HtmlAttributes;
import io.github.digitalsmile.goldberry.html.model.Tags;

/// The source, as tokens.
///
/// ## Why there is no failure mode
///
/// HTML has no syntax errors either — not in the sense a compiler means. Every
/// browser ever shipped renders `<b>bold<i>both</b>` and an unterminated `<div`, and
/// a content renderer that threw on a page a browser draws would be useless for
/// exactly the corpus it exists to read: help pages, changelogs and the HTML half of
/// an email, all of it written by hand and some of it by a templating language having
/// a bad day. So every case below **recovers**, and what it recovers to is written
/// down beside it (ADR-0298).
///
/// ## What it is not
///
/// Not the HTML5 tokenizer. That specification has 80 states because it describes
/// what a *browser* must do with a hostile document — script insertion points, the
/// `<plaintext>` element, four kinds of bogus comment, error recovery that is
/// byte-compatible across implementations. This reads the constructs an authored
/// document contains, and the differences are listed in `Html`'s own documentation
/// rather than left for somebody to discover: a reader who needs the other thing
/// needs a browser engine, and that is what `docs/content-widgets.md` §1 parks.
final class HtmlTokenizer {

    private final String source;

    private int at;

    HtmlTokenizer(String source) {
        this.source = source;
    }

    /// Every token in the source, in order.
    static List<Token> tokenize(String source) {
        return new HtmlTokenizer(source).run();
    }

    private List<Token> run() {
        var tokens = new ArrayList<Token>();
        while (at < source.length()) {
            var lessThan = source.indexOf('<', at);
            if (lessThan < 0) {
                characters(tokens, source.substring(at));
                break;
            }
            if (lessThan > at) {
                characters(tokens, source.substring(at, lessThan));
            }
            at = lessThan;
            markup(tokens);
        }
        return tokens;
    }

    /// What follows a `<`.
    private void markup(List<Token> tokens) {
        if (source.startsWith("<!--", at)) {
            note(tokens);
        } else if (source.startsWith("</", at)) {
            endTag(tokens);
        } else if (source.startsWith("<!", at) || source.startsWith("<?", at)) {
            // A doctype, a processing instruction or a bogus comment. Read to the `>`
            // and drop it: the model has no node for any of them, and a content
            // renderer has nothing to do with a doctype it was handed.
            skipTo('>');
        } else if (at + 1 < source.length() && isNameStart(source.charAt(at + 1))) {
            startTag(tokens);
        } else {
            // `a < b`, or a `<` at the very end of the file. A less-than sign that
            // begins nothing is a less-than sign, which is what a browser does and
            // what the author meant.
            characters(tokens, "<");
            at++;
        }
    }

    private void note(List<Token> tokens) {
        var end = source.indexOf("-->", at + 4);
        if (end < 0) {
            // Unterminated: the rest of the file is the comment. A page truncated
            // mid-comment shows nothing rather than showing its own source.
            tokens.add(new Token.Note(source.substring(at + 4)));
            at = source.length();
            return;
        }
        tokens.add(new Token.Note(source.substring(at + 4, end)));
        at = end + 3;
    }

    private void endTag(List<Token> tokens) {
        at += 2;
        var name = name();
        // Whatever else is in a close tag is parsed for its shape and thrown away,
        // which is what HTML says to do with `</p class="x">`.
        skipTo('>');
        if (!name.isEmpty()) {
            tokens.add(new Token.End(name));
        }
    }

    private void startTag(List<Token> tokens) {
        at++;
        var name = name();
        var attributes = attributes();
        var selfClosing = false;
        if (at < source.length() && source.charAt(at) == '/') {
            selfClosing = true;
            at++;
        }
        if (at < source.length() && source.charAt(at) == '>') {
            at++;
        }
        tokens.add(new Token.Start(name, attributes, selfClosing));
        if (!selfClosing && Tags.isRawText(name)) {
            rawText(tokens, name);
        }
    }

    /// The content of a `script` or a `style`, and its close tag.
    ///
    /// Read as text to the matching close tag: a `<` in a program is a less-than sign
    /// and an `&&` is not a broken entity, so nothing in here is markup and nothing in
    /// here is resolved.
    private void rawText(List<Token> tokens, String tag) {
        var closing = "</" + tag;
        var end = indexOfIgnoreCase(closing, at);
        var content = end < 0 ? source.substring(at) : source.substring(at, end);
        if (!content.isEmpty()) {
            tokens.add(new Token.Characters(content));
        }
        if (end < 0) {
            at = source.length();
            return;
        }
        at = end + closing.length();
        skipTo('>');
        tokens.add(new Token.End(tag));
    }

    /// The attributes of the tag being read, up to its `>` or `/>`.
    private HtmlAttributes attributes() {
        var byName = new LinkedHashMap<String, String>();
        while (at < source.length()) {
            skipWhitespace();
            if (at >= source.length()) {
                break;
            }
            var c = source.charAt(at);
            if (c == '>' || (c == '/' && at + 1 < source.length() && source.charAt(at + 1) == '>')) {
                break;
            }
            var name = attributeName();
            if (name.isEmpty()) {
                // A `=` or a stray `/` where a name should be. One character forward,
                // so that a malformed tag cannot become an endless loop — the failure
                // mode this guard exists for is a hang rather than a wrong picture.
                at++;
                continue;
            }
            // First one wins, which is HTML's rule for a repeated attribute.
            byName.putIfAbsent(name, attributeValue());
        }
        return HtmlAttributes.of(byName);
    }

    private String attributeName() {
        var start = at;
        while (at < source.length()) {
            var c = source.charAt(at);
            if (Character.isWhitespace(c) || c == '=' || c == '>' || c == '/') {
                break;
            }
            at++;
        }
        return source.substring(start, at).toLowerCase(Locale.ROOT);
    }

    /// What follows an `=`, or `""` for an attribute that is only present.
    private String attributeValue() {
        skipWhitespace();
        if (at >= source.length() || source.charAt(at) != '=') {
            // `<input disabled>`: present, with no value. The empty string rather than
            // null, so that `has` and `value` answer different questions — see
            // `HtmlAttributes`.
            return "";
        }
        at++;
        skipWhitespace();
        if (at >= source.length()) {
            return "";
        }
        var quote = source.charAt(at);
        if (quote == '"' || quote == '\'') {
            at++;
            var end = source.indexOf(quote, at);
            if (end < 0) {
                // Unterminated quote: the rest of the file is the value. The
                // alternative — treating the quote as a character and carrying on — is
                // how one missing `"` turns a page into its own source code.
                var value = source.substring(at);
                at = source.length();
                return Entities.resolveAll(value);
            }
            var value = source.substring(at, end);
            at = end + 1;
            return Entities.resolveAll(value);
        }
        var start = at;
        while (at < source.length() && !Character.isWhitespace(source.charAt(at)) && source.charAt(at) != '>') {
            at++;
        }
        return Entities.resolveAll(source.substring(start, at));
    }

    /// A tag name, lower-cased.
    private String name() {
        var start = at;
        while (at < source.length()) {
            var c = source.charAt(at);
            if (Character.isWhitespace(c) || c == '>' || c == '/') {
                break;
            }
            at++;
        }
        return source.substring(start, at).toLowerCase(Locale.ROOT);
    }

    private void skipWhitespace() {
        while (at < source.length() && Character.isWhitespace(source.charAt(at))) {
            at++;
        }
    }

    private void skipTo(char c) {
        var end = source.indexOf(c, at);
        at = end < 0 ? source.length() : end + 1;
    }

    /// Text, entities resolved, unless it is empty.
    private static void characters(List<Token> tokens, String text) {
        if (!text.isEmpty()) {
            tokens.add(new Token.Characters(Entities.resolveAll(text)));
        }
    }

    /// Where `needle` next appears, whichever case it is written in.
    ///
    /// `regionMatches` rather than lower-casing the source, which would copy a whole
    /// page for every `</script>` in it.
    private int indexOfIgnoreCase(String needle, int from) {
        for (var i = from; i + needle.length() <= source.length(); i++) {
            if (source.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    /// Whether `c` can begin a tag name — a letter, and nothing else.
    ///
    /// `<3` and `<= 5` are text because of this one test, which is the difference
    /// between a changelog rendering and a changelog disappearing into an element
    /// nobody closed.
    private static boolean isNameStart(char c) {
        return Character.isLetter(c);
    }
}
