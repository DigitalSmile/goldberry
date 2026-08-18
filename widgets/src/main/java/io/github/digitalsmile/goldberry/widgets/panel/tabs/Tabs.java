package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Bindable;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// A strip of tabs over one panel — `docs/core-widgets.md` §5's `tabs`.
///
/// ```kdl
/// tabs bind="view.tab" change="app.pick-tab" close="app.close-tab" new="app.new-tab" {
///     tab value="editor" icon="file" "Editor" { text "…" }
///     tab value="log" colour="#bf616a" closable=#true "Log" { text "…" }
/// }
/// ```
///
/// ## Controlled, like every other value in this toolkit
///
/// The strip **reads** which tab is selected through `bind` and reports what the
/// user asked for through `change`. It selects nothing itself
/// ([ADR-0063](../../../../../../../../book/src/adr/0063-data-flows-down-events-flow-up.md)),
/// which is the same shape `radio-group` and `segmented` have — and it is what
/// makes adding and removing tabs work without a single API for either: the list
/// of tabs is the application's, `close` asks for one to go, `new` asks for one to
/// arrive, and the strip draws whatever comes back
/// ([ADR-0107](../../../../../../../../book/src/adr/0107-a-tab-strip-is-a-model-a-header-and-a-panel.md)).
///
/// A strip whose `close` handler does nothing keeps its tab, which is the visible
/// form of "the model did not change" and is where the bug is when a tab will not
/// close.
///
/// ## This node styles nothing
///
/// `tabs` as a **CSS type** is [TabStrip], the node this one builds. A stateful
/// widget that was also styled would put two `tabs` nodes in the cascade, one
/// inside the other, and every rule in `controls.css` would apply to both — which
/// is a doubled padding and a doubled border waiting to happen. So this is a
/// composition node: it holds the model, and what it builds holds the appearance
/// ([ADR-0109](../../../../../../../../book/src/adr/0109-a-tab-arrives-and-departs-on-the-frame-clock.md)).
///
/// ## Three parts, and only one of them is built twice
///
/// `tab-list` holds the headers; `tab-panel` holds the selected tab's content.
/// **Only the selected tab's content is built into an element at all** — §5's
/// "lazy content instantiation" — so nine unselected tabs cost nine headers and
/// nothing behind them.
///
/// ## Keyboard
///
/// One Tab stop with the arrows roving inside it, per §7.2 — `HORIZONTAL`,
/// because a top-placed strip is a row and `Up`/`Down` belong to whatever is
/// above it ([ADR-0078](../../../../../../../../book/src/adr/0078-a-focus-scope-has-an-axis.md)).
/// `Delete` on a closable tab asks for it to close, which is the keyboard's answer
/// to an affordance that is otherwise a small target for a pointer.
///
/// @param value      the selected tab's value when nothing is bound
/// @param children   the tabs, as written. Anything that is not a [Tab] is drawn
///                   in the strip and left alone, which is how a spacer or a
///                   button gets into a tab bar
/// @param source     §9's `bind` — read-only
/// @param onChange   what the user asked to select
/// @param onClose    what the user asked to close, or null for a strip nobody can
///                   shorten
/// @param onNew      what the user asked to add, or null for no add affordance
/// @param attributes `id` and `class`, exactly as on the primitives
public record Tabs(
        String value, List<Widget> children, Observable<?> source, Consumer<String> onChange,
        Consumer<String> onClose, Runnable onNew, Attributes attributes)
        implements Widget.Stateful, Attributed<Tabs>, Bindable<Tabs> {

    public Tabs {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        Objects.requireNonNull(children, "children");
    }

    public Tabs(String value, Widget... children) {
        this(value, List.of(children), null, null, null, null, Attributes.NONE);
    }

    /// This strip reporting what the user picked.
    public Tabs onChange(Consumer<String> handler) {
        return new Tabs(value, children, source, handler, onClose, onNew, attributes);
    }

    /// This strip with closable tabs' × wired up. A tab is closable when *it*
    /// says so; this is who hears about it.
    public Tabs onClose(Consumer<String> handler) {
        return new Tabs(value, children, source, onChange, handler, onNew, attributes);
    }

    /// This strip with an add affordance at the end of the row.
    ///
    /// §5 does not ask for one — it asks for "closable tabs optional" and says
    /// nothing about adding — but a strip that can lose tabs and never gain them
    /// is half a control, and the alternative is every application drawing its own
    /// `+` and lining it up with the row by hand.
    public Tabs onNew(Runnable handler) {
        return new Tabs(value, children, source, onChange, onClose, handler, attributes);
    }

    @Override
    public Tabs bound(Observable<?> value) {
        return new Tabs(this.value, children, value, onChange, onClose, onNew, attributes);
    }

    @Override
    public Observable<?> binding() {
        return source;
    }

    @Override
    public Tabs withAttributes(Attributes value) {
        return new Tabs(this.value, children, source, onChange, onClose, onNew, value);
    }

    /// The tabs as written, before the strip rebuilt them with what only it knows
    /// — for a test, and for an application that wants to count them.
    public List<Widget> rawTabs() {
        return children;
    }

    /// Which tab is selected: the bound value if there is one, the written one
    /// otherwise.
    public String selected() {
        if (source != null) {
            var bound = source.get();
            return bound == null ? null : bound.toString();
        }
        return value;
    }

    /// **Stateful**, and the state is one thing: which tabs are arriving or
    /// leaving.
    ///
    /// A tab that has just been added has to fade up from nothing, and one that
    /// has just been closed has to fade down — after the application has already
    /// dropped it from its list, so something has to hold on to it for the length
    /// of the animation. That is the whole of what [TabsState] does
    /// ([ADR-0109](../../../../../../../../book/src/adr/0109-a-tab-arrives-and-departs-on-the-frame-clock.md)).
    @Override
    public State<?> createState() {
        return new TabsState();
    }

    /// Asks for a tab. It does **not** select it — see the class note.
    void select(String picked) {
        if (onChange != null) {
            onChange.accept(picked);
        }
    }

    /// Asks for a tab to close. Same rule: the list is the application's.
    void close(String picked) {
        if (onClose != null) {
            onClose.accept(picked);
        }
    }
}
