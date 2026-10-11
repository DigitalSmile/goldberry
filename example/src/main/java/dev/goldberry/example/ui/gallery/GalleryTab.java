package dev.goldberry.example.ui.gallery;

import java.util.Objects;
import java.util.function.Function;

import dev.goldberry.widget.Widget;

/// One screen of the gallery: its name, its label, where the sidebar lists it,
/// and how it is built.
///
/// @param name    the screen's name: its row's value, its accelerator's target
///                and what `-Dgoldberry.example.screen` picks
/// @param title   the sidebar's label, and the row in Edit ▸ Go to and the tray
/// @param section the sidebar group it is listed under
/// @param icon    the bundled icon beside its label
/// @param fit     whether the window puts the screen in a viewport
/// @param screen  builds the screen
public record GalleryTab(
        String name, String title, Section section, String icon, Fit fit, Function<GalleryContext, Widget> screen) {

    public GalleryTab {
        Objects.requireNonNull(section, "section");
        if (name.isBlank() || title.isBlank() || icon.isBlank()) {
            throw new IllegalArgumentException("a screen needs a name, a title and an icon");
        }
    }

    /// How a screen sits in the window.
    public enum Fit {
        /// In a vertical viewport the window provides: a wall of cards, as tall
        /// as it needs to be.
        SCROLLED,
        /// Filling the content area, for a screen that owns a viewport of its
        /// own. The design system bans a scroller inside a scroller on the same
        /// axis.
        FILLS
    }
}
