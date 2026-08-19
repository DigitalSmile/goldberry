package io.github.digitalsmile.goldberry.widgets.controls.select;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Placement;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
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
    /// dirty from [io.github.digitalsmile.goldberry.input.Located] is how a
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
        return new SelectField(
                select.label(), select.selected() == null, isOpen(), select.disabled(),
                select.attributes(), this::toggle, this::typeahead, this::located);
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

    /// Opens the list under the field.
    ///
    /// Nothing happens without a window, and that is a normal outcome rather than
    /// an error: a golden image and a layout preview build the same widget with no
    /// host behind it, and a control that threw there could not be drawn at all
    /// (ADR-0140).
    private void open() {
        var select = widget();
        if (host == null || select.disabled() || select.options().isEmpty()) {
            return;
        }
        var rows = new ArrayList<Widget>(select.children().size());
        var current = select.resolved();
        var chosen = (String) null;
        var index = 0;
        for (var child : select.children()) {
            if (child instanceof Option option) {
                var isSelected = option.value().equals(current);
                var id = "select-option-" + index++;
                var row = option
                        .within(isSelected, () -> choose(option.value()), select.disabled())
                        .inAList()
                        .id(id);
                if (isSelected) {
                    chosen = id;
                }
                rows.add(row);
            } else {
                // Kept, uncounted and unwired -- a heading between two groups of
                // options is not an option, exactly as it is not a segment in a
                // `segmented` bar.
                rows.add(child);
            }
        }

        var opened = host.popup(new SelectList(rows), field, Placement.BELOW);
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
        opened.get().focusOn(chosen);
        setState(() -> list = opened.get());
    }

    /// Reports a value and puts the list away.
    ///
    /// The order matters: the list closes first, so an application that opens a
    /// dialog from its `change` handler does not open it behind a popup window.
    private void choose(String value) {
        close();
        var onChange = widget().onChange();
        if (onChange != null) {
            onChange.accept(value);
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
