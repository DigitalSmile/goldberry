package io.github.digitalsmile.goldberry.natives.layout;

import java.util.Objects;

/// One row of the layout table reported by the compiled library.
///
/// A row with no [#fieldName()] describes a struct; a row with one describes a
/// field of that struct. Rows whose struct name is [#SCALAR] carry the width of
/// a primitive C type rather than a struct member.
/// ## The size column of a constant row is a bit pattern
///
/// The table's three numeric columns are `uint32_t`, and for a struct or a scalar
/// they are sizes and offsets, which cannot be negative. A [#CONSTANT] row puts
/// the constant's *value* in the size column, and a flag with the top bit set —
/// `SDL_WINDOW_NOT_FOCUSABLE` is `0x80000000`, the first one Goldberry uses —
/// reads back as a negative Java `int`. That is not a corrupt table; it is an
/// unsigned value in a signed box, and [#value()] is where it is read as what it
/// is.
public record LayoutEntry(String structName, String fieldName, int size, int offset, int alignment) {

    /// Struct name used by rows describing primitive C types.
    public static final String SCALAR = "<scalar>";

    /// Struct name used by rows carrying a constant's value rather than a size.
    public static final String CONSTANT = "<constant>";

    public LayoutEntry {
        Objects.requireNonNull(structName, "structName");
        // A constant's size column is its value and may legitimately have the top
        // bit set; a struct's or a scalar's cannot, and a negative one there
        // still means the table is being read wrongly.
        if ((size < 0 && !CONSTANT.equals(structName)) || offset < 0 || alignment < 0) {
            throw new IllegalArgumentException(
                    "negative value in layout entry for " + structName + "." + fieldName);
        }
    }

    /// The value a [#describesConstant] row carries, read unsigned.
    ///
    /// @throws IllegalStateException if this row is not a constant, where the
    ///         same column means a size and reading it this way would hide a bug
    public long value() {
        if (!describesConstant()) {
            throw new IllegalStateException(
                    structName + "." + fieldName + " is not a constant row;"
                            + " its size column is a size, not a value");
        }
        return Integer.toUnsignedLong(size);
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
