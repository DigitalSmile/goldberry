package io.github.digitalsmile.goldberry.gpu;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/// A vertex buffer a pipeline reads: which slot it is bound to, how far apart
/// its elements are, and the attributes each element holds.
///
/// @param slot       the slot [RenderPass#bindVertexBuffer] binds it to
/// @param stride     the bytes from one element to the next
/// @param rate       whether an element is a vertex or an instance
/// @param attributes what each element holds; at least one
public record VertexBufferLayout(int slot, int stride, VertexInputRate rate, List<VertexAttribute> attributes) {

    /// Checks the attributes fit the stride and their locations differ, and
    /// copies them.
    ///
    /// @throws IllegalArgumentException when the slot is negative, the stride
    ///                                  not positive, there is no attribute, two
    ///                                  share a location, or one ends past the
    ///                                  stride
    public VertexBufferLayout {
        Objects.requireNonNull(rate, "rate");
        attributes = List.copyOf(attributes);
        if (slot < 0 || stride <= 0) {
            throw new IllegalArgumentException("vertex buffer in slot " + slot + " with stride " + stride);
        }
        if (attributes.isEmpty()) {
            throw new IllegalArgumentException("vertex buffer in slot " + slot + " has no attributes");
        }
        var locations = new HashSet<Integer>();
        for (var attribute : attributes) {
            if (!locations.add(attribute.location())) {
                throw new IllegalArgumentException("location " + attribute.location() + " declared twice");
            }
            if (attribute.end() > stride) {
                throw new IllegalArgumentException(
                        attribute + " ends at byte " + attribute.end() + ", past the " + stride + "-byte stride");
            }
        }
    }

    /// A buffer of one element per vertex.
    public static VertexBufferLayout perVertex(int slot, int stride, VertexAttribute... attributes) {
        return new VertexBufferLayout(slot, stride, VertexInputRate.VERTEX, List.of(attributes));
    }

    /// A buffer of one element per instance.
    public static VertexBufferLayout perInstance(int slot, int stride, VertexAttribute... attributes) {
        return new VertexBufferLayout(slot, stride, VertexInputRate.INSTANCE, List.of(attributes));
    }
}
