package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// A whole parsed document — what [io.github.digitalsmile.goldberry.markdown.Markdown#parse]
/// returns.
///
/// @param blocks the top-level blocks, in order
public record Document(List<Block> blocks) implements Block {

    /// A document with nothing in it, which is what empty text parses to.
    public static final Document EMPTY = new Document(List.of());

    public Document {
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
    }

    @Override
    public List<Block> children() {
        return blocks;
    }
}
