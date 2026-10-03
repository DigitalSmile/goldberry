package dev.goldberry.example.ui.guide;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.example.docs.BookSections;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.application.ChapterFixture;

/// The Guide screen: one card per chapter it lists, in the book's parts, each
/// landing on a chapter the book has.
@DisplayName("the guide screen")
class GuideChapterTest {

    private static List<GuideParts.Chapter> chapters() {
        return GuideParts.PARTS.stream()
                .flatMap(part -> part.chapters().stream())
                .toList();
    }

    @Test
    @DisplayName("links only chapters the book's summary lists")
    void everyChapterResolves() {
        var broken = chapters().stream()
                .map(GuideParts.Chapter::page)
                .filter(page -> !BookSections.resolves(DocLink.page(page)))
                .toList();
        assertEquals(List.of(), broken);
    }

    @Test
    @DisplayName("is grouped as the book's parts are")
    void parts() {
        assertEquals(
                List.of("Overview", "Getting started", "Performance", "Developer guide", "Contributing", "Reference"),
                GuideParts.PARTS.stream().map(GuideParts.Part::title).toList());
    }

    @Test
    @DisplayName("shows a card for every chapter, each under its part")
    void cards() {
        RendererRequirement.enforce();
        try (var fixture = new ChapterFixture();
                var session = fixture.window("guide")) {
            var screen = session.byId("screen-guide").orElseThrow();
            assertAll(
                    () -> assertEquals(
                            chapters().stream().map(GuideParts.Chapter::id).collect(Collectors.toSet()),
                            Set.copyOf(ChapterFixture.cardIds(screen))),
                    () -> assertEquals(
                            GuideParts.PARTS.size(),
                            ChapterFixture.walk(screen)
                                    .filter(element -> element.classes().contains("guide-part"))
                                    .count()));
            for (var part : GuideParts.PARTS) {
                var id = "guide-" + part.title().toLowerCase(Locale.ROOT).replace(' ', '-');
                assertEquals(
                        part.chapters().stream().map(GuideParts.Chapter::id).collect(Collectors.toSet()),
                        Set.copyOf(ChapterFixture.cardIds(session.byId(id).orElseThrow())),
                        part.title());
            }
        }
    }
}
