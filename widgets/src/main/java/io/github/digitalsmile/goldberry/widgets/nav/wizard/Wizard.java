package io.github.digitalsmile.goldberry.widgets.nav.wizard;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.attr.Bindable;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// `steps` plus a content area plus an action bar — `docs/core-widgets.md` §6's
/// `wizard`, and the whole of it.
///
/// ```kdl
/// wizard bind="signup.step" back="signup.back" next="signup.next" finish="signup.finish" id="signup" {
///     page "Account" description="Who you are" { … }
///     page "Payment" { … }
///     page "Review" { … }
/// }
/// ```
///
/// ## It owns no policy, and that is the design
///
/// §6: "it owns *no* validation, no navigation policy and no data. Back/Next/
/// Finish raise events the application answers by moving the index, so a wizard
/// that refuses to advance is an application that did not move it." So `next`
/// is a request and not a step: the wizard reports it, the application decides
/// whether page two is valid yet, and `current` — through `bind` or written by
/// the application — is what moves. That is [ADR-0063]'s rule for every value
/// in this toolkit, and it is what makes a wizard's validation an ordinary
/// handler rather than a callback protocol.
///
/// ## The indicator is the standalone widget
///
/// "`steps` is a child widget rather than a drawing, so a wizard's indicator is
/// the standalone one and cannot drift from it." The wizard makes one
/// [io.github.digitalsmile.goldberry.widgets.nav.steps.Step] per page and hands
/// them to a [io.github.digitalsmile.goldberry.widgets.nav.steps.Steps]; what
/// `steps` learns, a wizard learns. A `clickable` wizard forwards a press on a
/// reachable step through `goTo`, which is the one way to move backwards two
/// pages at once.
///
/// ## Advancing moves the keyboard
///
/// "The content area is a `focus-scope`: advancing moves focus to the new
/// step's first control, because a keyboard user who pressed Next and stayed on
/// the button has not moved." So when the current index changes under a
/// mounted wizard, the state asks the host to focus the content area — which
/// [io.github.digitalsmile.goldberry.Host#focus] resolves to the first
/// focusable thing inside it — on a zero-delay timer, exactly as a dialog does
/// on opening. Not on the first build: a window opening on page one should not
/// take the keyboard from whatever the application put it on.
///
/// ## Platform button order
///
/// The bar is a dialog's: the buttons are written Back then Next, which is
/// dismissive-then-affirmative, and a theme that wants Windows' order writes
/// `wizard-actions { flex-direction: row-reverse }` — the same one declaration
/// `dialog-actions` takes.
///
/// ## This node styles nothing
///
/// `wizard` as a **CSS type** is [WizardPanel], the node the state builds
/// (ADR-0109).
///
/// @param children    the pages, as written; anything that is not a [WizardPage]
///                    is ignored
/// @param current     the current page's index when nothing is bound
/// @param source      §9's `bind` — read-only; a `Number` is read as the index
/// @param onBack      told when Back is pressed, or null for a wizard with no
///                    Back
/// @param onNext      told when Next is pressed, or null
/// @param onFinish    told when Finish is pressed, or null; Finish stands where
///                    Next would on the last page
/// @param goTo        told the index of a reachable step the user pressed on the
///                    indicator, or null for an indicator that is a picture
/// @param labels      what the three buttons say
/// @param attributes  `id` and `class`, exactly as on every other widget
@Markup("wizard")
public record Wizard(
        List<Widget> children,
        int current,
        @Nullable Observable<?> source,
        @Nullable Runnable onBack,
        @Nullable Runnable onNext,
        @Nullable Runnable onFinish,
        @Nullable IntConsumer goTo,
        Labels labels,
        Attributes attributes)
        implements Widget.Stateful, Attributed<Wizard>, Bindable<Wizard> {

    /// What the three buttons say. §6 names them Back, Next and Finish; an
    /// application in another language, or one whose last step is "Pay", says
    /// otherwise here.
    public record Labels(String back, String next, String finish) {

        /// §6's words.
        public static final Labels DEFAULT = new Labels("Back", "Next", "Finish");

        public Labels {
            Objects.requireNonNull(back, "back");
            Objects.requireNonNull(next, "next");
            Objects.requireNonNull(finish, "finish");
        }
    }

    public Wizard {
        children = List.copyOf(children == null ? List.of() : children);
        labels = labels == null ? Labels.DEFAULT : labels;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A wizard on page `current`, with nothing wired yet.
    public Wizard(int current, Widget... children) {
        this(List.of(children), current, null, null, null, null, null, Labels.DEFAULT, Attributes.NONE);
    }

    /// This wizard reporting Back.
    public Wizard onBack(@Nullable Runnable handler) {
        return new Wizard(children, current, source, handler, onNext, onFinish, goTo, labels, attributes);
    }

    /// This wizard reporting Next.
    public Wizard onNext(@Nullable Runnable handler) {
        return new Wizard(children, current, source, onBack, handler, onFinish, goTo, labels, attributes);
    }

    /// This wizard reporting Finish.
    public Wizard onFinish(@Nullable Runnable handler) {
        return new Wizard(children, current, source, onBack, onNext, handler, goTo, labels, attributes);
    }

    /// This wizard with a clickable indicator, reporting the index of a
    /// reachable step the user pressed.
    public Wizard goTo(@Nullable IntConsumer handler) {
        return new Wizard(children, current, source, onBack, onNext, onFinish, handler, labels, attributes);
    }

    /// This wizard's buttons saying something else.
    public Wizard labels(Labels value) {
        return new Wizard(children, current, source, onBack, onNext, onFinish, goTo, value, attributes);
    }

    @Override
    public Wizard bound(Observable<?> value) {
        return new Wizard(children, current, value, onBack, onNext, onFinish, goTo, labels, attributes);
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    @Override
    public Wizard withAttributes(Attributes value) {
        return new Wizard(children, current, source, onBack, onNext, onFinish, goTo, labels, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// The pages, in order — the children that are pages.
    public List<WizardPage> pages() {
        return children.stream()
                .filter(WizardPage.class::isInstance)
                .map(WizardPage.class::cast)
                .toList();
    }

    /// Which page is current: the bound value if there is one, the written one
    /// otherwise, clamped into the pages so a wizard is never on no page.
    public int resolvedCurrent() {
        var wanted = current;
        if (source != null) {
            wanted = source.get() instanceof Number number ? number.intValue() : 0;
        }
        var last = Math.max(0, pages().size() - 1);
        return Math.clamp(wanted, 0, last);
    }

    @Override
    public State<?> createState() {
        return new WizardState();
    }

    /// Builds a `wizard` from markup.
    ///
    /// `back`, `next` and `finish` are plain actions; `go-to` is numeric, since
    /// what the user pressed is an index. `back-label`, `next-label` and
    /// `finish-label` reword the buttons.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        Objects.requireNonNull(node, "node");
        var goTo = wiring.numeric(node, "go-to");
        var labels = new Labels(
                Objects.requireNonNullElse(node.stringProperty("back-label"), Labels.DEFAULT.back()),
                Objects.requireNonNullElse(node.stringProperty("next-label"), Labels.DEFAULT.next()),
                Objects.requireNonNullElse(node.stringProperty("finish-label"), Labels.DEFAULT.finish()));
        return new Wizard(
                children,
                (int) node.numberProperty("current", 0),
                wiring.bound(node),
                optional(node, "back", wiring),
                optional(node, "next", wiring),
                optional(node, "finish", wiring),
                goTo == null ? null : index -> goTo.accept(index),
                labels,
                Attributes.of(node));
    }

    /// An action the document may leave out — a wizard with no `back=` has no
    /// Back button rather than one that does nothing.
    private static @Nullable Runnable optional(KdlNode node, String attribute, Wiring wiring) {
        return node.stringProperty(attribute) == null ? null : wiring.action(node, attribute);
    }
}
