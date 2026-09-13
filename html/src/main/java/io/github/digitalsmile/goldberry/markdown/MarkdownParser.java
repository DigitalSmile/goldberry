package io.github.digitalsmile.goldberry.markdown;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.content.entity.Entities;
import io.github.digitalsmile.goldberry.markdown.model.Block;
import io.github.digitalsmile.goldberry.markdown.model.BulletList;
import io.github.digitalsmile.goldberry.markdown.model.CellAlignment;
import io.github.digitalsmile.goldberry.markdown.model.Code;
import io.github.digitalsmile.goldberry.markdown.model.CodeBlock;
import io.github.digitalsmile.goldberry.markdown.model.Document;
import io.github.digitalsmile.goldberry.markdown.model.Emphasis;
import io.github.digitalsmile.goldberry.markdown.model.Heading;
import io.github.digitalsmile.goldberry.markdown.model.HtmlBlock;
import io.github.digitalsmile.goldberry.markdown.model.Image;
import io.github.digitalsmile.goldberry.markdown.model.Inline;
import io.github.digitalsmile.goldberry.markdown.model.Inlines;
import io.github.digitalsmile.goldberry.markdown.model.Item;
import io.github.digitalsmile.goldberry.markdown.model.LineBreak;
import io.github.digitalsmile.goldberry.markdown.model.Link;
import io.github.digitalsmile.goldberry.markdown.model.NumberedList;
import io.github.digitalsmile.goldberry.markdown.model.Paragraph;
import io.github.digitalsmile.goldberry.markdown.model.Quote;
import io.github.digitalsmile.goldberry.markdown.model.RawHtml;
import io.github.digitalsmile.goldberry.markdown.model.Strong;
import io.github.digitalsmile.goldberry.markdown.model.Struck;
import io.github.digitalsmile.goldberry.markdown.model.Table;
import io.github.digitalsmile.goldberry.markdown.model.TableCell;
import io.github.digitalsmile.goldberry.markdown.model.TableRow;
import io.github.digitalsmile.goldberry.markdown.model.Text;
import io.github.digitalsmile.goldberry.markdown.model.ThematicBreak;
import io.github.digitalsmile.goldberry.markdown.model.Underlined;
import io.github.digitalsmile.goldberry.markdown.model.WikiLink;
import io.github.digitalsmile.goldberry.natives.md4c.BlockDetail;
import io.github.digitalsmile.goldberry.natives.md4c.MarkdownAttribute;
import io.github.digitalsmile.goldberry.natives.md4c.MarkdownEvent;
import io.github.digitalsmile.goldberry.natives.md4c.Md4c;
import io.github.digitalsmile.goldberry.natives.md4c.SpanDetail;
import io.github.digitalsmile.goldberry.natives.md4c.enums.BlockType;
import io.github.digitalsmile.goldberry.natives.md4c.enums.CellAlign;
import io.github.digitalsmile.goldberry.natives.md4c.enums.SpanType;
import io.github.digitalsmile.goldberry.natives.md4c.enums.TextType;

/// md4c's flat event stream, folded into the model's tree.
///
/// **This class is the seam**, and it is the only one in the module: md4c's
/// vocabulary comes in and the toolkit's goes out, which is why nothing exported
/// from `:html` mentions a `MarkdownEvent`, a `BlockType` or an `MD_ALIGN`. The same
/// arrangement `:core` uses for Yoga and Blend2D — a translation in one
/// package-private file, and a compiler error if it leaks (ADR-0280, ADR-0294).
///
/// The fold is a stack. Every `EnterBlock` and `EnterSpan` pushes a frame, every
/// `Leave` pops one, builds its node and hands it to the frame underneath. That the
/// stream is well nested is md4c's guarantee rather than an assumption: it is a
/// parser, and its own tests are what enforce it — but a stream that violated it
/// would be caught here, because a pop that finds the wrong type raises rather than
/// building something plausible.
///
/// ## Two things md4c does that the model does not repeat
///
/// **A tight list item holds no paragraph.** md4c reports `- one` as a list item with
/// text directly inside it, and `- one\n\n- two` as one with a paragraph inside.
/// That is faithful to CommonMark's tight/loose distinction but awkward to consume —
/// a renderer would have two shapes of item to handle — so a tight item's inlines are
/// wrapped in a [Paragraph] here and the distinction survives where it belongs, on
/// [BulletList#tight()]. A renderer draws a tight list closer together; it does not
/// have to know that the parser said so twice.
///
/// **A table's rows arrive inside `THEAD` and `TBODY`.** Those two blocks carry
/// nothing of their own, so they are not model nodes; their rows are marked and
/// flattened, and [Table] holds head and body separately.
final class MarkdownParser {

    private MarkdownParser() {}

    /// One open block or span, and what has accumulated inside it.
    ///
    /// `blocks` and `inlines` are both here because a block can hold either — a
    /// quote holds blocks, a paragraph holds inlines — and which one a frame ends up
    /// using is decided by what arrives in it rather than by its type. `text` is for
    /// the two constructs whose content is bytes and not nodes: a code block and a
    /// code span.
    private static final class Frame {

        private final @Nullable BlockType block;
        private final @Nullable SpanType span;
        private final @Nullable BlockDetail blockDetail;
        private final @Nullable SpanDetail spanDetail;
        private final List<Block> blocks = new ArrayList<>();
        private final List<Inline> inlines = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();

        private Frame(BlockType block, BlockDetail detail) {
            this.block = block;
            this.blockDetail = detail;
            this.span = null;
            this.spanDetail = null;
        }

        private Frame(SpanType span, SpanDetail detail) {
            this.span = span;
            this.spanDetail = detail;
            this.block = null;
            this.blockDetail = null;
        }

        /// Adds an inline, merging it into the run before it when both are plain
        /// text.
        ///
        /// md4c splits a sentence at every entity and every escape — `AT&amp;T` is
        /// three events — and a model that kept them apart would make
        /// `List.of(new Text("AT&T"))` fail against a document that says exactly
        /// that. Merging here means a consumer sees runs that are as long as they can
        /// be, which is also one text widget per run rather than three.
        private void add(Inline inline) {
            if (inline instanceof Text(var more)
                    && !inlines.isEmpty()
                    && inlines.getLast() instanceof Text(var already)) {
                inlines.set(inlines.size() - 1, new Text(already + more));
                return;
            }
            inlines.add(inline);
        }
    }

    /// Parses `markdown` in `syntax`.
    static Document parse(String markdown, MarkdownSyntax syntax) {
        return fold(Md4c.get().parse(markdown, syntax.flags()));
    }

    /// The fold itself, taken separately so that a test can drive it with a stream it
    /// wrote by hand — which is how the nesting failures below are covered without a
    /// document that provokes md4c into misbehaving.
    static Document fold(List<MarkdownEvent> events) {
        var stack = new ArrayDeque<Frame>();
        Document document = null;

        for (var event : events) {
            switch (event) {
                case MarkdownEvent.EnterBlock(var type, var detail) -> stack.push(new Frame(type, detail));
                case MarkdownEvent.EnterSpan(var type, var detail) -> stack.push(new Frame(type, detail));
                case MarkdownEvent.LeaveBlock(var type) -> {
                    var frame = pop(stack, type);
                    if (type == BlockType.DOC) {
                        document = new Document(frame.blocks);
                    } else {
                        block(frame, type, stack);
                    }
                }
                case MarkdownEvent.LeaveSpan(var type) -> {
                    var frame = pop(stack, type);
                    current(stack).add(span(frame, type));
                }
                case MarkdownEvent.Text(var type, var text) -> text(current(stack), type, text);
            }
        }

        if (document == null) {
            throw new IllegalStateException("the event stream held no document; md4c always brackets one");
        }
        return document;
    }

    private static Frame pop(Deque<Frame> stack, Object closing) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("the event stream closes " + closing + " with nothing open");
        }
        var frame = stack.pop();
        var opened = frame.block == null ? frame.span : frame.block;
        if (!closing.equals(opened)) {
            throw new IllegalStateException("the event stream closes " + closing + " while " + opened + " is open");
        }
        return frame;
    }

    private static Frame current(Deque<Frame> stack) {
        var frame = stack.peek();
        if (frame == null) {
            throw new IllegalStateException("the event stream has content outside the document");
        }
        return frame;
    }

    /// Builds a block from its finished frame and adds it to its parent.
    ///
    /// `THEAD` and `TBODY` add their *rows* rather than themselves, which is what
    /// flattens the table.
    private static void block(Frame frame, BlockType type, Deque<Frame> stack) {
        var parent = current(stack);
        switch (type) {
            case P -> parent.blocks.add(new Paragraph(frame.inlines));
            case H -> parent.blocks.add(new Heading(level(frame), frame.inlines));
            case QUOTE -> parent.blocks.add(new Quote(frame.blocks));
            case UL -> {
                var detail = (BlockDetail.BulletList) frame.blockDetail;
                parent.blocks.add(new BulletList(detail != null && detail.tight(), items(frame)));
            }
            case OL -> {
                var detail = (BlockDetail.NumberedList) frame.blockDetail;
                var start = detail == null ? 1 : detail.start();
                parent.blocks.add(new NumberedList(start, detail != null && detail.tight(), items(frame)));
            }
            case LI -> parent.blocks.add(item(frame));
            case HR -> parent.blocks.add(new ThematicBreak());
            case CODE -> parent.blocks.add(codeBlock(frame));
            case HTML -> parent.blocks.add(new HtmlBlock(frame.text.toString()));
            case TABLE -> parent.blocks.add(table(frame));
            // No node of their own: a head group and a body group carry nothing
            // except which half their rows are in, and the rows already say so.
            case THEAD, TBODY -> parent.blocks.addAll(frame.blocks);
            case TR -> parent.blocks.add(new TableRow(headerRow(frame, stack), cells(frame)));
            case TH, TD -> parent.blocks.add(new TableCell(type == BlockType.TH, alignment(frame), frame.inlines));
            case DOC -> throw new IllegalStateException("the document is closed by fold(), not here");
        }
    }

    private static int level(Frame frame) {
        return frame.blockDetail instanceof BlockDetail.Heading(var level) ? level : 1;
    }

    /// A list's items. Anything else a list frame collected is a stream md4c would
    /// never produce, and dropping it silently is how a renderer loses content.
    private static List<Item> items(Frame frame) {
        var items = new ArrayList<Item>(frame.blocks.size());
        for (var block : frame.blocks) {
            if (block instanceof Item item) {
                items.add(item);
            } else {
                throw new IllegalStateException(
                        "a list holds items, not a " + block.getClass().getSimpleName());
            }
        }
        return items;
    }

    /// One list item — with its inlines wrapped in a paragraph when md4c gave it
    /// none, which is what a tight list looks like on the wire.
    private static Item item(Frame frame) {
        var blocks = new ArrayList<Block>(frame.blocks);
        if (!frame.inlines.isEmpty()) {
            blocks.addFirst(new Paragraph(frame.inlines));
        }
        if (frame.blockDetail instanceof BlockDetail.Item detail && detail.task()) {
            return new Item(true, detail.checked(), blocks);
        }
        return new Item(blocks);
    }

    private static CodeBlock codeBlock(Frame frame) {
        var detail = frame.blockDetail instanceof BlockDetail.Code code ? code : null;
        if (detail == null) {
            return new CodeBlock(frame.text.toString());
        }
        var language = detail.language().isPresent() ? detail.language().plain() : null;
        return new CodeBlock(language, detail.info().plain(), frame.text.toString());
    }

    private static Table table(Frame frame) {
        var head = new ArrayList<TableRow>();
        var body = new ArrayList<TableRow>();
        for (var block : frame.blocks) {
            if (block instanceof TableRow row) {
                (row.header() ? head : body).add(row);
            } else {
                throw new IllegalStateException(
                        "a table holds rows, not a " + block.getClass().getSimpleName());
            }
        }
        return new Table(head, body);
    }

    /// Whether a row belongs to the head, which its enclosing group is what says.
    ///
    /// Read from the stack rather than from the row's own cells, so that an empty row
    /// — no cells at all — still lands in the right half.
    private static boolean headerRow(Frame frame, Deque<Frame> stack) {
        var parent = stack.peek();
        if (parent != null && parent.block == BlockType.THEAD) {
            return true;
        }
        return !frame.blocks.isEmpty() && frame.blocks.getFirst() instanceof TableCell cell && cell.header();
    }

    private static List<TableCell> cells(Frame frame) {
        var cells = new ArrayList<TableCell>(frame.blocks.size());
        for (var block : frame.blocks) {
            if (block instanceof TableCell cell) {
                cells.add(cell);
            } else {
                throw new IllegalStateException(
                        "a row holds cells, not a " + block.getClass().getSimpleName());
            }
        }
        return cells;
    }

    private static CellAlignment alignment(Frame frame) {
        if (!(frame.blockDetail instanceof BlockDetail.Cell(var align))) {
            return CellAlignment.DEFAULT;
        }
        return switch (align) {
            case CellAlign.DEFAULT -> CellAlignment.DEFAULT;
            case CellAlign.LEFT -> CellAlignment.START;
            case CellAlign.CENTER -> CellAlignment.CENTER;
            case CellAlign.RIGHT -> CellAlignment.END;
        };
    }

    /// Builds an inline from its finished frame.
    private static Inline span(Frame frame, SpanType type) {
        return switch (type) {
            case EM -> new Emphasis(frame.inlines);
            case STRONG -> new Strong(frame.inlines);
            case DEL -> new Struck(frame.inlines);
            case U -> new Underlined(frame.inlines);
            case CODE -> new Code(frame.text.toString());
            case A -> link(frame);
            case IMG -> image(frame);
            case WIKILINK -> wikiLink(frame);
            // Unreachable while `MarkdownExtension` offers no LaTeX: md4c cannot
            // report a math span without the flag. Rendered as code rather than
            // dropped, so that a dialect grown here later loses no content while
            // waiting for something that can set an equation.
            case LATEXMATH, LATEXMATH_DISPLAY -> new Code(frame.text.toString());
        };
    }

    private static Inline link(Frame frame) {
        var detail = frame.spanDetail instanceof SpanDetail.Link link ? link : null;
        if (detail == null) {
            return new Link("", frame.inlines);
        }
        return new Link(resolve(detail.href()), title(detail.title()), detail.autolink(), frame.inlines);
    }

    private static Inline image(Frame frame) {
        var detail = frame.spanDetail instanceof SpanDetail.Image image ? image : null;
        // The alt text is Markdown in its own right, and what an alt text is has no
        // marks in it -- so it is flattened here rather than kept as a tree nobody
        // can read out loud.
        var alt = Inlines.text(frame.inlines);
        if (detail == null) {
            return new Image("", null, alt);
        }
        return new Image(resolve(detail.src()), title(detail.title()), alt);
    }

    private static Inline wikiLink(Frame frame) {
        var detail = frame.spanDetail instanceof SpanDetail.WikiLink wiki ? wiki : null;
        var target = detail == null ? "" : resolve(detail.target());
        // `[[target]]` with no label carries the target as its text, which md4c
        // reports as text events -- so there is nothing to substitute here.
        return new WikiLink(target, frame.inlines);
    }

    /// An attribute's text, with its entities resolved.
    ///
    /// A URL is where this matters most: `?a=1&amp;b=2` has to become `?a=1&b=2`
    /// before anybody follows it, and md4c hands the parts over separately precisely
    /// so that whoever consumes them can decide (ADR-0294).
    private static String resolve(MarkdownAttribute attribute) {
        var out = new StringBuilder();
        for (var part : attribute.parts()) {
            out.append(
                    switch (part.type()) {
                        case TextType.ENTITY -> Entities.resolve(part.text());
                        case TextType.NULLCHAR -> "�";
                        default -> part.text();
                    });
        }
        return out.toString();
    }

    private static @Nullable String title(MarkdownAttribute attribute) {
        return attribute.isPresent() ? resolve(attribute) : null;
    }

    /// A run of text, put wherever the open frame keeps its content.
    private static void text(Frame frame, TextType type, String text) {
        var verbatim = frame.span == SpanType.CODE
                || frame.span == SpanType.LATEXMATH
                || frame.span == SpanType.LATEXMATH_DISPLAY
                || frame.block == BlockType.CODE
                || frame.block == BlockType.HTML;
        if (verbatim) {
            // Inside a fence or a code span every byte is content, entities included:
            // `&amp;` in a program is three characters the program needs.
            frame.text.append(text);
            return;
        }
        switch (type) {
            case NORMAL, CODE, LATEXMATH -> frame.add(new Text(text));
            case ENTITY -> frame.add(new Text(Entities.resolve(text)));
            case NULLCHAR -> frame.add(new Text("�"));
            case BR -> frame.inlines.add(new LineBreak(true));
            case SOFTBR -> frame.inlines.add(new LineBreak(false));
            // Inline markup outside an HTML block: a node of its own, so that the
            // HTML writer can pass it through and a widget can show what it is.
            case HTML -> frame.inlines.add(new RawHtml(text));
        }
    }
}
