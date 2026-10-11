package dev.goldberry.css.select;

import dev.goldberry.css.StyleElement;

/// Where an element sits among its siblings: among all of them, and among those
/// of its own type. What a [Structural] pseudo-class is asked about.
///
/// @param index       the element's position among its parent's children,
///                    from 0
/// @param count       how many children its parent has, the element included
/// @param indexOfType its position among the children of its own type, from 0
/// @param typeCount   how many of its parent's children share its type
public record SiblingPosition(int index, int count, int indexOfType, int typeCount) {

    /// A root, or an only child: first and last of everything.
    public static final SiblingPosition ALONE = new SiblingPosition(0, 1, 0, 1);

    public SiblingPosition {
        if (index < 0 || index >= count || indexOfType < 0 || indexOfType >= typeCount || typeCount > count) {
            throw new IllegalArgumentException("not a position among siblings: " + index + " of " + count + ", "
                    + indexOfType + " of " + typeCount + " of its type");
        }
    }

    /// Where `element` says it is.
    public static SiblingPosition of(StyleElement element) {
        return new SiblingPosition(
                element.indexInParent(), element.siblingCount(), element.indexOfType(), element.typeCount());
    }
}
