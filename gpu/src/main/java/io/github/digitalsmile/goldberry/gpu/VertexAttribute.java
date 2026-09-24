package io.github.digitalsmile.goldberry.gpu;

import java.util.Objects;

/// One input of a vertex shader, and where in its buffer's element it is.
///
/// @param location the shader's input location: `TEXCOORD`*n* in HLSL compiled
///                 by DXC, `layout(location = n)` in GLSL
/// @param format   how it is stored
/// @param offset   its byte offset from the start of the element
public record VertexAttribute(int location, VertexFormat format, int offset) {

    /// Checks the numbers are not negative.
    public VertexAttribute {
        Objects.requireNonNull(format, "format");
        if (location < 0 || offset < 0) {
            throw new IllegalArgumentException("vertex attribute at location " + location + ", offset " + offset);
        }
    }

    /// The attribute at `location`, stored as `format` from `offset`.
    public static VertexAttribute of(int location, VertexFormat format, int offset) {
        return new VertexAttribute(location, format, offset);
    }

    /// The byte just past it.
    int end() {
        return offset + format.bytes();
    }
}
