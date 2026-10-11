package dev.goldberry.example.ui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseStyles;
import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.search.FuzzyMatcher.Span;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Mod;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.text.Run;

@DisplayName("the search palette")
class CommandPaletteTest {

    private static final SearchEntry FORMS_CARD = new SearchEntry.Card(
            new IndexedCard("forms", "field-card", "Field", "A label and a control."), Gallery.tab("forms"));

    private static final SearchEntry.Command LIGHT = new SearchEntry.Command("Switch the light", "Ctrl+T", () -> {});

    private static List<SearchEntry> entries() {
        var entries = new ArrayList<SearchEntry>();
        Gallery.TABS.forEach(tab -> entries.add(new SearchEntry.Screen(tab, "")));
        entries.add(FORMS_CARD);
        entries.add(LIGHT);
        return entries;
    }

    @Test
    @DisplayName("marks the matched letters of a title as runs of their own")
    void marked() {
        var runs = CommandPalette.marked("Switch the light", List.of(new Span(0, 2), new Span(11, 16)));

        assertEquals(
                List.of(new Run("Sw", Set.of("mark")), new Run("itch the "), new Run("light", Set.of("mark"))), runs);
        assertEquals(List.of(new Run("Plain")), CommandPalette.marked("Plain", List.of()));
    }

    @Test
    @DisplayName("with nothing typed, lists the recent picks first and then every screen")
    void idle() {
        var listed = CommandPalette.idle(entries(), List.of(LIGHT.key(), "card:gone"), null);

        assertEquals(LIGHT, listed.getFirst().entry());
        assertEquals(1 + Gallery.TABS.size(), listed.size(), "a recent pick that no longer exists is skipped");
        var commands = CommandPalette.idle(entries(), List.of(), SearchEntry.Kind.COMMAND);
        assertEquals(
                List.of(LIGHT),
                commands.stream().map(SearchRanking.Ranked::entry).toList());
    }

    @Test
    @DisplayName("tags a result with where it is, and draws it with its screen's icon")
    void tags() {
        assertEquals("Forms", CommandPalette.tag(FORMS_CARD));
        assertEquals("Ctrl+T", CommandPalette.tag(LIGHT));
        assertEquals(Gallery.tab("forms").icon(), CommandPalette.icon(FORMS_CARD));
        assertEquals("zap", CommandPalette.icon(LIGHT));
    }

    /// The palette in a window, driven by its keys.
    @Nested
    @DisplayName("with the keyboard")
    class Keys {

        private final List<SearchEntry> picked = new ArrayList<>();
        private Fonts fonts;
        private Session session;

        @BeforeEach
        void open() {
            RendererRequirement.enforce();
            fonts = Fonts.bundled();
            var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
            sheets.addAll(ShowcaseStyles.sheets());
            Consumer<SearchEntry> pick = picked::add;
            session = Offscreen.of(800, 700)
                    .stylesheets(sheets)
                    .fonts(fonts)
                    .session(new CommandPalette(entries(), List.of(), pick));
            assertTrue(session.focus(CommandPalette.INPUT), "the field takes the keyboard");
        }

        @AfterEach
        void close() {
            if (session != null) {
                session.close();
            }
            if (fonts != null) {
                fonts.close();
            }
        }

        private boolean isActive(int index) {
            return session.byId(CommandPalette.resultId(index))
                    .map(element -> element.widget() instanceof Pressable row
                            && row.attributes().classes().contains("active"))
                    .orElse(false);
        }

        @Test
        @DisplayName("Enter opens the best match for what was typed")
        void enterOpens() {
            session.type("field");
            session.key(Key.ENTER);

            assertEquals(List.of(FORMS_CARD), picked);
        }

        @Test
        @DisplayName("the arrows move the mark, and Enter opens the marked one")
        void arrows() {
            assertTrue(isActive(0));

            session.key(Key.DOWN);
            session.key(Key.DOWN);
            session.key(Key.UP);

            assertTrue(isActive(1));
            session.key(Key.ENTER);
            assertEquals(
                    Gallery.TABS.get(1).name(),
                    ((SearchEntry.Screen) picked.getFirst()).tab().name());
        }

        @Test
        @DisplayName("Tab steps through the kinds, and Shift+Tab back")
        void tabFilters() {
            session.type("light");
            session.key(Key.TAB);
            session.key(Key.TAB);
            session.key(Key.TAB);
            // All → Screens → Cards → Commands
            session.key(Key.ENTER);
            assertEquals(List.of(LIGHT), picked);

            session.key(Key.TAB, Modifiers.of(Mod.SHIFT));
            assertTrue(
                    session.byId(CommandPalette.resultId(0)).isEmpty(),
                    "no card is called light, so the Cards filter lists nothing");
        }

        @Test
        @DisplayName("a query that matches nothing says so")
        void nothing() {
            session.type("zzqx");
            session.key(Key.ENTER);

            assertEquals(List.of(), picked);
            assertTrue(session.byId(CommandPalette.resultId(0)).isEmpty());
        }
    }
}
