package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// A `-`, `+` or `*` list.
///
/// @param tight whether the author left no blank lines between the items, which
///        CommonMark says means the items hold no paragraphs — a renderer draws a
///        tight list closer together, and this is the only place that intent
///        survives
/// @param items the items
public record BulletList(boolean tight, List<Item> items) implements Block {

    public BulletList {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    @Override
    public List<Item> children() {
        return items;
    }
}
