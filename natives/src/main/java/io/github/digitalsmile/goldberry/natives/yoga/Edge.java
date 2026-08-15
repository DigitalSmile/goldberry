package io.github.digitalsmile.goldberry.natives.yoga;

/// Which side an edge-valued style applies to — `YGEdge`.
///
/// Margin, padding, border and inset are all set per edge, and the shorthands
/// are edges too: setting [#ALL] and then [#LEFT] leaves the left side with the
/// more specific value, which is how CSS's `padding: 4px; padding-left: 8px`
/// compiles without the CSS layer resolving the shorthand itself.
///
/// [#START] and [#END] are the direction-relative pair. They resolve to left and
/// right under [Direction#LTR] and swap under [Direction#RTL], which is the
/// entire mechanism behind mirrored layouts — a widget that uses them is
/// bidi-correct without knowing it.
public enum Edge implements YogaEnum {

    LEFT(0, "YGEdgeLeft"),

    TOP(1, "YGEdgeTop"),

    RIGHT(2, "YGEdgeRight"),

    BOTTOM(3, "YGEdgeBottom"),

    /// The leading edge in the resolved writing direction.
    START(4, "YGEdgeStart"),

    /// The trailing edge in the resolved writing direction.
    END(5, "YGEdgeEnd"),

    /// [#LEFT] and [#RIGHT] together.
    HORIZONTAL(6, "YGEdgeHorizontal"),

    /// [#TOP] and [#BOTTOM] together.
    VERTICAL(7, "YGEdgeVertical"),

    /// Every edge. The least specific of the shorthands.
    ALL(8, "YGEdgeAll");

    private final int nativeValue;
    private final String nativeName;

    Edge(int nativeValue, String nativeName) {
        this.nativeValue = nativeValue;
        this.nativeName = nativeName;
    }

    @Override
    public int nativeValue() {
        return nativeValue;
    }

    @Override
    public String nativeName() {
        return nativeName;
    }

    /// Whether this edge names exactly one side.
    ///
    /// The computed-layout getters only answer for a single physical side —
    /// asking Yoga for the padding of [#ALL] is a question with no answer, and
    /// Yoga returns zero rather than saying so.
    boolean isPhysicalSide() {
        return switch (this) {
            case LEFT, TOP, RIGHT, BOTTOM -> true;
            case START, END, HORIZONTAL, VERTICAL, ALL -> false;
        };
    }
}
