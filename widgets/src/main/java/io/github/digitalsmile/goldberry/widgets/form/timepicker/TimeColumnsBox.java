package io.github.digitalsmile.goldberry.widgets.form.timepicker;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The node a stylesheet calls `time-columns`, and everything that needs a frame.
///
/// ## What it is made of
///
/// ```
/// time-columns          this node. Takes every key the popover is sent
/// └── time-column ×1..3 [TimeColumn] — a wheel of numbers
///     └── time-cell ×5  [TimeCell]
/// ```
///
/// ## One Tab stop, and the arrows split by axis
///
/// `calendar`'s arrangement and for its reason: the cells are parts, this is the
/// one node that hears anything, and the roving position is a class. What differs
/// is what the arrows mean. A calendar is a grid and all four move a *cell*; a
/// column set is a row of independent wheels, so **`Up`/`Down` change a value and
/// `Left`/`Right` change which column** — which is what a segmented pair of
/// spinners does everywhere, and is why §4 could give the two pickers the same
/// sentence ("arrows move within the grid") and mean different things by it.
///
/// `Home` and `End` are the ends of the **column**, not of the row: `00` and `23`
/// on the hours, `00` and `59` on the minutes. A row has three items and reaching
/// its ends is what `Left` and `Right` already do in one press.
///
/// @param columns   the wheels, in order
/// @param disabled  whether it refuses everything
/// @param keys      what to tell about a key
record TimeColumnsBox(List<Widget> columns, boolean disabled, TimeKeys keys)
        implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    TimeColumnsBox {
        columns = List.copyOf(columns);
    }

    @Override
    public String cssType() {
        return "time-columns";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public List<Widget> children() {
        return columns;
    }

    /// Every key it understands is consumed, `calendar`'s reason unchanged: a
    /// popover with the keyboard owns its arrows, or `Left` would walk the focus
    /// scope of the window underneath it.
    ///
    /// `Escape` is deliberately absent — it belongs to the popup, which the
    /// launcher dismisses before anything here is reached (ADR-0233).
    @Override
    public void onKey(KeyEvent event) {
        if (disabled || event.kind() != KeyEvent.Kind.PRESSED) {
            return;
        }
        var modifiers = event.modifiers();
        if (modifiers.control() || modifiers.alt() || modifiers.shift()) {
            return;
        }
        var handled =
                switch (event.key()) {
                    case UP -> keys.step(-1);
                    case DOWN -> keys.step(1);
                    case LEFT -> keys.moveColumn(-1);
                    case RIGHT -> keys.moveColumn(1);
                    case HOME -> keys.toColumnEdge(true);
                    case END -> keys.toColumnEdge(false);
                    case ENTER, SPACE -> keys.commit();
                    default -> false;
                };
        if (handled) {
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// A group and not a grid: the columns are independent wheels rather than
    /// cells addressed by row and column, which is exactly the distinction
    /// [Role#GRID]'s javadoc draws.
    @Override
    public Role role() {
        return Role.GROUP;
    }

    @Override
    public @Nullable String accessibleName() {
        return null;
    }

    /// What a key means, answered by the state that holds the wheels.
    interface TimeKeys {

        /// `Up`/`Down` — the roving column's value, by `delta`, wrapping.
        boolean step(int delta);

        /// `Left`/`Right` — which column the keyboard is on.
        boolean moveColumn(int delta);

        /// `Home`/`End` — the first or last value of the roving column.
        boolean toColumnEdge(boolean first);

        /// `Enter`/`Space`, which reports what the wheels currently say.
        boolean commit();
    }
}
