package io.github.digitalsmile.goldberry.widgets.panel.carousel;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntConsumer;

/// **This is the `carousel` a stylesheet selects.**
///
/// [Carousel] is stateful and styles nothing, so this node carries the CSS type
/// and the document's `id` and classes.
///
/// ## One tab stop, and the arrows move between slides
///
/// §5: "Arrows move between slides when the strip is focused; slides are one Tab
/// stop and their content is reachable inside." So this node is focusable and its
/// scope is [FocusScope#HORIZONTAL] — the arrows along the strip move slides, and
/// `Tab` goes on into the current slide's own controls rather than round the
/// carousel's.
///
/// @param index          which slide is showing
/// @param count          how many there are, for the dots
/// @param loop           whether the ends wrap, which decides the two buttons
/// @param rotates        whether a rotation exists at all, for `.rotating`
/// @param canGoBack      whether `Previous` does anything
/// @param canGoForward   whether `Next` does
/// @param slide          the current slide, or null when there are none
/// @param attributes     the document's `id` and classes
/// @param onGo           go to a slide by number — a dot
/// @param onStep         move by one — a button, an arrow, or the rotation
/// @param onHover        the pointer arrived or left
/// @param onFocus        the strip or a control took focus, or gave it up
/// @param onMotion       what the frame says about the motion preference
/// @param visibility     how far into its arrival the current slide is at a given
///                       frame time, `0..1`. Reading it is also what *starts* the
///                       arrival, because `render` is the only place a widget is
///                       given the clock ([ADR-0109], [Phase])
/// @param direction      which way the last move went, `+1` forwards; the arriving
///                       slide translates in from that side
record CarouselView(
        int index, int count, boolean loop, boolean rotates,
        boolean canGoBack, boolean canGoForward, Widget slide, Attributes attributes,
        IntConsumer onGo, IntConsumer onStep, Consumer<Boolean> onHover,
        Consumer<Boolean> onFocus, Consumer<Boolean> onMotion,
        DoubleUnaryOperator visibility, int direction)
        implements Widget.Leaf, Styled, Paints, Handles {

    @Override
    public String cssType() {
        return "carousel";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    /// `.rotating` while a rotation exists, which is what lets a stylesheet show
    /// that this one moves on its own — the difference between a carousel you
    /// have to drive and one that is driving itself is worth being able to see.
    @Override
    public Set<String> classes() {
        if (!rotates) {
            return attributes.classes();
        }
        var all = new java.util.LinkedHashSet<>(attributes.classes());
        all.add("rotating");
        return Set.copyOf(all);
    }

    @Override
    public boolean isFocusable() {
        return count > 1;
    }

    @Override
    public FocusScope focusScope() {
        return FocusScope.HORIZONTAL;
    }

    /// Focus on the strip pauses the rotation.
    ///
    /// Focus **inside a slide** does not, which is §5's "on focus anywhere
    /// inside" and is not built: the cascade has no `:focus-within` and nothing
    /// tells a widget that focus landed in its subtree. See [Carousel].
    @Override
    public void onFocusChanged(boolean focused, boolean fromKeyboard) {
        onFocus.accept(focused);
    }

    /// Hover pauses it, which is the half that works completely — and it is the
    /// half that matters most, because reading a slide means having the pointer
    /// somewhere near it.
    @Override
    public void onPointer(PointerEvent event) {
        switch (event.kind()) {
            case ENTERED -> onHover.accept(true);
            case EXITED -> onHover.accept(false);
            default -> {
            }
        }
    }

    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || !event.modifiers().none()) {
            return;
        }
        switch (event.key()) {
            case LEFT -> step(event, -1);
            case RIGHT -> step(event, 1);
            case HOME -> {
                onGo.accept(0);
                event.consume();
            }
            case END -> {
                onGo.accept(count - 1);
                event.consume();
            }
            default -> {
            }
        }
    }

    private void step(KeyEvent event, int by) {
        onStep.accept(by);
        // Consumed even at an end with no loop, where nothing moved: the key
        // belongs to the carousel whether or not it had anywhere to go, and
        // letting it bubble would scroll whatever the carousel is sitting in.
        event.consume();
    }

    /// The viewport with the current slide in it, the two buttons, and the dots.
    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(3);
        parts.add(new CarouselViewport(slide, visibility, direction));
        parts.add(new CarouselControls(
                canGoBack, canGoForward, () -> onStep.accept(-1), () -> onStep.accept(1),
                onFocus));
        if (count > 1) {
            parts.add(new CarouselDots(index, count, onGo, onFocus));
        }
        return List.copyOf(parts);
    }

    /// Reports the motion preference on the way past, which is the only place a
    /// `Paints.Context` exists — a `State` cannot ask for one.
    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        onMotion.accept(context.reducedMotion());
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// What the current slide is drawn in — a node of its own so a stylesheet can
    /// clip it and give it a height without touching whatever the author put in,
    /// and so the **arrival** has somewhere to live: the animation belongs to the
    /// viewport rather than to the slide, because the slide is the author's widget
    /// and a carousel must not reach inside it.
    record CarouselViewport(Widget slide, DoubleUnaryOperator visibility, int direction)
            implements Widget.Leaf, Styled, Paints {

        /// How far the arriving slide travels, in logical pixels.
        ///
        /// Larger than a `tab`'s 6, because a carousel is *about* the movement
        /// between slides where a tab's arrival is a detail — and small enough
        /// that it is a settle rather than a swipe. §1.7 has no token for this;
        /// a clock-driven animation cannot read a `transition` declaration
        /// because it is not one (ADR-0109).
        private static final double TRAVEL = 24;

        @Override
        public String cssType() {
            return "carousel-viewport";
        }

        @Override
        public List<Widget> children() {
            return slide == null ? List.of() : List.of(slide);
        }

        /// Whether an arrival is still running, which is what keeps the frame
        /// loop awake for the length of one.
        ///
        /// A carousel that has been sitting on one slide animates nothing and
        /// asks for nothing, so a window with one in it is as idle as a window
        /// without.
        @Override
        public boolean isAnimating() {
            return visibility != null;
        }

        /// **Opacity and a translation, and nothing else** — §1.7's whitelist is
        /// the compositor-cheap set, and a viewport that animated its own width
        /// would run Yoga on every frame of every move.
        ///
        /// Under reduced motion there is no animation at all: the slide is simply
        /// there. §1.7 asks for movement to be removed rather than shortened.
        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            var box = Box.of().style(style).children(boxes.toArray(Box[]::new));
            if (visibility == null) {
                return box;
            }
            // Reading it is what starts the arrival: the phase is stamped from the
            // frame clock on its first read, and this is the only place there is
            // one.
            var visible = context.reducedMotion() ? 1 : visibility.applyAsDouble(
                    context.nowMillis());
            if (visible >= 1) {
                return box;
            }
            // Going forwards, the new slide comes in from the right — content
            // moves leftwards, which is the direction a reader's eye is already
            // travelling.
            var offset = (1 - visible) * TRAVEL * direction;
            return box.opacity(visible)
                    .transform(Transform.of(new Transform.Function.Translate(
                            Transform.Length.px(offset), Transform.Length.ZERO)));
        }
    }

    /// `Previous` and `Next`.
    record CarouselControls(
            boolean canGoBack, boolean canGoForward, Runnable onPrevious, Runnable onNext,
            Consumer<Boolean> onFocus)
            implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "carousel-controls";
        }

        @Override
        public List<Widget> children() {
            return List.of(
                    new CarouselStep(false, canGoBack, onPrevious, onFocus),
                    new CarouselStep(true, canGoForward, onNext, onFocus));
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }

    /// One of the two step buttons.
    ///
    /// Disabled at an end rather than absent, so the row does not change width as
    /// the carousel moves — and because a disabled `Next` is what says "that is
    /// all of them", which is the reason `loop` is off by default.
    record CarouselStep(
            boolean forward, boolean enabled, Runnable onPress, Consumer<Boolean> onFocus)
            implements Widget.Leaf, Styled, Paints, Handles {

        @Override
        public String cssType() {
            return "carousel-step";
        }

        @Override
        public Set<String> classes() {
            return Set.of(forward ? "next" : "previous");
        }

        @Override
        public boolean isDisabled() {
            return !enabled;
        }

        @Override
        public boolean isFocusable() {
            return enabled;
        }

        @Override
        public void onFocusChanged(boolean focused, boolean fromKeyboard) {
            onFocus.accept(focused);
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (enabled && event.kind() == PointerEvent.Kind.CLICKED) {
                onPress.run();
                event.consume();
            }
        }

        @Override
        public void onKey(KeyEvent event) {
            if (!enabled || event.kind() != KeyEvent.Kind.PRESSED || event.isRepeat()
                    || !event.modifiers().none()) {
                return;
            }
            if (event.key() == Key.ENTER || event.key() == Key.SPACE) {
                onPress.run();
                event.consume();
            }
        }

        /// A chevron, for `ItemChevron`'s reason: an icon owns native memory that
        /// must be closed exactly once, and a widget is a value rebuilt every
        /// frame.
        ///
        /// **The same mark for both buttons**, turned half round by the
        /// stylesheet for `Previous`. There is no `CHEVRON_START` and adding one
        /// would be a second kind that has to stay the mirror of the first
        /// forever — where a `transform` is one declaration and cannot drift.
        /// `collapse-chevron` makes the same trade for the same reason.
        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style)
                    .mark(new Box.Mark(Box.Mark.Kind.CHEVRON_END, style.color(), 1.5));
        }
    }

    /// The position indicator — §5's "a dot indicator".
    record CarouselDots(int index, int count, IntConsumer onGo, Consumer<Boolean> onFocus)
            implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "carousel-dots";
        }

        @Override
        public List<Widget> children() {
            var dots = new ArrayList<Widget>(count);
            for (var slide = 0; slide < count; slide++) {
                var target = slide;
                dots.add(new CarouselDot(slide == index, () -> onGo.accept(target), onFocus));
            }
            return List.copyOf(dots);
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }

    /// One dot.
    ///
    /// **Not focusable.** A carousel of nine slides would otherwise be nine tab
    /// stops on top of the two buttons, which is `tab`'s close-button argument
    /// (ADR-0107): the keyboard already reaches every slide through the arrows,
    /// and the dots are a pointer affordance and a position readout.
    record CarouselDot(boolean current, Runnable onPress, Consumer<Boolean> onFocus)
            implements Widget.Leaf, Styled, Paints, Handles {

        @Override
        public String cssType() {
            return "carousel-dot";
        }

        @Override
        public Set<String> classes() {
            return current ? Set.of("current") : Set.of();
        }

        /// Mirrored to `:checked`, so "this is the slide showing" is a state a
        /// stylesheet selects rather than a second drawing.
        @Override
        public boolean isChecked() {
            return current;
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.CLICKED) {
                onPress.run();
                event.consume();
            }
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style);
        }
    }
}
