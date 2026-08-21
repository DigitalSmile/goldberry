package io.github.digitalsmile.goldberry.widgets.panel.collapse;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.Phase;

/// Whether a [Collapse] is open, when the application is not the one deciding.
///
/// The whole of the state, and it is one boolean — which is why §5 calls it
/// "retained state" rather than a value: a section that has been opened stays
/// open across a rebuild of everything round it, and nothing in the model has to
/// know that.
final class CollapseState extends State<Collapse> {

    /// Only meaningful while the widget is uncontrolled. A controlled `collapse`
    /// reads its widget every build, so this is not consulted and not written.
    private boolean open;

    /// Where the body is in its arrival, or `SETTLED` when it has been open a
    /// while — and always `SETTLED` for a section that started open, because
    /// there was nothing to arrive from.
    ///
    /// There is **no departure**. §5 says the body is unmounted while closed, and
    /// keeping it alive for the length of a fade would be building a subtree that
    /// has just been asked to go away. Closing is therefore instant and opening is
    /// not, which is asymmetric on purpose: the thing worth animating is content
    /// appearing where there was none ([Phase], [ADR-0166]).
    private Phase arriving = new Phase(Phase.Kind.SETTLED);

    @Override
    protected void initState() {
        // The declared `open` is the *initial* state here, and the state itself
        // when the widget is controlled. Read once rather than on every build,
        // or reopening a section the document declared shut would be undone by
        // the next rebuild.
        open = widget().open();
    }

    /// A controlled section's answer comes from its widget; an uncontrolled one's
    /// from here.
    private boolean isOpen() {
        return widget().isControlled() ? widget().open() : open;
    }

    @Override
    public Widget build(BuildContext context) {
        var collapse = widget();
        var showing = isOpen();
        return new CollapseSection(
                collapse.title(), showing, this::toggle, showing ? this::visibility : null,
                // **The body is not built while it is shut.** Not built and
                // handed to something that hides it -- the list is empty, so the
                // element layer never mounts it, its bindings never subscribe,
                // and there is nothing alive behind a header nobody has opened
                // (§5, ADR-0004).
                showing ? collapse.children() : java.util.List.of(),
                collapse.attributes());
    }

    /// What the header asks for. The value goes *out* — the application is told
    /// what was asked for and answers by changing the widget — or is kept here
    /// when nobody is listening.
    private void toggle() {
        var next = !isOpen();
        if (next) {
            // Before the change, so the body's first frame is its first frame --
            // and set here rather than in `build` so that a controlled section,
            // whose `open` comes back from the application, animates too.
            arriving = new Phase(Phase.Kind.ENTERING);
        }
        if (widget().isControlled()) {
            widget().onToggle().accept(next);
            return;
        }
        setState(() -> open = next);
    }

    /// How far into its arrival the body is at `now`, `0..1`.
    ///
    /// Read from `render`, which is what stamps the beginning: a `State` never
    /// sees the frame clock.
    private double visibility(double now) {
        return arriving.progressAt(now);
    }
}
