package dev.goldberry.example.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.ui.gallery.Documents;
import dev.goldberry.example.ui.gallery.Summaries;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.kdl.KdlValue;

/// The cards the gallery's documents write, held to the card's shape without a
/// renderer: a document is checked as the text it is, so this runs on a machine
/// with no native library as well.
@DisplayName("a card in a document")
class DocumentCardsTest {

    @Test
    @DisplayName("has a head with a title and a link into the guide, then a summary")
    void everyCardHasTheShape() throws IOException {
        var problems = new ArrayList<String>();
        var cards = 0;
        for (var document : documents()) {
            for (var card : cardsIn(KdlParser.parse(Files.readString(document)))) {
                cards++;
                CardShape.problems(card).forEach(problem -> problems.add(document.getFileName() + ": " + problem));
            }
        }
        assertFalse(cards == 0, "no wall-card found in any document; is the folder right?");
        assertEquals(List.of(), problems);
    }

    @Test
    @DisplayName("says nothing on screen that cites a decision record")
    void noTextCitesARecord() throws IOException {
        var citing = new ArrayList<String>();
        for (var document : documents()) {
            walk(KdlParser.parse(Files.readString(document)))
                    .forEach(node -> node.argument()
                            .map(KdlValue::asString)
                            .filter(Summaries::citesARecord)
                            .ifPresent(
                                    text -> citing.add(document.getFileName() + " " + node.position() + ": " + text)));
        }
        assertEquals(List.of(), citing);
    }

    /// Every document beside the screens.
    static List<Path> documents() throws IOException {
        try (Stream<Path> files = Files.list(folder())) {
            return files.filter(file -> file.toString().endsWith(".kdl"))
                    .sorted()
                    .toList();
        }
    }

    private static Path folder() {
        try {
            var url = Documents.class.getResource("/dev/goldberry/example/ui/statusbar.kdl");
            if (url == null) {
                throw new IllegalStateException("statusbar.kdl is not on the test's class path");
            }
            return Path.of(url.toURI()).getParent();
        } catch (URISyntaxException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static List<KdlNode> cardsIn(List<KdlNode> nodes) {
        return walk(nodes).filter(CardShape::isGalleryCard).toList();
    }

    private static Stream<KdlNode> walk(List<KdlNode> nodes) {
        return nodes.stream().flatMap(node -> Stream.concat(Stream.of(node), walk(node.children())));
    }
}
