package io.github.digitalsmile.goldberry.natives.md4c;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.natives.md4c.enums.BlockType;
import io.github.digitalsmile.goldberry.natives.md4c.enums.CellAlign;
import io.github.digitalsmile.goldberry.natives.md4c.enums.SpanType;
import io.github.digitalsmile.goldberry.natives.md4c.enums.TextType;

/// The reader for the buffer `goldberry_md_parse` fills in.
///
/// This class and the encoder at the bottom of `goldberry_shim.c` are two halves of
/// one table, and the C side carries the same table in its own comment. A record
/// is a 20-byte header and then `textLength` bytes:
///
/// | at | width | field         | meaning                                          |
/// |----|-------|---------------|--------------------------------------------------|
/// | 0  | u8    | `event`       | 1 enter block, 2 leave block, 3 enter span, 4 leave span, 5 text, 6 attribute |
/// | 1  | u8    | `type`        | the block, span or text type                     |
/// | 2  | u8    | `role`        | attribute records only: 1 primary, 2 secondary   |
/// | 3  | u8    | `flags`       | bit 0 tight, bit 1 task, bit 2 autolink          |
/// | 4  | u32   | `a`           | level / start / mark / align / column count      |
/// | 8  | u32   | `b`           | mark delimiter / task mark offset / head rows    |
/// | 12 | u32   | `c`           | body rows / fence character                      |
/// | 16 | u32   | `textLength`  | payload bytes following the header               |
///
/// **Little-endian, explicitly.** The parse and this read happen in one process, so
/// the host's own order would do — but a byte order that is written down is one
/// fewer thing for a big-endian port to find out the hard way, and the layouts below
/// say which it is.
///
/// **Unaligned layouts, deliberately.** A record is 20 bytes plus however many bytes
/// of text, so the next one starts wherever that lands. Reading an `int` at an
/// address that is not a multiple of four is exactly what `JAVA_INT_UNALIGNED` is
/// for; the aligned layout would throw on the first document with an odd-length word
/// in it.
///
/// **Attributes arrive before the record they belong to.** An href is a string in
/// parts (see [MarkdownAttribute]), so it cannot travel in the fixed header; the
/// encoder emits one record per part and this collects them until the enter record
/// consumes them. A stream that ends with parts left over is a malformed stream and
/// is refused rather than silently dropped.
final class MarkdownStream {

    private static final ValueLayout.OfInt U32 = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

    private static final int HEADER_SIZE = 20;

    private static final int ENTER_BLOCK = 1;
    private static final int LEAVE_BLOCK = 2;
    private static final int ENTER_SPAN = 3;
    private static final int LEAVE_SPAN = 4;
    private static final int TEXT = 5;
    private static final int ATTRIBUTE = 6;

    private static final int ROLE_PRIMARY = 1;
    private static final int ROLE_SECONDARY = 2;

    private static final int FLAG_TIGHT = 0x01;
    private static final int FLAG_TASK = 0x02;
    private static final int FLAG_AUTOLINK = 0x04;

    private MarkdownStream() {}

    /// Decodes `bytes` — the whole buffer, resized to its real length — into events.
    ///
    /// @throws IllegalStateException if the buffer is not a well-formed stream, which
    ///         means this reader and the encoder disagree rather than that the
    ///         document was strange
    static List<MarkdownEvent> decode(MemorySegment bytes) {
        var events = new ArrayList<MarkdownEvent>();
        var primary = new ArrayList<MarkdownAttribute.Part>();
        var secondary = new ArrayList<MarkdownAttribute.Part>();
        var length = bytes.byteSize();
        var at = 0L;

        while (at < length) {
            if (length - at < HEADER_SIZE) {
                throw new IllegalStateException(
                        "markdown event stream ends inside a record header, " + (length - at) + " bytes short");
            }
            var event = Byte.toUnsignedInt(bytes.get(ValueLayout.JAVA_BYTE, at));
            var type = Byte.toUnsignedInt(bytes.get(ValueLayout.JAVA_BYTE, at + 1));
            var role = Byte.toUnsignedInt(bytes.get(ValueLayout.JAVA_BYTE, at + 2));
            var flags = Byte.toUnsignedInt(bytes.get(ValueLayout.JAVA_BYTE, at + 3));
            var a = bytes.get(U32, at + 4);
            var b = bytes.get(U32, at + 8);
            var c = bytes.get(U32, at + 12);
            var textLength = bytes.get(U32, at + 16);
            if (textLength < 0 || length - at - HEADER_SIZE < textLength) {
                throw new IllegalStateException("markdown event claims " + Integer.toUnsignedString(textLength)
                        + " bytes of text, " + (length - at - HEADER_SIZE) + " remain");
            }
            var text = textLength == 0 ? "" : text(bytes, at + HEADER_SIZE, textLength);
            at += HEADER_SIZE + textLength;

            switch (event) {
                case ATTRIBUTE -> {
                    var part = new MarkdownAttribute.Part(TextType.of(type), text);
                    (role == ROLE_SECONDARY ? secondary : primary).add(part);
                    if (role != ROLE_PRIMARY && role != ROLE_SECONDARY) {
                        throw new IllegalStateException("markdown attribute part has role " + role);
                    }
                }
                case ENTER_BLOCK -> {
                    var block = BlockType.of(type);
                    events.add(new MarkdownEvent.EnterBlock(
                            block, blockDetail(block, flags, a, b, c, take(primary), take(secondary))));
                }
                case LEAVE_BLOCK -> events.add(new MarkdownEvent.LeaveBlock(BlockType.of(type)));
                case ENTER_SPAN -> {
                    var span = SpanType.of(type);
                    events.add(
                            new MarkdownEvent.EnterSpan(span, spanDetail(span, flags, take(primary), take(secondary))));
                }
                case LEAVE_SPAN -> events.add(new MarkdownEvent.LeaveSpan(SpanType.of(type)));
                case TEXT -> events.add(new MarkdownEvent.Text(TextType.of(type), text));
                default -> throw new IllegalStateException("markdown event stream holds event kind " + event);
            }
        }

        if (!primary.isEmpty() || !secondary.isEmpty()) {
            throw new IllegalStateException("markdown event stream ends with " + (primary.size() + secondary.size())
                    + " attribute parts that no block or span claimed");
        }
        return List.copyOf(events);
    }

    /// The parts collected so far, as an attribute, leaving the list empty for the
    /// next one.
    private static MarkdownAttribute take(List<MarkdownAttribute.Part> parts) {
        if (parts.isEmpty()) {
            return MarkdownAttribute.NONE;
        }
        var attribute = new MarkdownAttribute(List.copyOf(parts));
        parts.clear();
        return attribute;
    }

    private static String text(MemorySegment bytes, long at, int length) {
        var copy = new byte[length];
        MemorySegment.copy(bytes, ValueLayout.JAVA_BYTE, at, copy, 0, length);
        return new String(copy, StandardCharsets.UTF_8);
    }

    /// The three numeric columns, read as whatever this block's detail struct put
    /// there. The mapping is the encoder's, and this is the other half of it.
    private static BlockDetail blockDetail(
            BlockType type, int flags, int a, int b, int c, MarkdownAttribute primary, MarkdownAttribute secondary) {
        return switch (type) {
            case H -> new BlockDetail.Heading(a);
            case UL -> new BlockDetail.BulletList((flags & FLAG_TIGHT) != 0, (char) a);
            case OL -> new BlockDetail.NumberedList(a, (flags & FLAG_TIGHT) != 0, (char) b);
            case LI -> new BlockDetail.Item((flags & FLAG_TASK) != 0, (char) a, b);
            case CODE -> new BlockDetail.Code(primary, secondary, (char) c);
            case TABLE -> new BlockDetail.Table(a, b, c);
            case TH, TD -> new BlockDetail.Cell(CellAlign.of(a));
            case DOC, QUOTE, HR, HTML, P, THEAD, TBODY, TR -> BlockDetail.NONE;
        };
    }

    private static SpanDetail spanDetail(
            SpanType type, int flags, MarkdownAttribute primary, MarkdownAttribute secondary) {
        return switch (type) {
            case A -> new SpanDetail.Link(primary, secondary, (flags & FLAG_AUTOLINK) != 0);
            case IMG -> new SpanDetail.Image(primary, secondary);
            case WIKILINK -> new SpanDetail.WikiLink(primary);
            case EM, STRONG, CODE, DEL, LATEXMATH, LATEXMATH_DISPLAY, U -> SpanDetail.NONE;
        };
    }
}
