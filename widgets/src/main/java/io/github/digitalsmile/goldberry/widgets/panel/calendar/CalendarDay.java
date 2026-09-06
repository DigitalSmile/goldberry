package io.github.digitalsmile.goldberry.widgets.panel.calendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One day in the grid — `calendar-day`, a **part**: styleable and not
/// constructible (ADR-0065).
///
/// **Not focusable**, and it says so by saying nothing: `Handles.isFocusable`
/// already answers false, and `SemanticsSweepTest` reads an *override* of it as a
/// type making focusability its business — which then owes a role. This does not
/// want one. §10: "The grid is one Tab stop with a roving day (§2.2)", so
/// [CalendarBox] takes the keys and this wears the roving highlight as a class;
/// forty-two focusable cells would be forty-two Tab stops in a control the
/// specification calls one.
///
/// ## The classes, and why each is a class
///
/// §8's subset has `:checked` and `:disabled` and nothing that means "this day is
/// in the month the grid is of", "this day is the one the arrows are on" or "this
/// day is inside a range". So `outside`, `today`, `roving`, `range-start`,
/// `range-end` and `in-range` are classes, exactly as `text-value`'s
/// `placeholder` is and for the same stated reason. The two that *are*
/// pseudo-classes are pseudo-classes: a chosen day is `:checked`, which is the
/// same fact about a set that a `radio`, an `option` and a `tab` report, and an
/// unreachable one is `:disabled`.
///
/// **A range's ends are `:checked` and its middle is not**, which is what lets
/// §2's "radius `full` on the selected day, range ends only" be one rule rather
/// than a rule and an exception.
///
/// @param date       which day this is
/// @param label      what to draw — the day of the month
/// @param inMonth    whether it belongs to the month the grid is of
/// @param today      whether it is today
/// @param chosen     whether it is one of the selected dates, or a range end
/// @param rangeStart whether it is the start of a range
/// @param rangeEnd   whether it is the end of a range
/// @param inRange    whether it falls strictly inside a range
/// @param roving     whether the keyboard is on it
/// @param disabled   whether it is unreachable — outside `min`/`max`, or refused
/// @param decoration what the application draws on it, or null
/// @param onPress    told this date when it is clicked
record CalendarDay(
        LocalDate date,
        String label,
        boolean inMonth,
        boolean today,
        boolean chosen,
        boolean rangeStart,
        boolean rangeEnd,
        boolean inRange,
        boolean roving,
        boolean disabled,
        @Nullable Widget decoration,
        Consumer<LocalDate> onPress)
        implements Widget.Leaf, Styled, Paints, Handles {

    @Override
    public String cssType() {
        return "calendar-day";
    }

    @Override
    public Set<String> classes() {
        var classes = new ArrayList<String>(4);
        if (!inMonth) {
            classes.add("outside");
        }
        if (today) {
            classes.add("today");
        }
        if (rangeStart) {
            classes.add("range-start");
        }
        if (rangeEnd) {
            classes.add("range-end");
        }
        if (inRange) {
            classes.add("in-range");
        }
        if (roving) {
            classes.add("roving");
        }
        return Set.copyOf(classes);
    }

    @Override
    public boolean isChecked() {
        return chosen;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    /// A press and not a click, for `text-input`'s reason turned round: a day is
    /// chosen the moment the button goes down in every calendar anybody has used,
    /// and waiting for the release makes a picker feel like it is thinking.
    @Override
    public void onPointer(PointerEvent event) {
        if (disabled || event.kind() != PointerEvent.Kind.PRESSED) {
            return;
        }
        if (event.button() != PointerEvent.Button.PRIMARY) {
            return;
        }
        onPress.accept(date);
        event.consume();
    }

    /// The number, and whatever the application put under it.
    ///
    /// The decoration is a **child** rather than a replacement, which is what
    /// makes §10's "a dot, a `badge`, a background" one seam instead of three: a
    /// dot is a child under the number, a badge is a child beside it, and a
    /// background is a class the application's own stylesheet reaches through
    /// `decoration` returning a node with one.
    @Override
    public List<Widget> children() {
        return decoration == null ? List.of() : List.of(decoration);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var boxes = new ArrayList<Box>(2);
        boxes.add(Box.text(context.paragraph(style, label), style.color()));
        boxes.addAll(children);
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
