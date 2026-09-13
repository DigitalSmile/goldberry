package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// A `1.` list.
///
/// @param start the first number. Not always 1 — `7.` starts at seven, and a
///        renderer that assumed otherwise would renumber somebody's document
/// @param tight see [BulletList#tight()]
/// @param items the items
public record NumberedList(int start, boolean tight, List<Item> items) implements Block {

    public NumberedList {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    @Override
    public List<Item> children() {
        return items;
    }

    /// The number of the item at `index`.
    public int numberOf(int index) {
        if (index < 0 || index >= items.size()) {
            throw new IndexOutOfBoundsException("no item " + index + " in a list of " + items.size());
        }
        return start + index;
    }
}
