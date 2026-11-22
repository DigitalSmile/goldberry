package dev.goldberry.widgets.form.richinput;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.bind.Subscription;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.i18n.Labels;
import dev.goldberry.i18n.ToolkitLabel;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.event.PreeditEvent;
import dev.goldberry.input.event.TextEvent;
import dev.goldberry.input.hit.Extent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Mod;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.paint.Box;
import dev.goldberry.render.clipboard.Clipboard;
import dev.goldberry.render.event.EventLoop;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.SpanPaint;
import dev.goldberry.text.edit.Editor;
import dev.goldberry.text.edit.keys.EditKeys;
import dev.goldberry.text.edit.keys.FormatCommand;
import dev.goldberry.text.font.Font;
import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkRun;
import dev.goldberry.text.mark.MarkStyle;
import dev.goldberry.text.mark.MarkedClipboard;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.text.mark.MentionQuery;
import dev.goldberry.text.spell.SpellCheck;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.form.edit.TextCommands;
import dev.goldberry.widgets.form.edit.TextMenu;
import dev.goldberry.widgets.form.spelling.FieldSpelling;
import dev.goldberry.widgets.form.spelling.SpellingInk;
import dev.goldberry.widgets.form.spelling.SpellingMenu;
import dev.goldberry.widgets.menu.Item;

/// What a [RichInput] holds: an [Editor] over its marked text, and what the
/// field needs around it.
///
/// The editor is made on the first frame, because it shapes in the font the
/// cascade resolves and nothing earlier knows it. A theme that changes the font
/// gets a new editor with the same text and caret; its undo history starts
/// again, as a field given a new font has a new document.
///
/// The width the text wraps at is last frame's border box less the padding,
/// from [RichInputBox#measured]. A change asks for one more frame, so a field
/// takes two frames to settle when it mounts and one per resize, and none
/// after: what the width decides (the rows, and so the height) cannot change
/// the width.
///
/// ## Around the selection
///
/// A selection that is not being dragged opens the formatting bar over its
/// first line, in a panel that never takes the keyboard ([FormatPopups]). The
/// bar follows the selection and goes when it collapses, when a composition
/// starts, or when the field loses the focus. `Ctrl+K` and the bar's Link open
/// the link editor, which does take the keyboard and gives it back when it
/// applies.
///
/// ## Mentions
///
/// With [RichInput#mentions] set, every change looks for a [MentionQuery] at the
/// caret and reports it when it opens, changes, moves or ends, as a
/// [MentionPrompt] the application completes or dismisses. While one is open
/// the application's key handler is offered the list keys first. A query
/// `Escape` closed stays closed until the caret leaves its word. A composition
/// changes no query: the input method's text is not the field's yet.
final class RichInputState extends State<RichInput> implements RichInputBox.Input, TextCommands, MentionPrompt.Field {

    /// How many lines a notch of the wheel scrolls.
    static final int WHEEL_LINES = 3;

    private @Nullable Editor editor;

    /// The font [#editor] shapes in; another one makes another editor.
    private @Nullable Font font;

    /// The text before there is an editor to hold it.
    private MarkedText pending = MarkedText.EMPTY;

    /// What [RichInput#onChange] last heard, or what was last loaded: the
    /// value an edit is compared against to decide whether to report one.
    private MarkedText published = MarkedText.EMPTY;

    private Map<Set<String>, MarkStyle> looks = Map.of();

    /// Whether [#editor] has been handed [#looks] yet: a new editor has not.
    private boolean styled;

    private Edges edges = Edges.NONE;

    private Extent bounds = Extent.NONE;

    private boolean focused;

    /// A count of changes the painter is compared by.
    private long revision;

    /// The spell check `spellcheck` and `lang` call for.
    private final FieldSpelling spelling = new FieldSpelling(this::recheck);

    /// The menu's rows for a misspelled word.
    private final SpellingMenu spellingMenu = new SpellingMenu(this::refreshMenu);

    /// Whether the press began with the middle button: a paste, so the drag
    /// after it selects nothing.
    private boolean pasting;

    private @Nullable Subscription following;

    private @Nullable Host host;

    /// Each half of the caret's blink, `text-area`'s interval.
    static final Duration BLINK = Duration.ofMillis(530);

    private boolean caretShown = true;

    private EventLoop.@Nullable Timer blink;

    /// Whether a button is down over the text: a selection being dragged is not
    /// settled, and gets no bar until it is let go.
    private boolean pointerDown;

    /// Where the last frame drew the field, in the window, or null before it
    /// was drawn.
    private @Nullable LogicalRect located;

    private final FormatPopups popups = new FormatPopups();

    /// What the link editor's address field holds while it is open.
    private String linkDraft = "";

    private @Nullable TextMenu menu;

    /// The query last reported to the application, or null while none is
    /// open.
    private @Nullable MentionPrompt prompt;

    /// Where a query `Escape` closed starts, which is not reported again until
    /// the caret leaves its word; -1 for none.
    private int dismissedAt = -1;

    /// The id the field goes by when the application gave it none, so the link
    /// editor can hand the keyboard back to it.
    private final String ownId = "rich-input-" + Integer.toHexString(System.identityHashCode(this));

    @Override
    protected void initState() {
        pending = initialValue();
        published = pending;
        follow();
    }

    @Override
    protected void didUpdateWidget(RichInput previous) {
        if (widget().source() != previous.source()) {
            unfollow();
            follow();
            load(initialValue());
        } else if (widget().source() == null && !widget().value().equals(previous.value())) {
            load(widget().value());
        }
        var current = editor;
        if (current != null) {
            current.readOnly(widget().readOnly() || widget().disabled());
            current.clipboardFormats(widget().clipboardFormats());
        }
        var before = previous.mentions();
        if (before != null && widget().mentions() == null && prompt != null) {
            // Told by the listener that heard it open, the only one that knows.
            prompt = null;
            dismissedAt = -1;
            before.onQuery().accept(Optional.empty());
        }
    }

    /// An open query is dropped without a word: whatever the application showed
    /// for it belongs to a tree that is going too.
    @Override
    protected void dispose() {
        spelling.close();
        prompt = null;
        unfollow();
        stopBlinking();
        popups.close();
        var open = menu;
        if (open != null) {
            open.close();
        }
    }

    private MarkedText initialValue() {
        var source = widget().source();
        return source == null ? widget().value() : convert(source.get());
    }

    private static MarkedText convert(@Nullable Object value) {
        return switch (value) {
            case null -> MarkedText.EMPTY;
            case MarkedText marked -> marked;
            case String text -> MarkedText.of(text);
            default -> MarkedText.of(value.toString());
        };
    }

    private void follow() {
        var source = widget().source();
        if (source != null) {
            following = source.subscribe(next -> {
                var value = convert(next);
                // The echo of this field's own change, through the model, is
                // not a new value to load: loading it would forget the caret
                // and the undo history on every keystroke.
                if (!value.equals(published)) {
                    setState(() -> load(value));
                }
            });
        }
    }

    private void unfollow() {
        var subscription = following;
        following = null;
        if (subscription != null) {
            subscription.close();
        }
    }

    /// A value to hold rather than an edit: the history starts again.
    private void load(MarkedText value) {
        published = value;
        var current = editor;
        if (current == null) {
            pending = value;
        } else if (!current.value().equals(value)) {
            current.value(value);
        }
        revision++;
    }

    /// The text, its marks and its caret, for a test or an application that
    /// holds the state.
    MarkedText value() {
        var current = editor;
        return current == null ? pending : current.value();
    }

    /// The editor, once a frame has made one.
    Optional<Editor> editor() {
        return Optional.ofNullable(editor);
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var input = widget();
        var value = value();
        var label = input.placeholder().isEmpty() ? value.text() : input.placeholder();
        var attributes = input.attributes().id() == null ? input.attributes().id(ownId) : input.attributes();
        return new RichInputBox(
                markSets(),
                input.placeholder(),
                label,
                !input.disabled(),
                !input.disabled() && !input.readOnly(),
                this,
                attributes,
                spelling.update(context, true) != null);
    }

    /// A check landed or the checkers changed: paint the words again. The
    /// revision moves so the painter is not the one already drawn.
    private void recheck() {
        if (isMounted()) {
            setState(() -> revision++);
        }
    }

    /// The misspelled words of each hard line, leaving out what is under code,
    /// a link, a mention or a kind of the application's own: those are not
    /// prose.
    private Editor.LineOverlay overlay(Editor current, SpellCheck check, SpellingInk ink, int text) {
        var marks = current.value().marks();
        var caret = focused ? current.edit().caret() : -1;
        return (k, line, start) -> {
            var end = start + line.length();
            var inLine = caret >= start && caret <= end ? caret - start : -1;
            var spans = ink.spans(line, 0, line.length(), check, inLine, text);
            if (spans.isEmpty() || marks.isEmpty()) {
                return spans;
            }
            var kept = new ArrayList<SpanPaint>(spans.size());
            for (var span : spans) {
                if (!underUnchecked(marks, start + span.start(), start + span.end())) {
                    kept.add(span);
                }
            }
            return kept;
        };
    }

    private static boolean underUnchecked(List<Mark> marks, int from, int to) {
        for (var mark : marks) {
            var prose = mark.kind() instanceof MarkKind.Style style && style != MarkKind.Style.CODE;
            if (!prose && mark.start() < to && mark.end() > from) {
                return true;
            }
        }
        return false;
    }

    /// The class sets of every set of kinds in the text, and of what is typed
    /// next, each once, in the order they first occur.
    private List<Set<String>> markSets() {
        var value = value();
        var sets = new LinkedHashSet<Set<String>>();
        for (var run : MarkRun.of(value.length(), value.marks())) {
            if (!run.isPlain()) {
                sets.add(classesOf(run.kinds()));
            }
        }
        var current = editor;
        if (current != null) {
            var typing = current.marked().typingKinds();
            if (!typing.isEmpty()) {
                sets.add(classesOf(typing));
            }
        }
        return new ArrayList<>(sets);
    }

    private static Set<String> classesOf(Set<MarkKind> kinds) {
        return kinds.stream().map(MarkKind::className).collect(Collectors.toUnmodifiableSet());
    }

    // --- the frame --------------------------------------------------------------

    @Override
    public Box laidOut(
            Font font,
            Map<Set<String>, MarkStyle> looks,
            RichInputBox.Colours colours,
            @Nullable Paragraph placeholder,
            ComputedStyle style,
            @Nullable SpellingInk misspelled) {
        var input = widget();
        edges = Edges.of(style);
        var current = editorIn(font);
        if (!styled || !looks.equals(this.looks)) {
            this.looks = looks;
            styled = true;
            current.markStyles(kinds -> looks.getOrDefault(classesOf(kinds), MarkStyle.plain(font)));
        }
        var width = bounds.width() - edges.horizontal();
        current.wrapWidth(width > 0 ? width : Paragraph.UNCONSTRAINED);
        var shown = Math.clamp(current.lines().size(), input.rows(), input.maxRows());
        current.viewportHeight(shown * font.lineHeight());
        var check = spelling.current();
        current.overlay(
                check == null || misspelled == null ? null : overlay(current, check, misspelled, colours.text()));
        var painter = new RichInputPainter(
                current,
                revision,
                new Editor.Ink(colours.text(), colours.selection(), colours.text()),
                focused && caretShown && !input.disabled() && !input.readOnly(),
                placeholder,
                colours.placeholder());
        return Box.of()
                .style(style)
                .size(style.width(), RichInputBox.heightOf(shown, font, edges))
                .painting(painter);
    }

    /// The editor that shapes in `font`: this one, or a new one holding its
    /// text and caret.
    private Editor editorIn(Font font) {
        var current = editor;
        if (current != null && this.font == font) {
            return current;
        }
        var made = new Editor(font)
                .multiline(true)
                .readOnly(widget().readOnly() || widget().disabled())
                .clipboardFormats(widget().clipboardFormats());
        if (current == null) {
            made.value(pending);
        } else {
            made.value(current.value());
            made.caretTo(current.edit().caret(), false);
        }
        var window = host;
        if (window != null) {
            made.clipboard(window.clipboard());
            window.primarySelection().ifPresent(made::primarySelection);
        }
        this.font = font;
        styled = false;
        editor = made;
        return made;
    }

    // --- input -------------------------------------------------------------------

    @Override
    public void pointer(PointerEvent event) {
        var current = editor;
        if (current == null || widget().disabled()) {
            return;
        }
        var at = event.content();
        var x = at.x();
        var y = at.y() + current.scrollTop();
        switch (event.kind()) {
            case PRESSED -> {
                pointerDown = true;
                pasting = event.button() == PointerEvent.Button.MIDDLE;
                if (pasting) {
                    current.pastePrimaryAt(x, y);
                } else {
                    current.pointerAt(x, y, event.modifiers().shift(), event.clickCount());
                }
                changed();
            }
            case MOVED -> {
                if (!pasting && !Float.isNaN(event.pressX())) {
                    current.pointerAt(x, y, true, 1);
                    changed();
                }
            }
            case RELEASED -> {
                pointerDown = false;
                if (!pasting) {
                    current.pointerReleased();
                }
                // The selection has settled: the bar's moment.
                refreshBar();
            }
            case WHEEL -> {
                // Only when there is somewhere to go, so a field at its end
                // leaves the wheel to the page around it.
                if (current.scrollBy(
                        event.deltaY() * WHEEL_LINES * current.document().font().lineHeight())) {
                    changed();
                    event.consume();
                }
                return;
            }
            default -> {
                return;
            }
        }
        event.consume();
    }

    @Override
    public void key(KeyEvent event) {
        var current = editor;
        if (current == null || widget().disabled()) {
            return;
        }
        if (routedToMentions(event)) {
            event.consume();
            return;
        }
        if (submits(event)) {
            submit();
            event.consume();
            return;
        }
        if (EditKeys.format(event) == FormatCommand.Simple.LINK && !widget().readOnly()) {
            if (openLinkEditor()) {
                event.consume();
            }
            return;
        }
        if (event.kind() == KeyEvent.Kind.PRESSED && event.is(Key.ESCAPE) && popups.barShown()) {
            popups.hideBar();
            event.consume();
            return;
        }
        if (current.onKey(event)) {
            event.consume();
            changed();
        }
    }

    /// Offers a list key to the application while a query is open, and closes
    /// the query on an `Escape` it left. Whether the key was taken.
    private boolean routedToMentions(KeyEvent event) {
        var mentions = widget().mentions();
        var open = prompt;
        if (mentions == null || open == null || !Mentions.routed(event)) {
            return false;
        }
        if (mentions.onKey().test(event)) {
            return true;
        }
        return event.is(Key.ESCAPE) && dismiss(open);
    }

    /// Whether `event` is the `Enter` that sends: a plain one, with somewhere to
    /// send to and no composition open, which an `Enter` is choosing a
    /// candidate for.
    private boolean submits(KeyEvent event) {
        var current = editor;
        return widget().onSubmit() != null
                && current != null
                && event.kind() == KeyEvent.Kind.PRESSED
                && event.is(Key.ENTER)
                && !event.modifiers().shift()
                && !current.isComposing();
    }

    private void submit() {
        var listener = widget().onSubmit();
        var current = editor;
        if (listener != null && current != null) {
            listener.accept(current.value());
        }
    }

    @Override
    public void text(TextEvent event) {
        var current = editor;
        if (current == null || widget().disabled()) {
            return;
        }
        if (current.onText(event.text())) {
            event.consume();
            changed();
        }
    }

    @Override
    public void preedit(PreeditEvent event) {
        var current = editor;
        if (current == null || widget().disabled()) {
            return;
        }
        if (current.onPreedit(event)) {
            event.consume();
            changed();
        }
    }

    @Override
    public void focus(boolean focused) {
        setState(() -> {
            this.focused = focused;
            caretShown = true;
            revision++;
        });
        if (focused) {
            schedule();
        } else {
            stopBlinking();
        }
        refreshBar();
        refreshQuery();
    }

    @Override
    public void located(LogicalRect self) {
        if (self.equals(located)) {
            return;
        }
        located = self;
        // The bar follows the field, and a field that arrived where the bar
        // has to go has nothing else to ask for another look. A suggestion
        // list hangs off the query the same way.
        refreshBar();
        refreshQuery();
    }

    @Override
    public void measured(Extent bounds) {
        var changed = Math.abs(bounds.width() - this.bounds.width()) > 0.5f;
        this.bounds = bounds;
        if (changed && isMounted()) {
            setState(() -> revision++);
        }
    }

    /// The caret's line, in the content box: where a candidate window goes.
    @Override
    public Optional<LogicalRect> caretArea() {
        var current = editor;
        if (current == null || !focused || widget().disabled() || widget().readOnly()) {
            return Optional.empty();
        }
        var line = current.caretLine();
        return Optional.of(
                LogicalRect.of(line.left(), (float) (line.top() - current.scrollTop()), line.width(), line.height()));
    }

    @Override
    public double caretOffset() {
        var current = editor;
        return current == null ? 0 : current.caret().x();
    }

    // --- what the application can ask of it ---------------------------------------

    /// Turns `kind` on or off over the selection, or for what is typed next: a
    /// formatting bar's button.
    boolean toggle(MarkKind kind) {
        var current = editor;
        if (current == null || widget().disabled() || !current.toggle(kind)) {
            return false;
        }
        changed();
        return true;
    }

    /// Something the user did changed the editor: draw it, report the value if
    /// it is a new one, keep the caret solid while it moves, and put the bar
    /// where the selection now is.
    private void changed() {
        setState(() -> {
            revision++;
            caretShown = true;
        });
        schedule();
        refreshBar();
        refreshQuery();
        var current = editor;
        if (current == null) {
            return;
        }
        var now = current.value();
        if (!now.equals(published)) {
            published = now;
            var listener = widget().onChange();
            if (listener != null) {
                listener.accept(now);
            }
        }
    }

    // --- the formatting bar -----------------------------------------------------

    /// Opens, moves, brings up to date or puts away the bar, so that it is out
    /// exactly while a settled selection in an editable, focused field has one
    /// to offer.
    private void refreshBar() {
        var input = widget();
        var current = editor;
        var window = host;
        var field = located;
        if (current == null
                || window == null
                || field == null
                || !isMounted()
                || input.formatting().isEmpty()
                || input.disabled()
                || input.readOnly()
                || !focused
                || pointerDown
                || current.isComposing()
                || !current.edit().hasSelection()
                || popups.linkShown()) {
            popups.hideBar();
            return;
        }
        var anchor = selectionAnchor(current, field);
        if (anchor == null) {
            popups.hideBar();
            return;
        }
        popups.showBar(window, context(), anchor, input.formatting(), activeCommands(current), this::format);
    }

    /// The selection's first line, in the window.
    private @Nullable LogicalRect selectionAnchor(Editor current, LogicalRect field) {
        var rects = current.selectionRects();
        if (rects.isEmpty()) {
            return null;
        }
        return inWindow(rects.getFirst(), current, field);
    }

    /// `rect`, in the editor's own space, in the window: past the field's
    /// padding and border, less the scroll.
    private LogicalRect inWindow(LogicalRect rect, Editor current, @Nullable LogicalRect field) {
        var left = field == null ? 0 : field.left();
        var top = field == null ? 0 : field.top();
        return LogicalRect.of(
                (float) (left + edges.left() + rect.left()),
                (float) (top + edges.top() + rect.top() - current.scrollTop()),
                Math.max(1, rect.width()),
                rect.height());
    }

    /// The commands that are on for the selection: what the bar lights.
    private static Set<FormatCommand> activeCommands(Editor current) {
        var kinds = current.activeKinds();
        var on = new LinkedHashSet<FormatCommand>();
        for (var kind : kinds) {
            switch (kind) {
                case MarkKind.Style style -> on.add(new FormatCommand.Toggle(style));
                case MarkKind.Link _ -> on.add(FormatCommand.Simple.LINK);
                default -> {}
            }
        }
        return Set.copyOf(on);
    }

    /// A formatting command from the bar or the menu.
    boolean format(FormatCommand command) {
        var current = editor;
        if (current == null || widget().disabled() || widget().readOnly()) {
            return false;
        }
        var done = switch (command) {
            case FormatCommand.Toggle(var style) -> current.toggle(style);
            case FormatCommand.Simple.CLEAR -> current.clearFormatting();
            case FormatCommand.Simple.LINK -> openLinkEditor();
        };
        if (done && command != FormatCommand.Simple.LINK) {
            changed();
        }
        return done;
    }

    // --- mentions ----------------------------------------------------------------

    /// Looks for a query at the caret and tells the application when it
    /// opened, changed, moved or ended. Nothing is open in a field that is not
    /// focused and editable.
    private void refreshQuery() {
        var input = widget();
        var mentions = input.mentions();
        var current = editor;
        if (mentions == null || current == null || !isMounted() || !focused || input.readOnly() || input.disabled()) {
            dismissedAt = -1;
            report(null);
            return;
        }
        if (current.isComposing()) {
            return;
        }
        var found = MentionQuery.at(current.marked(), mentions.triggers()).orElse(null);
        if (found == null || found.start() != dismissedAt) {
            dismissedAt = -1;
        }
        if (found == null || found.start() == dismissedAt) {
            report(null);
            return;
        }
        report(new MentionPrompt(found, queryAnchor(current, found), this));
    }

    /// The query's first line in the window, or the caret's line when the
    /// query has no width yet.
    private LogicalRect queryAnchor(Editor current, MentionQuery query) {
        var rects = current.rangeRects(query.start(), query.end());
        return inWindow(rects.isEmpty() ? current.caretLine() : rects.getFirst(), current, located);
    }

    /// `next` reported, unless it says what the open prompt says already; null
    /// reports the end of an open one.
    private void report(@Nullable MentionPrompt next) {
        var open = prompt;
        var mentions = widget().mentions();
        if (next == null) {
            prompt = null;
            if (open != null && mentions != null) {
                mentions.onQuery().accept(Optional.empty());
            }
            return;
        }
        if (open != null && open.query().equals(next.query()) && open.anchor().equals(next.anchor())) {
            return;
        }
        prompt = next;
        if (mentions != null) {
            mentions.onQuery().accept(Optional.of(next));
        }
    }

    @Override
    public boolean complete(MentionPrompt asked, String text, @Nullable MarkKind kind) {
        var current = editor;
        if (asked != prompt || current == null || widget().readOnly() || widget().disabled()) {
            return false;
        }
        var query = asked.query();
        if (!current.replace(query.start(), query.end(), query.completion(text, kind, current.value()))) {
            return false;
        }
        dismissedAt = -1;
        changed();
        return true;
    }

    @Override
    public boolean dismiss(MentionPrompt asked) {
        if (asked != prompt) {
            return false;
        }
        dismissedAt = asked.query().start();
        report(null);
        return true;
    }

    @Override
    public boolean isCurrent(MentionPrompt asked) {
        return asked == prompt;
    }

    /// For a test: the query last reported, or null.
    @Nullable
    MentionPrompt prompt() {
        return prompt;
    }

    // --- the link editor --------------------------------------------------------

    /// Opens the link editor over the selection, or over the link the caret is
    /// in, which it selects first. Nothing happens with nothing selected and no
    /// link to edit.
    boolean openLinkEditor() {
        var current = editor;
        var window = host;
        var field = located;
        if (current == null || window == null || field == null || widget().readOnly() || widget().disabled()) {
            return false;
        }
        var value = current.value();
        if (!current.edit().hasSelection()) {
            var caret = current.edit().caret();
            var around = value.marks().stream()
                    .filter(mark ->
                            mark.kind() instanceof MarkKind.Link && mark.start() <= caret && caret <= mark.end())
                    .findFirst();
            if (around.isEmpty()) {
                return false;
            }
            current.caretTo(around.get().start(), false);
            current.caretTo(around.get().end(), true);
        }
        var existing = linksOver(current).stream().findFirst();
        linkDraft = existing.map(link -> link.uri().toString()).orElse("");
        var anchor = selectionAnchor(current, field);
        if (anchor == null) {
            return false;
        }
        var editor = new LinkEditor(
                linkDraft, existing.isPresent(), draft -> linkDraft = draft, this::applyLink, this::removeLink);
        popups.showLink(window, context(), anchor, editor);
        // A repaint for the link it may have selected, and not [#changed]: the
        // bar stays away while the editor is out.
        setState(() -> revision++);
        return true;
    }

    /// The links over any of the selection.
    private static List<MarkKind.Link> linksOver(Editor current) {
        var edit = current.edit();
        var links = new ArrayList<MarkKind.Link>();
        for (var mark : current.value().marks()) {
            if (mark.kind() instanceof MarkKind.Link link && mark.overlaps(edit.start(), edit.end())) {
                links.add(link);
            }
        }
        return links;
    }

    /// The address typed, on the selected words. An empty one takes the link
    /// off; one that is not an address changes nothing and leaves the editor
    /// open.
    void applyLink() {
        var current = editor;
        if (current == null) {
            return;
        }
        var trimmed = linkDraft.strip();
        if (trimmed.isEmpty()) {
            removeLink();
            return;
        }
        var uri = addressOf(trimmed);
        if (uri == null) {
            return;
        }
        current.mark(new MarkKind.Link(uri));
        closeLinkEditor();
    }

    /// Every link off the selected words.
    void removeLink() {
        var current = editor;
        if (current == null) {
            return;
        }
        for (var link : linksOver(current)) {
            current.unmark(link);
        }
        closeLinkEditor();
    }

    private void closeLinkEditor() {
        popups.hideLink();
        var window = host;
        if (window != null) {
            var id = widget().attributes().id();
            window.focus(id == null ? ownId : id, true);
        }
        changed();
    }

    /// `typed` as an address: as written when it has a scheme, and as `https`
    /// when it has none, which is how a person types one. Null when it is not
    /// an address at all.
    static @Nullable URI addressOf(String typed) {
        var written = typed.contains(":") ? typed : "https://" + typed;
        try {
            var uri = new URI(written);
            return uri.getScheme() == null ? null : uri;
        } catch (URISyntaxException notAnAddress) {
            return null;
        }
    }

    /// What the link editor's field holds: for a test.
    String linkDraft() {
        return linkDraft;
    }

    // --- the text menu ----------------------------------------------------------

    @Override
    public boolean openContextMenu(LogicalRect anchor) {
        var window = host;
        if (window == null || widget().disabled()) {
            return false;
        }
        var open = menu;
        if (open == null) {
            open = new TextMenu(window, this, () -> spellingMenu.rows(this::replaceWord), this::formatRows);
            menu = open;
        }
        var current = editor;
        if (current != null) {
            caretToPointer(current, anchor);
            spellingMenu.aim(spelling.current(), current.text(), current.edit().caret());
        }
        return open.open(anchor);
    }

    /// A right-click on the field, while it checks its spelling and nothing is
    /// selected, puts the caret where the pointer is, so the menu is about the
    /// word under it.
    private void caretToPointer(Editor current, LogicalRect anchor) {
        var self = located;
        if (spelling.current() == null
                || current.edit().hasSelection()
                || self == null
                || !anchor.size().isEmpty()
                || !self.contains(anchor.origin())) {
            return;
        }
        var x = anchor.origin().x() - self.origin().x() - edges.left();
        var y = anchor.origin().y() - self.origin().y() - edges.top() + current.scrollTop();
        current.pointerAt(x, y, false, 1);
        var _ = current.pointerReleased();
        changed();
    }

    private void refreshMenu() {
        var open = menu;
        if (open != null) {
            open.refresh();
        }
    }

    /// A suggestion chosen from the menu: the word replaced with the marks its
    /// first letter had, so a misspelled bold word comes back bold. One undo
    /// step.
    private boolean replaceWord(int start, int end, String with) {
        var current = editor;
        if (current == null || widget().readOnly() || widget().disabled()) {
            return false;
        }
        var replacement = MarkedText.of(with);
        for (var mark : current.value().marks()) {
            if (mark.start() <= start && mark.end() > start && mark.kind() instanceof MarkKind.Style) {
                replacement = replacement.mark(0, with.length(), mark.kind());
            }
        }
        var replaced = current.replace(start, end, replacement);
        if (replaced) {
            changed();
        }
        return replaced;
    }

    /// The Format submenu: every formatting command, ticked where it is on.
    List<Widget> formatRows() {
        var current = editor;
        if (current == null || widget().readOnly()) {
            return List.of();
        }
        var on = activeCommands(current);
        var rows = new ArrayList<Widget>();
        for (var button : FormatButton.defaults()) {
            var command = button.command();
            var row = new Item(button.label(), () -> format(command));
            row = row.accelerator(acceleratorOf(command));
            if (command instanceof FormatCommand.Toggle) {
                row = row.checked(on.contains(command));
            }
            rows.add(row);
        }
        return List.of(new Item(Labels.text(ToolkitLabel.FORMAT)).submenu(rows.toArray(Widget[]::new)));
    }

    /// The keys that do what `command` does, as [EditKeys#format] reads them.
    private static String acceleratorOf(FormatCommand command) {
        var shortcut = switch (command) {
            case FormatCommand.Toggle(var style) ->
                switch (style) {
                    case BOLD -> Shortcut.primary(Key.B);
                    case ITALIC -> Shortcut.primary(Key.I);
                    case UNDERLINE -> Shortcut.primary(Key.U);
                    case STRIKE -> Shortcut.primary(Key.X, Mod.SHIFT);
                    case CODE -> Shortcut.primary(Key.M, Mod.SHIFT);
                    case SPOILER -> Shortcut.primary(Key.P, Mod.SHIFT);
                };
            case FormatCommand.Simple.LINK -> Shortcut.primary(Key.K);
            case FormatCommand.Simple.CLEAR -> Shortcut.primary(Key.N, Mod.SHIFT);
        };
        return shortcut.toString();
    }

    @Override
    public boolean canCut() {
        return canCopy() && !widget().readOnly();
    }

    @Override
    public boolean canCopy() {
        var current = editor;
        return current != null && current.edit().hasSelection();
    }

    @Override
    public boolean canPaste() {
        var window = host;
        if (window == null || widget().readOnly()) {
            return false;
        }
        Clipboard board = window.clipboard();
        return board.hasText()
                || board.has(MarkedClipboard.MIME)
                || widget().clipboardFormats().stream().anyMatch(format -> board.has(format.mime()));
    }

    @Override
    public boolean canSelectAll() {
        var current = editor;
        return current != null && !current.text().isEmpty();
    }

    @Override
    public boolean cut() {
        var current = editor;
        if (current == null || widget().readOnly() || !current.cut()) {
            return false;
        }
        changed();
        return true;
    }

    @Override
    public boolean copy() {
        var current = editor;
        return current != null && current.copy();
    }

    @Override
    public boolean paste() {
        var current = editor;
        if (current == null || widget().readOnly() || !current.paste()) {
            return false;
        }
        changed();
        return true;
    }

    @Override
    public boolean selectAll() {
        var current = editor;
        if (current == null || current.text().isEmpty()) {
            return false;
        }
        current.caretTo(0, false);
        current.caretTo(current.text().length(), true);
        changed();
        return true;
    }

    // --- the blink --------------------------------------------------------------

    private void schedule() {
        stopBlinking();
        var window = host;
        if (window == null || !focused || widget().readOnly() || widget().disabled()) {
            return;
        }
        blink = window.after(BLINK, () -> {
            blink = null;
            if (!focused || !isMounted()) {
                return;
            }
            setState(() -> {
                caretShown = !caretShown;
                revision++;
            });
            schedule();
        });
    }

    private void stopBlinking() {
        var running = blink;
        blink = null;
        if (running != null) {
            running.cancel();
        }
    }

    /// For a test: the panels.
    FormatPopups popups() {
        return popups;
    }
}
