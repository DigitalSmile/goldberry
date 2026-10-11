package dev.goldberry.example.ui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.example.docs.CardShape;
import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.gallery.GalleryTab;
import dev.goldberry.example.ui.gallery.ScreenHeader;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// That the search index shipped in the jar is every card the gallery builds.
///
/// Every screen is built the way the window builds it and read as the element
/// tree it mounts, exactly as `GalleryDocsTest` reads it: a card written in a
/// document and one built in Java are the same tree by then. The index is that
/// list, and the file on disk has to equal it.
///
/// `./gradlew :example:test --tests '*SearchIndexTest*' -Dgoldberry.golden.update=true`
/// rewrites the file, the way it retakes a golden image.
@DisplayName("the search index")
class SearchIndexTest {

    private static SearchIndex built;

    @BeforeAll
    static void buildEveryScreen() {
        RendererRequirement.enforce();
        var entries = new ArrayList<IndexedCard>();
        try (var scene = new ShowcaseScene();
                var fonts = Fonts.bundled()) {
            for (var tab : Gallery.TABS) {
                entries.addAll(read(scene, fonts, tab));
            }
        }
        built = new SearchIndex(entries);
        if (Boolean.getBoolean(GoldenImage.UPDATE_PROPERTY)) {
            try {
                Files.writeString(Path.of(SearchIndex.SOURCE), built.write(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("could not rewrite " + SearchIndex.SOURCE, e);
            }
        }
    }

    @AfterAll
    static void forget() {
        built = null;
    }

    /// The header and the cards of one screen, in the order they were built.
    private static List<IndexedCard> read(ShowcaseScene scene, Fonts fonts, GalleryTab tab) {
        var root = scene.root(tab.name());
        try (var session = Offscreen.of(1280, 900)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(root)) {
            var found = new ArrayList<IndexedCard>();
            walk(session.byId("root").orElseThrow()).forEach(element -> {
                switch (element.widget()) {
                    case ScreenHeader header ->
                        found.add(new IndexedCard(tab.name(), "", header.title(), header.summary()));
                    case Card card
                    when CardShape.isGalleryCard(card) ->
                        found.add(new IndexedCard(
                                tab.name(), String.valueOf(card.attributes().id()), title(card), summary(card)));
                    default -> {}
                }
            });
            return found;
        }
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(SearchIndexTest::walk));
    }

    private static String title(Card card) {
        if (card.children().getFirst() instanceof Row head) {
            return head.children().stream()
                    .filter(child -> child instanceof Text text
                            && text.attributes().classes().contains("card-title"))
                    .map(child -> ((Text) child).content())
                    .findFirst()
                    .orElse("");
        }
        return "";
    }

    private static String summary(Card card) {
        return card.children().size() > 1 && card.children().get(1) instanceof Text text ? text.content() : "";
    }

    @Test
    @DisplayName("is the list the screens build, and the file holds it")
    void theFileIsTheGallery() throws IOException {
        // The source file rather than the resource on the class path: a run that
        // rewrote the file is then checked against what it wrote.
        var onDisk = Files.readString(Path.of(SearchIndex.SOURCE), StandardCharsets.UTF_8);
        assertEquals(
                built.write(),
                SearchIndex.parse(onDisk).write(),
                "the search index is stale: rerun with -Dgoldberry.golden.update=true to rewrite "
                        + SearchIndex.SOURCE);
    }

    @Test
    @DisplayName("names every screen once, by its header")
    void everyScreenHasItsHeader() {
        var headers = built.entries().stream()
                .filter(IndexedCard::isHeader)
                .map(IndexedCard::screen)
                .toList();
        assertEquals(Gallery.names(), headers);
    }

    @Test
    @DisplayName("gives every card an id the window can scroll to, and no two the same")
    void everyCardCanBeFound() {
        var seen = new LinkedHashSet<String>();
        var twice = new ArrayList<String>();
        for (var card : built.cards()) {
            assertFalse(card.id().isBlank() || "null".equals(card.id()), () -> card + " has no id to scroll to");
            assertFalse(card.title().isBlank(), () -> card + " has no title to find it by");
            if (!seen.add(card.id())) {
                twice.add(card.id());
            }
        }
        assertEquals(List.of(), twice);
        assertTrue(built.cards().size() > 100, () -> "only " + built.cards().size() + " cards were found");
    }
}
