package dev.goldberry.example.ui.lookalike.view;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.example.ui.lookalike.library.Cover;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// A cover: a square in the cover's gradient, with an icon on it for the covers
/// that are a kind of list rather than a record — the heart on Liked Songs.
///
/// @param cover which gradient
/// @param icon  a bundled icon drawn in the middle, or null for none
/// @param size  how big, which the stylesheet decides
record CoverArt(Cover cover, @Nullable String icon, Size size) implements Widget.Stateless {

    /// The sizes a cover is drawn at.
    enum Size {
        /// A sidebar row's and the player bar's.
        SMALL("cover-sm"),
        /// The shelf's.
        LARGE("cover-lg"),
        /// The playlist header's and the now-playing panel's.
        HUGE("cover-xl");

        private final String cssClass;

        Size(String cssClass) {
            this.cssClass = cssClass;
        }

        String cssClass() {
            return cssClass;
        }
    }

    /// The cover for `cover`, with a heart on it when it is the liked list's.
    static CoverArt of(Cover cover, Size size) {
        return new CoverArt(cover, cover == Cover.LIKED ? "heart" : null, size);
    }

    @Override
    public Widget build(BuildContext context) {
        List<Widget> inside = icon == null ? List.of() : List.of(Parts.icon(icon, "cover-icon"));
        return Parts.column(inside, "cover", cover.cssClass(), size.cssClass());
    }
}
