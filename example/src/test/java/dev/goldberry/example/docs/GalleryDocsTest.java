package dev.goldberry.example.docs;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.gallery.GalleryTab;
import dev.goldberry.example.ui.gallery.ScreenHeader;
import dev.goldberry.example.ui.gallery.Summaries;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// That the gallery shows the guide: every screen opens with a header linking its
/// chapter, every card has a title, a summary and a link into the guide, and
/// every section of the guide that needs a card has one.
///
/// Each screen is built the way the window builds it, through the application's
/// own wiring, and read as the element tree it mounts. What is checked is what a
/// reader can see: a card in a document and a card built in Java are the same tree
/// by the time they are on screen.
@DisplayName("the gallery")
class GalleryDocsTest {

    /// What one screen mounted: its headers and its cards.
    private record Mounted(boolean named, List<ScreenHeader> headers, List<Card> cards, List<String> citing) {}

    private static final Map<String, Mounted> SCREENS = new LinkedHashMap<>();

    private static ShowcaseScene scene;

    private static Fonts fonts;

    @BeforeAll
    static void mountEveryScreen() {
        RendererRequirement.enforce();
        scene = new ShowcaseScene();
        fonts = Fonts.bundled();
        for (var tab : Gallery.TABS) {
            SCREENS.put(tab.name(), mount(tab));
        }
    }

    @AfterAll
    static void close() {
        if (fonts != null) {
            fonts.close();
        }
        if (scene != null) {
            scene.close();
        }
    }

    private static Mounted mount(GalleryTab tab) {
        var root = scene.root(tab.name());
        try (var session = Offscreen.of(1280, 900)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(root)) {
            var headers = new ArrayList<ScreenHeader>();
            var cards = new ArrayList<Card>();
            var citing = new ArrayList<String>();
            walk(session.byId("root").orElseThrow()).forEach(element -> {
                switch (element.widget()) {
                    case ScreenHeader header -> headers.add(header);
                    case Card card when CardShape.isGalleryCard(card) -> cards.add(card);
                    case Text text when Summaries.citesARecord(text.content()) -> citing.add(text.content());
                    default -> {}
                }
            });
            return new Mounted(
                    session.byId("screen-" + tab.name()).isPresent(),
                    List.copyOf(headers),
                    List.copyOf(cards),
                    List.copyOf(citing));
        }
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(GalleryDocsTest::walk));
    }

    @TestFactory
    @DisplayName("screen by screen")
    Stream<DynamicTest> everyScreen() {
        return SCREENS.entrySet().stream()
                .map(screen -> DynamicTest.dynamicTest(screen.getKey(), () -> {
                    var mounted = screen.getValue();
                    var problems = mounted.cards().stream()
                            .flatMap(card -> CardShape.problems(card).stream())
                            .toList();
                    assertAll(
                            () -> assertEquals(1, mounted.headers().size(), "one header, which links the chapter"),
                            () -> assertTrue(
                                    mounted.headers().stream().allMatch(header -> BookSections.resolves(header.doc())),
                                    "the header's link lands in the guide: " + mounted.headers()),
                            () -> assertEquals(List.of(), problems, "every card has a head, a link and a summary"),
                            () -> assertEquals(
                                    List.of(), mounted.citing(), "no text on the screen cites a decision record"));
                }));
    }

    @Test
    @DisplayName("has no two cards with one id")
    void cardIdsAreDistinct() {
        var seen = new LinkedHashSet<String>();
        var twice = new ArrayList<String>();
        SCREENS.values().stream()
                .flatMap(mounted -> mounted.cards().stream())
                .map(card -> card.attributes().id())
                .forEach(id -> {
                    if (!seen.add(id)) {
                        twice.add(id);
                    }
                });
        assertEquals(List.of(), twice);
    }

    @Test
    @DisplayName("shows every section of the guide that needs a card")
    void everySectionHasACard() {
        var links = new LinkedHashSet<DocLink>();
        for (var mounted : SCREENS.values()) {
            mounted.headers().forEach(header -> links.add(header.doc()));
            mounted.cards().forEach(card -> CardShape.link(card).ifPresent(links::add));
        }
        var missing = BookSections.required().stream()
                .filter(section -> !BookSections.covers(links, section))
                .collect(Collectors.groupingBy(
                        DocLink::page,
                        TreeMap::new,
                        Collectors.mapping(
                                section -> section.anchor().isEmpty() ? "(a link to the chapter)" : section.anchor(),
                                Collectors.toList())));
        var count = missing.values().stream().mapToInt(List::size).sum();
        assertTrue(
                missing.isEmpty(),
                () -> count + " sections of the guide have no card:\n"
                        + missing.entrySet().stream()
                                .map(chapter -> "  " + chapter.getKey() + ": " + String.join(", ", chapter.getValue()))
                                .collect(Collectors.joining("\n")));
    }

    @Test
    @DisplayName("links nothing the guide does not have")
    void everyLinkResolves() {
        Set<DocLink> broken = SCREENS.values().stream()
                .flatMap(mounted -> Stream.concat(
                        mounted.headers().stream().map(ScreenHeader::doc),
                        mounted.cards().stream().flatMap(card -> CardShape.link(card).stream())))
                .filter(link -> !BookSections.resolves(link))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertFalse(SCREENS.isEmpty());
        assertEquals(Set.of(), broken);
    }
}
