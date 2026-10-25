package dev.goldberry.media.bitstream.av1;

import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.bitstream.BitReader;

/// The reader every AV1 header is read through: each syntax element by the
/// name FFmpeg's `cbs_av1` gives it, so that a test can hold every element to
/// FFmpeg's `trace_headers` of the same bytes, at the same bit.
///
/// Without a trace it is the [BitReader] and nothing more: a subscripted name
/// is put together only when there is a trace to hand it to.
final class Syntax {

    /// Where each element read goes, for a test.
    interface Trace {

        /// A unit begins, under FFmpeg's heading for it: `Frame Header`.
        void heading(String heading);

        /// One element: the bit it starts at, counted from the first bit of
        /// its OBU; its name, with subscripts as FFmpeg writes them
        /// (`ref_frame_idx[3]`); and its value.
        void element(long bit, String name, long value);
    }

    private final BitReader in;
    private final @Nullable Trace trace;

    Syntax(BitReader in, @Nullable Trace trace) {
        this.in = in;
        this.trace = trace;
    }

    /// The bits read so far, from the first bit of the OBU.
    long position() {
        return in.position();
    }

    /// Passes over `count` bits that are not syntax elements: an OBU's header
    /// when its payload is read alone.
    void skip(long count) {
        in.skip(count);
    }

    void heading(String heading) {
        if (trace != null) {
            trace.heading(heading);
        }
    }

    /// `f(n)`, `n` up to 31 bits.
    int f(int n, String name) {
        var at = in.position();
        var value = in.bits(n);
        trace(at, name, value);
        return value;
    }

    /// `f(n)` of `name[i]`.
    int f(int n, String name, int i) {
        var at = in.position();
        var value = in.bits(n);
        if (trace != null) {
            trace.element(at, name + "[" + i + "]", value);
        }
        return value;
    }

    /// `f(n)` of `name[i][j]`.
    int f(int n, String name, int i, int j) {
        var at = in.position();
        var value = in.bits(n);
        if (trace != null) {
            trace.element(at, name + "[" + i + "][" + j + "]", value);
        }
        return value;
    }

    /// `f(n)` up to 32 bits, unsigned.
    long fLong(int n, String name) {
        var at = in.position();
        var value = in.bitsLong(n);
        trace(at, name, value);
        return value;
    }

    /// `f(n)` of `name[i]` up to 32 bits, unsigned.
    long fLong(int n, String name, int i) {
        var at = in.position();
        var value = in.bitsLong(n);
        if (trace != null) {
            trace.element(at, name + "[" + i + "]", value);
        }
        return value;
    }

    boolean flag(String name) {
        return f(1, name) == 1;
    }

    boolean flag(String name, int i) {
        return f(1, name, i) == 1;
    }

    boolean flag(String name, int i, int j) {
        return f(1, name, i, j) == 1;
    }

    /// `su(n)`.
    int su(int n, String name) {
        var at = in.position();
        var value = in.signed(n);
        trace(at, name, value);
        return value;
    }

    /// `su(n)` of `name[i]`.
    int su(int n, String name, int i) {
        var at = in.position();
        var value = in.signed(n);
        if (trace != null) {
            trace.element(at, name + "[" + i + "]", value);
        }
        return value;
    }

    /// `su(n)` of `name[i][j]`.
    int su(int n, String name, int i, int j) {
        var at = in.position();
        var value = in.signed(n);
        if (trace != null) {
            trace.element(at, name + "[" + i + "][" + j + "]", value);
        }
        return value;
    }

    /// `ns(n)` of `name[i]`: a value below `n`.
    int ns(int n, String name, int i) {
        var at = in.position();
        var value = in.nonSymmetric(n);
        if (trace != null) {
            trace.element(at, name + "[" + i + "]", value);
        }
        return value;
    }

    /// A value from `min` that each set bit raises by one, up to `max`, which
    /// needs no stop bit: how AV1 codes `TileColsLog2`, `lr_unit_shift` and
    /// `TxMode`. One element, as FFmpeg traces it, even when it takes no bits.
    int increment(int min, int max, String name) {
        var at = in.position();
        var value = min;
        while (value < max && in.flag()) {
            value++;
        }
        trace(at, name, value);
        return value;
    }

    /// `leb128()`.
    long leb128(String name) {
        var at = in.position();
        var value = in.leb128();
        trace(at, name, value);
        return value;
    }

    /// `uvlc()`.
    long uvlc(String name) {
        var at = in.position();
        var value = in.uvlc();
        trace(at, name, value);
        return value;
    }

    /// `decode_subexp(numSyms)` (§5.9.28), as FFmpeg reads and traces it: the
    /// run of `subexp_more_bits`, then `subexp_bits` or `subexp_final_bits`,
    /// then the value under `name[i][j]` at the bit it began.
    int subexp(int numSyms, String name, int i, int j) {
        var at = in.position();
        var maxLength = 31 - Integer.numberOfLeadingZeros(numSyms - 1) - 3;
        var length = increment(0, maxLength, "subexp_more_bits");
        var rangeBits = length == 0 ? 3 : 2 + length;
        var rangeOffset = length == 0 ? 0 : 1 << rangeBits;
        int value;
        if (length < maxLength) {
            value = f(rangeBits, "subexp_bits");
        } else {
            var bit = in.position();
            value = in.nonSymmetric(numSyms - rangeOffset);
            trace(bit, "subexp_final_bits", value);
        }
        value += rangeOffset;
        if (trace != null) {
            trace.element(at, name + "[" + i + "][" + j + "]", value);
        }
        return value;
    }

    /// `byte_alignment()`: zero bits to the next byte.
    void byteAlignment() {
        while (!in.byteAligned()) {
            if (f(1, "zero_bit") != 0) {
                throw new IllegalArgumentException("a set bit in byte_alignment() at bit " + (in.position() - 1));
            }
        }
    }

    private void trace(long at, String name, long value) {
        if (trace != null) {
            trace.element(at, name, value);
        }
    }

    /// `values`, as an unmodifiable list.
    static List<Integer> list(int[] values) {
        return Arrays.stream(values).boxed().toList();
    }
}
