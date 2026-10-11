package dev.goldberry.example.ui.search;

import java.util.List;
import java.util.Objects;

import dev.goldberry.example.ui.gallery.GalleryTab;

/// Something the search palette can find: a screen, a card on one, or a command.
///
/// Sealed, so the palette's rendering and what `Enter` does are exhaustive
/// switches over the three kinds, and a fourth kind is a compile error in every
/// place that has to learn about it.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public sealed interface SearchEntry {

    /// What the result's first line says.
    String title();

    /// What its second line says.
    String detail();

    /// The text matched besides the title, in the order it counts.
    List<String> context();

    /// What the kind is called, for the filter and the result's tag.
    Kind kind();

    /// A name for this entry that survives a rebuild of the list, for the
    /// recent picks.
    String key();

    /// The three kinds, in the order the filter offers them.
    enum Kind {
        SCREEN("Screens"),
        CARD("Cards"),
        COMMAND("Commands");

        private final String title;

        Kind(String title) {
            this.title = title;
        }

        /// The filter's label.
        public String title() {
            return title;
        }
    }

    /// A gallery screen.
    ///
    /// @param tab     the screen
    /// @param summary what it opens with, or empty
    record Screen(GalleryTab tab, String summary) implements SearchEntry {

        public Screen {
            Objects.requireNonNull(tab, "tab");
            Objects.requireNonNull(summary, "summary");
        }

        @Override
        public String title() {
            return tab.title();
        }

        @Override
        public String detail() {
            return summary.isEmpty() ? tab.section().title() : summary;
        }

        @Override
        public List<String> context() {
            return List.of(tab.section().title(), summary);
        }

        @Override
        public Kind kind() {
            return Kind.SCREEN;
        }

        @Override
        public String key() {
            return "screen:" + tab.name();
        }
    }

    /// A card on a gallery screen.
    ///
    /// @param card   the card, as the index holds it
    /// @param screen the screen it is on
    record Card(IndexedCard card, GalleryTab screen) implements SearchEntry {

        public Card {
            Objects.requireNonNull(card, "card");
            Objects.requireNonNull(screen, "screen");
        }

        @Override
        public String title() {
            return card.title();
        }

        @Override
        public String detail() {
            return card.summary();
        }

        @Override
        public List<String> context() {
            return List.of(screen.title(), card.summary());
        }

        @Override
        public Kind kind() {
            return Kind.CARD;
        }

        @Override
        public String key() {
            return "card:" + card.id();
        }
    }

    /// Something the window does: switch the light, start the tour.
    ///
    /// @param title    what it is called, as a menu row would call it
    /// @param shortcut its accelerator as a reader types it, or empty
    /// @param run      what choosing it does
    record Command(String title, String shortcut, Runnable run) implements SearchEntry {

        public Command {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(shortcut, "shortcut");
            Objects.requireNonNull(run, "run");
        }

        @Override
        public String detail() {
            return shortcut.isEmpty() ? "Command" : "Command · " + shortcut;
        }

        @Override
        public List<String> context() {
            return List.of();
        }

        @Override
        public Kind kind() {
            return Kind.COMMAND;
        }

        @Override
        public String key() {
            return "command:" + title;
        }
    }
}
