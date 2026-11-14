package dev.goldberry.widgets.panel.list;

import java.util.Objects;
import java.util.function.Function;

import dev.goldberry.widget.Widget;

/// How a [ListView]'s items fall into sections, and what heads each one.
///
/// A section is a run of consecutive items with the same key: a day in a chat
/// timeline, a letter in a contact list. The model is not sorted or grouped
/// here, so an application whose items are not in section order gets one
/// section per run.
///
/// @param sectionOf an item's section key
/// @param header    the widget that heads the section with that key
public record ListSections<T>(Function<T, String> sectionOf, Function<String, Widget> header) {

    public ListSections {
        Objects.requireNonNull(sectionOf, "sectionOf");
        Objects.requireNonNull(header, "header");
    }
}
