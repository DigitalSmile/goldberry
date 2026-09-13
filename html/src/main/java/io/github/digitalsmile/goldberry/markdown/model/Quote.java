package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// `> quoted`, which holds blocks rather than words — a quote can contain a list,
/// and nesting one inside another is how Markdown quotes a quote.
///
/// @param blocks what is being quoted
public record Quote(List<Block> blocks) implements Block {

    public Quote {
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
    }

    @Override
    public List<Block> children() {
        return blocks;
    }
}
