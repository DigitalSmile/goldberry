package io.github.digitalsmile.goldberry.natives.layout;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedSet;

/// A hand-written struct layout, paired with the C type name it models.
///
/// The name is what ties the Java declaration to the row the compiled library
/// reports in its layout table, so [LayoutVerifier] can prove the two agree.
public record NativeStructLayout(String name, StructLayout layout) {

    public NativeStructLayout {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(layout, "layout");
        if (name.isBlank()) {
            throw new IllegalArgumentException("struct name must not be blank");
        }
    }

    public long byteSize() {
        return layout.byteSize();
    }

    public long byteAlignment() {
        return layout.byteAlignment();
    }

    /// The named members, in declaration order. Padding members are unnamed and
    /// therefore excluded — there is nothing on the C side to compare them to.
    public SequencedSet<String> fieldNames() {
        var names = new LinkedHashSet<String>();
        for (var member : layout.memberLayouts()) {
            member.name().ifPresent(names::add);
        }
        return names;
    }

    /// Byte offset of a named field.
    public long offsetOf(String field) {
        return layout.byteOffset(MemoryLayout.PathElement.groupElement(field));
    }

    /// Byte size of a named field.
    public long sizeOf(String field) {
        return layout.select(MemoryLayout.PathElement.groupElement(field)).byteSize();
    }
}
