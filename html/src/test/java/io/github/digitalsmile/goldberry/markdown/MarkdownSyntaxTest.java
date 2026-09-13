package io.github.digitalsmile.goldberry.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The dialect, as a value.
///
/// No library needed: this is a set with four methods on it, and the test that the
/// flags actually reach md4c is in [MarkdownTest] where the documents are.
@DisplayName("a Markdown dialect")
class MarkdownSyntaxTest {

    @Test
    @DisplayName("is plain CommonMark when nothing is asked for")
    void commonMark() {
        assertEquals(Set.of(), MarkdownSyntax.commonMark().extensions());
    }

    @Test
    @DisplayName("is what people mean by Markdown when it says gitHub")
    void gitHub() {
        assertEquals(
                Set.of(
                        MarkdownExtension.TABLES,
                        MarkdownExtension.STRIKETHROUGH,
                        MarkdownExtension.TASK_LISTS,
                        MarkdownExtension.AUTOLINKS),
                MarkdownSyntax.gitHub().extensions(),
                "md4c calls this set MD_DIALECT_GITHUB");
    }

    @Test
    @DisplayName("adds and removes without touching the value it came from")
    void withers() {
        var base = MarkdownSyntax.of(MarkdownExtension.TABLES);
        var more = base.with(MarkdownExtension.WIKI_LINKS);
        var fewer = more.without(MarkdownExtension.TABLES);

        assertEquals(Set.of(MarkdownExtension.TABLES), base.extensions(), "the original is untouched");
        assertEquals(Set.of(MarkdownExtension.TABLES, MarkdownExtension.WIKI_LINKS), more.extensions());
        assertEquals(Set.of(MarkdownExtension.WIKI_LINKS), fewer.extensions());
        assertTrue(more.has(MarkdownExtension.TABLES));
        assertFalse(fewer.has(MarkdownExtension.TABLES));
    }

    @Test
    @DisplayName("cannot be changed through the set it was built from")
    void defensiveCopy() {
        var mutable = EnumSet.of(MarkdownExtension.TABLES);
        var syntax = new MarkdownSyntax(mutable);
        mutable.add(MarkdownExtension.NO_HTML);
        assertEquals(
                Set.of(MarkdownExtension.TABLES),
                syntax.extensions(),
                "a dialect a document has been parsed with must not change afterwards");
        assertThrows(
                UnsupportedOperationException.class, () -> syntax.extensions().clear());
    }

    @Test
    @DisplayName("refuses null")
    void nullIsRefused() {
        assertThrows(NullPointerException.class, () -> new MarkdownSyntax(null));
    }

    @Test
    @DisplayName("turns three md4c bits on for one autolink extension")
    void autolinksAreThreeBits() {
        // Reaching into the package-private translation on purpose: it is the one place
        // this module names md4c, and an extension silently mapping to nothing would
        // otherwise be invisible until somebody noticed a link was not a link.
        assertEquals(3, MarkdownExtension.AUTOLINKS.flags().size());
        assertEquals(2, MarkdownExtension.NO_HTML.flags().size());
        var flags = MarkdownSyntax.gitHub().flags();
        assertEquals(6, flags.size(), "four extensions, six bits");
    }
}
