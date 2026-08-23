package io.github.digitalsmile.goldberry.widgets.controls.select;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Placement;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Fitted;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Whether a [Select]'s list is open, where the field is, and what typing means.
///
/// Everything here is the half of the control that cannot be a value: a popup
/// window, a rectangle from the last frame, and the letters of a typeahead that
/// has not timed out yet ([ADR-0141]).
final class SelectState extends State<Select> {

    /// How long a typeahead lasts before the next letter starts a new one.
    ///
    /// A second, which is the interval every desktop list uses: long enough to
    /// type "no" and reach Norway rather than Oman, short enough that coming back
    /// a moment later starts again rather than continuing a word the user has
    /// forgotten typing.
    private static final long TYPEAHEAD_MILLIS = 1000;

    /// The open list, or null. Closed by choosing a row, by [#toggle], and by the
    /// popup's own light dismissal — a press outside or `Escape` — which this
    /// notices through [Popup#isOpen()] rather than being told.
    private Popup list;

    /// Where the last frame painted the field, and what clipped it.
    ///
    /// **Not `setState`**: nothing drawn depends on it, and marking the element
    /// dirty from [io.github.digitalsmile.goldberry.input.handler.Located] is how a
    /// widget told where it is ends up rebuilding forever (ADR-0119).
    private LogicalRect field = LogicalRect.of(0, 0, 0, 0);

    /// The typeahead so far, and when it was last added to.
    private String typed = "";
    private long typedAt;

    /// The window this is being built into, captured for the handlers.
    ///
    /// Read in `build` and **used** only from a click or a keypress, which is
    /// what [BuildContext#host()] allows: a build that read anything off a host
    /// would depend on the last frame, and nothing invalidates that.
    private Host host;

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var select = widget();
        // A list left open over a control that has become disabled would be a
        // menu with no owner. Checked on every build rather than only when
        // `disabled` is written, because it can arrive through a binding.
        if (select.disabled()) {
            close();
        }
        // The open list, told what the model now says. Here rather than at the
        // moment of the click, because this is the first place the application's
        // answer is visible -- see [#choose]. Cheap and idempotent: a popup whose
        // description has not changed reconciles to nothing.
        if (isOpen() && (select.multiple() || select.isTree())) {
            list.content(panel());
        }
        return new SelectField(
                select.label(), select.selected() == null, chips(select), editor(select),
                isOpen(), select.disabled(), select.attributes(),
                this::toggle, this::typeahead, this::restore, this::settle, this::located);
    }

    /// §3's "renders the selection as `badge` chips ... each with a remove
    /// affordance", and empty for every select that is not `multiple`.
    ///
    /// A chip's × reports the value through `change` exactly as picking it from
    /// the list does, because in a multiple control **`change` is a toggle**: the
    /// set is the application's, and asking for a value that is already in it can
    /// only mean taking it out ([ADR-0182]). One channel rather than two is also
    /// what keeps `select multiple=` inside the `Consumer<String>` every other
    /// valued control reports through.
    private java.util.List<Widget> chips(Select select) {
        if (!select.multiple()) {
            return java.util.List.of();
        }
        var chosen = select.selectedOptions();
        var out = new java.util.ArrayList<Widget>(chosen.size());
        for (var option : chosen) {
            out.add(new SelectChip(option.label(), () -> choose(option.value())));
        }
        return java.util.List.copyOf(out);
    }

    /// What the editable control currently holds, or null while it is showing the
    /// committed value.
    ///
    /// The **offered** text, in `TextInput`'s sense: it is handed down as that
    /// widget's `value`, and `TextInputState.follow` overwrites the field only
    /// when this *changes* — so typing is never fought, and setting it back to
    /// the committed label is exactly how `Esc` restores ([ADR-0183]).
    private String typedText;

    /// §3's editable closed control, or null for a `select` you cannot type in.
    ///
    /// A real [io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput],
    /// because §3 says "makes the closed control an editable `text-input`" and
    /// because everything an editable field needs — the edit model, the undo
    /// history, the clipboard, the caret's blink — already lives there and has
    /// rules in it. A second editor would be a second set of those rules.
    private Widget editor(Select select) {
        if (!select.autocomplete()) {
            return null;
        }
        var shown = typedText != null ? typedText : committedLabel(select);
        return new io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput(
                        shown, this::typed)
                .placeholder(select.placeholder())
                .disabled(select.disabled());
    }

    /// The label of the value the model currently holds — what `Esc` restores to
    /// and what a refused free-typed value falls back to.
    private static String committedLabel(Select select) {
        var option = select.selected();
        return option == null ? "" : option.label();
    }

    /// A keystroke in the editable control.
    ///
    /// Two things happen and they are separate on purpose: the query goes **up**
    /// for the application to filter on, and the list is opened if it was not.
    /// Nothing is selected and nothing is committed — a user typing is narrowing,
    /// not choosing (§3).
    private void typed(String text) {
        setState(() -> typedText = text);
        var onQuery = widget().onQuery();
        if (onQuery != null) {
            onQuery.accept(text);
        }
        if (!isOpen()) {
            open();
        } else {
            reopenRows();
        }
    }

    /// §3: "`Esc` restores the last committed value rather than clearing".
    ///
    /// Which is the sentence that tells a combobox apart from a search box: the
    /// control holds a value, typing is a way of *reaching* one, and abandoning
    /// the attempt leaves the value alone. Clearing would throw away something
    /// the user never asked to lose.
    void restore() {
        close();
        setState(() -> typedText = null);
    }

    /// §3: "a free-typed value is refused unless `free=#true`".
    ///
    /// Called when the editable control loses the keyboard, which is the moment a
    /// half-typed value stops being an attempt and starts being an answer. A
    /// `free` control keeps whatever was typed and reports it; every other one
    /// puts the committed value back, because a combobox is a **set** of values
    /// and text naming none of them is a mistake rather than a new member.
    void settle() {
        var select = widget();
        if (!select.autocomplete() || typedText == null) {
            return;
        }
        var typed = typedText;
        var matches = select.options().stream()
                .anyMatch(option -> option.label().equals(typed) || option.value().equals(typed));
        if (matches || !select.free()) {
            // A match is already the committed value, or is about to be reported
            // by whatever chose it; either way the editor goes back to showing
            // the model rather than a string that happens to agree with it.
            restore();
            return;
        }
        setState(() -> typedText = null);
        var onChange = select.onChange();
        if (onChange != null) {
            onChange.accept(typed);
        }
    }

    @Override
    protected void dispose() {
        // An element that goes away with its list showing would leave a platform
        // window parented to nothing -- the one leak a widget can cause, because
        // a popup is not a value and is not collected with the tree.
        close();
        super.dispose();
    }

    /// Whether the list is showing, allowing for a popup that dismissed itself.
    private boolean isOpen() {
        if (list != null && !list.isOpen()) {
            list = null;
        }
        return list != null;
    }

    /// What a click, `Space` or `Alt+Down` on the field does.
    private void toggle() {
        if (isOpen()) {
            close();
            return;
        }
        open();
    }

    /// A list taller than the screen scrolls rather than losing its bottom — the
    /// answer `menu` gives to the same question, from the same helper.
    private static final Fitted VIEWPORT = new Fitted("select-viewport");

    /// Opens the list under the field.
    ///
    /// Nothing happens without a window, and that is a normal outcome rather than
    /// an error: a golden image and a layout preview build the same widget with no
    /// host behind it, and a control that threw there could not be drawn at all
    /// (ADR-0140).
    private void open() {
        var select = widget();
        if (host == null || select.disabled()
                || (select.options().isEmpty() && !select.isTree())) {
            return;
        }
        var chosen = chosenId(select);

        // At least as wide as the field, and wider when an option is longer. A
        // list narrower than the control it hangs off reads as a mistake rather
        // than as a menu, and no measurement of the *content* can know how wide
        // the field turned out (ADR-0145).
        // ... and no taller than the screen. A list longer than the display used
        // to be clamped to the near edge with its last options silently dropped,
        // which is the same gap `menu` had and the same fix: the popup facility
        // says what it measured, and a list that does not fit becomes a list of
        // the screen's height with the options scrolling inside it
        // ([ADR-0179](../../../../../../../../book/src/adr/0179-a-popup-says-what-it-measured.md)).
        // A combobox's list is **attached** rather than a menu: it hangs off a
        // field the user is typing into, and a focusable window would take the
        // keyboard off it (ADR-0186). Every other select opens a menu, which is
        // what it is.
        var opened = select.autocomplete()
                ? host.attachedPopup(panel(), field, Placement.BELOW,
                        field.size().width(), VIEWPORT)
                : host.popup(panel(), field, Placement.BELOW,
                        field.size().width(), VIEWPORT);
        // The anchor this was placed against, so a report of "it opened in the
        // wrong place" can be settled from a log rather than from guesses. The
        // rectangle is what the last frame *painted* the field as, which is the
        // only thing a popup can be anchored to (ADR-0119).
        LOG.debug("select list anchored to {} (field {}x{} at {},{})", field,
                field.size().width(), field.size().height(), field.left(), field.top());
        if (opened.isEmpty()) {
            // No popup windows on this driver. The list stays closed rather than
            // falling back to an in-window overlay, because the overlay would be
            // clipped to the window and §3 asks for this list specifically to
            // escape it (ADR-0102). Saying so is more use than nothing happening.
            LOG.info("this platform has no popup windows, so a select cannot open its list");
            return;
        }
        // The row that is already chosen, so `Down` moves from the value rather
        // than from the top of the list.
        //
        // **Unless it is a combobox**, where the keyboard belongs to the editor:
        // a list that focused a row on opening would swallow the second keystroke
        // and every one after it. The arrows still reach it, because the owner
        // forwards keys to whatever popup is open (ADR-0104, ADR-0185).
        if (select.autocomplete()) {
            opened.get().takesFocus(false);
        } else {
            opened.get().focusOn(chosen);
        }
        setState(() -> list = opened.get());
    }

    /// Reports a value and puts the list away — unless there is more to choose.
    ///
    /// **A `multiple` keeps its list open**, which is not a flourish: the whole
    /// point of the mode is picking several, and a list that shut after each one
    /// would make choosing three values three round trips through a popup that
    /// has to be measured, placed and opened again each time. §3 says the control
    /// "renders the selection" in the closed field, which is a sentence about a
    /// control the user has finished with.
    ///
    /// For the single-valued control the order matters and has not changed: the
    /// list closes **first**, so an application that opens a dialog from its
    /// `change` handler does not open it behind a popup window.
    private void choose(String value) {
        if (!widget().multiple()) {
            close();
            // The editor goes back to showing the model, which the `change` below
            // is about to move. Cleared rather than set to the new label, because
            // what the control shows is the *application's* answer and not this
            // control's guess at it (ADR-0063).
            setState(() -> typedText = null);
        }
        var onChange = widget().onChange();
        if (onChange != null) {
            onChange.accept(value);
        }
        // **Not re-described here.** The model has moved, but this control has not
        // been rebuilt yet -- `onChange` above has only just told the application,
        // and `widget()` is still the description that was current when the click
        // arrived. Re-describing the rows from it would draw the selection the
        // list had *before* the pick, which is exactly what "the chip appears and
        // the row stays grey" looked like. The refresh belongs in `build`, which
        // is by definition the first moment the new model is visible (ADR-0185).
    }

    /// What goes in the popup: §3's flat list, or a `tree` when one was given.
    ///
    /// The **same panel either way**, so the surface, the edge, the radius and
    /// the scroll-when-it-does-not-fit are one decision rather than two. What
    /// differs is the one child inside it, which is exactly what §3's sentence
    /// says: "takes a `tree`'s model instead of a flat option list, so the popup
    /// is a `tree`" ([ADR-0184]).
    private Widget panel() {
        var select = widget();
        if (!select.isTree()) {
            return new SelectList(rows());
        }
        return new SelectList(java.util.List.of(
                new io.github.digitalsmile.goldberry.widgets.panel.tree.Tree(
                        select.tree(), select.resolved(), this::chooseNode)));
    }

    /// A node was chosen from the tree — the same road an option takes.
    ///
    /// A tree's rows are not `option`s, so they cannot report through
    /// [Option#within]; the value is the node's id and it goes out through
    /// `change` like everything else.
    private void chooseNode(String id) {
        choose(id);
    }

    /// The list's rows, described from the model as it is right now.
    ///
    /// Shared by opening and by re-describing an open list, which a `multiple`
    /// does after every pick — two copies of this would be two answers to "which
    /// rows are ticked", and the second one would be the stale one.
    ///
    /// **Which options read as selected differs by mode**: a single-valued select
    /// marks the one [Select#resolved()] names, and a `multiple` marks every one
    /// in [Select#resolvedAll()]. A non-[Option] child is kept, uncounted and
    /// unwired — a heading between two groups of options is not an option,
    /// exactly as it is not a segment in a `segmented` bar.
    private java.util.List<Widget> rows() {
        var select = widget();
        var current = select.resolved();
        var all = select.resolvedAll();
        var rows = new ArrayList<Widget>(select.children().size());
        var index = 0;
        for (var child : select.children()) {
            if (child instanceof Option option) {
                var isSelected = select.multiple()
                        ? all.contains(option.value())
                        : option.value().equals(current);
                rows.add(option
                        .within(isSelected, () -> choose(option.value()), select.disabled())
                        .inAList()
                        .id("select-option-" + index++));
            } else {
                rows.add(child);
            }
        }
        return java.util.List.copyOf(rows);
    }

    /// The id of the row the keyboard should open on, so `Down` moves from the
    /// value rather than from the top of the list.
    ///
    /// The **first** selected one for a `multiple`, which is the only answer that
    /// is not arbitrary when there are several.
    private String chosenId(Select select) {
        var wanted = select.multiple()
                ? (select.resolvedAll().isEmpty() ? null : select.resolvedAll().getFirst())
                : select.resolved();
        if (wanted == null) {
            return null;
        }
        var index = 0;
        for (var child : select.children()) {
            if (child instanceof Option option) {
                if (option.value().equals(wanted)) {
                    return "select-option-" + index;
                }
                index++;
            }
        }
        return null;
    }

    /// Re-describes the open list — §3's "the popup stays open and narrows".
    ///
    /// Called from [#typed], where it *is* safe to read `widget()`: the query is
    /// this control's own state and does not travel through the application
    /// before the list has to show it.
    ///
    /// A popup is an element tree of its own with its own build schedule
    /// ([ADR-0103]), so a `setState` here reaches this control's field and
    /// nothing in the window the list is drawn in. Closing and reopening would
    /// flicker and lose the keyboard's place, so the list's tree is asked to
    /// rebuild where it stands.
    private void reopenRows() {
        if (list != null) {
            list.content(panel());
        }
    }

    private void close() {
        if (list == null) {
            return;
        }
        var open = list;
        list = null;
        if (open.isOpen()) {
            open.close();
        }
        // Only if this element is still mounted -- `unmount` closes the list too,
        // and a `setState` there would schedule a build for an element that has
        // gone.
        if (isMounted()) {
            setState(() -> {
            });
        }
    }

    /// §3's typeahead, on the **closed** control.
    ///
    /// The letters accumulate for [#TYPEAHEAD_MILLIS] and match the start of an
    /// option's label, case-insensitively. A first letter typed twice cycles
    /// through the options starting with it, which is what a list does when a
    /// user has forgotten how the rest of the word is spelled.
    ///
    /// It reports through `change` like everything else — typing does not set
    /// anything, it asks (ADR-0063).
    private void typeahead(String text) {
        var select = widget();
        if (select.disabled() || select.options().isEmpty()) {
            return;
        }
        var now = clock();
        var stale = now - typedAt > TYPEAHEAD_MILLIS;
        typedAt = now;
        // Three cases, and the middle one is the whole reason this is not a
        // string concatenation: the **same letter again** is a request for the
        // next option starting with it, not a search for "dd". Every desktop list
        // does this, and a user pressing `d` four times to reach the fourth
        // `d`-word is relying on it.
        if (stale || (text.length() == 1 && typed.equals(text))) {
            typed = text;
        } else {
            typed = typed + text;
        }

        var options = select.options();
        var current = select.resolved();
        var from = 0;
        // A repeated single letter cycles; a longer prefix always matches from
        // the top, because "no" then "nor" must not skip Norway for having
        // matched it once already.
        if (typed.length() == 1) {
            for (var i = 0; i < options.size(); i++) {
                if (options.get(i).value().equals(current)) {
                    from = i + 1;
                    break;
                }
            }
        }
        var match = matching(options, typed, from);
        if (match == null && from > 0) {
            match = matching(options, typed, 0);
        }
        if (match != null && !match.value().equals(current)) {
            var onChange = select.onChange();
            if (onChange != null) {
                onChange.accept(match.value());
            }
        }
    }

    /// The first enabled option at or after `from` whose label starts with
    /// `prefix`.
    private static Option matching(List<Option> options, String prefix, int from) {
        var wanted = prefix.toLowerCase(Locale.ROOT);
        for (var i = from; i < options.size(); i++) {
            var option = options.get(i);
            if (!option.disabled()
                    && option.label().toLowerCase(Locale.ROOT).startsWith(wanted)) {
                return option;
            }
        }
        return null;
    }

    /// Where the field was painted — see [SelectField].
    ///
    /// The clip is deliberately dropped. `Located` reports it because `affix`
    /// needs to compare itself against the viewport that confines it (ADR-0119);
    /// a popup is placed against the *display's* work area by `Placement`, so a
    /// list opened from a row half-scrolled out of a viewport is placed against
    /// where that row is on screen, which is where the user is looking.
    private void located(LogicalRect self, LogicalRect clip) {
        this.field = self;
    }

    /// The frame clock, as milliseconds.
    ///
    /// `System.nanoTime` and not the wall clock, for the reason every timing in
    /// this toolkit uses it: a typeahead that a clock adjustment could make
    /// negative would swallow the letter that triggered it.
    private static long clock() {
        return System.nanoTime() / 1_000_000;
    }

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(SelectState.class);
}
