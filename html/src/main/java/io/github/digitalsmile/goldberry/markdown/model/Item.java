package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// One item of either list.
///
/// @param task whether the item is a check box — `- [x] done` — which is GitHub's
///        task-list extension and off in plain CommonMark
/// @param done whether that box is ticked. Always false when [#task()] is false
/// @param blocks the item's content, which is blocks because an item can hold a
///        paragraph, a nested list, or a code fence
public record Item(boolean task, boolean done, List<Block> blocks) implements Block {

    public Item {
        if (done && !task) {
            throw new IllegalArgumentException("an item that is not a task cannot be done");
        }
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
    }

    /// An ordinary item.
    public Item(List<Block> blocks) {
        this(false, false, blocks);
    }

    @Override
    public List<Block> children() {
        return blocks;
    }
}
