package io.github.digitalsmile.goldberry.natives.layout;

import java.util.Objects;

/// One row of the layout table reported by the compiled library.
///
/// A row with no [#fieldName()] describes a struct; a row with one describes a
/// field of that struct. Rows whose struct name is [#SCALAR] carry the width of
/// a primitive C type rather than a struct member.
public record LayoutEntry(String structName, String fieldName, int size, int offset, int alignment) {

    /// Struct name used by rows describing primitive C types.
    public static final String SCALAR = "<scalar>";

    /// Struct name used by rows carrying a constant's value rather than a size.
    public static final String CONSTANT = "<constant>";

    public LayoutEntry {
        Objects.requireNonNull(structName, "structName");
        if (size < 0 || offset < 0 || alignment < 0) {
            throw new IllegalArgumentException(
                    "negative value in layout entry for " + structName + "." + fieldName);
        }
    }

    /// Whether this row describes the struct itself rather than one of its fields.
    public boolean describesStruct() {
        return fieldName == null;
    }

    /// Whether this row describes a primitive C type.
    public boolean describesScalar() {
        return SCALAR.equals(structName);
    }

    /// Whether this row carries a constant's value in [#size()].
    public boolean describesConstant() {
        return CONSTANT.equals(structName);
    }
}
