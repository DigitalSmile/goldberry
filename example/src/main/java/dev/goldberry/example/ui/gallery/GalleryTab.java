package dev.goldberry.example.ui.gallery;

import java.util.function.Function;

import dev.goldberry.widget.Widget;

/// One tab of the gallery: its name, its label, and how its screen is built.
///
/// @param name   the screen's name: its `Tab` value, its accelerator's target and
///               what `-Dgoldberry.example.screen` picks
/// @param title  the strip's label, and the row in Edit ▸ Go to and the tray
/// @param fit    whether the gallery puts the screen in a viewport
/// @param screen builds the screen
public record GalleryTab(String name, String title, Fit fit, Function<GalleryContext, Widget> screen) {

    /// How a screen sits in its tab.
    public enum Fit {
        /// In a vertical viewport the gallery provides: a wall of cards, as tall
        /// as it needs to be.
        SCROLLED,
        /// Filling the tab, for a screen that owns a viewport of its own. The
        /// design system bans a scroller inside a scroller on the same axis.
        FILLS
    }
}
