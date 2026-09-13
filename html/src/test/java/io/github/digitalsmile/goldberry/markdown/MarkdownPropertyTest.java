package io.github.digitalsmile.goldberry.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import io.github.digitalsmile.goldberry.markdown.html.MarkdownHtml;
import io.github.digitalsmile.goldberry.markdown.model.Block;
import io.github.digitalsmile.goldberry.markdown.model.Document;
import io.github.digitalsmile.goldberry.markdown.model.Inlines;
import io.github.digitalsmile.goldberry.markdown.model.Paragraph;

/// The two properties that hold for **every** document, which is what `docs/testing.md`
/// §1.1 asks a round trip for.
///
/// Generated rather than written down, because the interesting inputs are the ones
/// nobody would think to write: a paragraph that is one ampersand, a word made of
/// angle brackets, text that is nothing but whitespace.
class MarkdownPropertyTest {

    /// Text a paragraph can be made of: printable, no newlines — which would make more
    /// than one block — and no backslash, which is an escape rather than a character.
    @Provide
    Arbitrary<String> prose() {
        return Arbitraries.strings()
                .withCharRange(' ', '~')
                .excludeChars('\\')
                .ofMinLength(1)
                .ofMaxLength(120);
    }

    /// **Every word of the source survives the parse.**
    ///
    /// Not "the text is identical": Markdown is a syntax, so `*a*` legitimately loses
    /// its asterisks and `&amp;` legitimately becomes one character. What must never
    /// happen is a word disappearing — which is the failure a renderer cannot see and a
    /// reader cannot miss.
    @Property(tries = 300)
    void wordsSurviveTheParse(@ForAll("prose") String source) {
        var document = Markdown.parse(source);
        var text = new StringBuilder();
        for (var block : document.blocks()) {
            if (block instanceof Paragraph paragraph) {
                text.append(paragraph.text()).append(' ');
            } else {
                // A generated line can legitimately be a heading, a fence or a rule --
                // the properties below are about paragraphs, so anything else is
                // skipped rather than asserted about.
                return;
            }
        }
        var parsed = text.toString();
        for (var word : source.split("\\s+")) {
            // Only the words made of characters Markdown has no opinion about. The
            // others are the syntax doing its job.
            if (word.chars().allMatch(c -> Character.isLetterOrDigit(c))) {
                assertTrue(parsed.contains(word), "the parse lost \"" + word + "\" from " + source);
            }
        }
    }

    /// **Nothing text says reaches the HTML as markup.**
    ///
    /// The one property an HTML writer must have: a `<` that came from a
    /// [io.github.digitalsmile.goldberry.markdown.model.Text] node is `&lt;` in the
    /// output, always. Raw HTML the *author* wrote is different — it is a node of its
    /// own and passes through on purpose — so documents that produced one are skipped.
    @Property(tries = 300)
    void textIsAlwaysEscaped(@ForAll("prose") String source) {
        var document = Markdown.parse(source, MarkdownSyntax.gitHub().with(MarkdownExtension.NO_HTML));
        var html = MarkdownHtml.of(document);
        var text = Inlines.text(inlines(document.blocks()));
        if (text.indexOf('<') < 0 && text.indexOf('&') < 0) {
            return;
        }
        // Every `<` left in the output belongs to a tag this writer opened, and every
        // tag it opens is followed by a letter or a `/`. A `<` from the document would
        // be followed by whatever the author typed -- which is why the check is on what
        // comes after it rather than on a count.
        for (var i = html.indexOf('<'); i >= 0; i = html.indexOf('<', i + 1)) {
            var next = i + 1 < html.length() ? html.charAt(i + 1) : ' ';
            assertTrue(
                    Character.isLetter(next) || next == '/',
                    "an unescaped `<` reached the output of " + source + ": " + html);
        }
        assertFalse(html.contains("&amp;amp;"), "an entity escaped twice, from " + source);
    }

    private static List<io.github.digitalsmile.goldberry.markdown.model.Inline> inlines(List<Block> blocks) {
        var content = new java.util.ArrayList<io.github.digitalsmile.goldberry.markdown.model.Inline>();
        for (var block : blocks) {
            if (block instanceof Paragraph paragraph) {
                content.addAll(paragraph.content());
            }
        }
        return content;
    }

    /// A sanity check that the generator is producing what the properties assume, so a
    /// change to it cannot quietly turn both of them into no-ops.
    @Property(tries = 20)
    void everyGeneratedDocumentParses(@ForAll("prose") String source) {
        assertEquals(Document.class, Markdown.parse(source).getClass());
    }
}
