package dev.goldberry.content.marked;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkKind.Behaviour;
import dev.goldberry.text.mark.MarkKind.Custom;
import dev.goldberry.text.mark.MarkKind.Mention;

/// The addresses reference kinds are written as.
class MarkLinksTest {

    @Test
    @DisplayName("each reference kind goes out and comes back")
    void roundTrip() {
        MarkKind[] kinds = {
            new MarkKind.Link(URI.create("https://goldberry.dev/a?b=c")),
            new Mention("user:42 & co"),
            new Custom("hashtag", "rel ease", Behaviour.REFERENCE),
            new Custom("emoji", "1F44B", Behaviour.ATOM),
            new Custom("cmd", "", Behaviour.STYLE),
        };
        for (var kind : kinds) {
            var href = MarkLinks.href(kind).orElseThrow();
            assertEquals(Optional.of(kind), MarkLinks.kind(href), href);
        }
    }

    @Test
    @DisplayName("as written: readable addresses")
    void written() {
        assertEquals("mention:user%3A42", MarkLinks.href(new Mention("user:42")).orElseThrow());
        assertEquals(
                "mark:emoji/1F44B?atomic&exclusive",
                MarkLinks.href(new Custom("emoji", "1F44B", Behaviour.ATOM)).orElseThrow());
    }

    @Test
    @DisplayName("a style has none, and an address that is not one reads as nothing")
    void none() {
        assertTrue(MarkLinks.href(MarkKind.Style.BOLD).isEmpty());
        assertTrue(MarkLinks.kind("mention:").isEmpty());
        assertTrue(MarkLinks.kind("mark:nameonly").isEmpty());
        assertTrue(MarkLinks.kind("http://a b").isEmpty());
        assertTrue(MarkLinks.kind("mention:%zz").isEmpty());
        assertTrue(MarkLinks.kind(" ").isEmpty());
    }
}
