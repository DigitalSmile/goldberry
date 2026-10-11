package dev.goldberry.css.container;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.StyleElement;
import dev.goldberry.css.media.MediaCondition;
import dev.goldberry.css.media.MediaContext;
import dev.goldberry.css.value.Viewport;

/// One `@container` prelude: an optional name and a size condition.
///
/// ```css
/// @container sidebar (min-width: 320px) { … }
/// @container (orientation: portrait) { … }
/// ```
///
/// Answered for an element against its **query container**: the nearest
/// ancestor that is a container for the axes the condition reads, and has the
/// name when one is written. With none, or with one not laid out yet, the
/// query does not hold.
///
/// @param name      the `container-name` asked for, or null for any
/// @param condition the size condition, in the features a media query has:
///                  `width`, `height`, `aspect-ratio` and `orientation`
///                  (`inline-size` and `block-size` read as width and height)
/// @param block     whether the condition reads the block axis, which an
///                  `inline-size` container cannot answer
public record ContainerQuery(@Nullable String name, MediaCondition condition, boolean block) {

    public ContainerQuery {
        Objects.requireNonNull(condition, "condition");
    }

    /// Whether this holds for `element`.
    public boolean holds(StyleElement element) {
        Objects.requireNonNull(element, "element");
        var container = queryContainer(element);
        return container != null && condition.matches(MediaContext.UNKNOWN.size(container.width(), container.height()));
    }

    /// The container this asks about for `element`, or null when no ancestor
    /// qualifies.
    public @Nullable QueryContainer queryContainer(StyleElement element) {
        return nearest(element, name, block);
    }

    /// The nearest ancestor of `element` that is a container, with `name`
    /// when one is given and answering for the block axis when `block`.
    public static @Nullable QueryContainer nearest(StyleElement element, @Nullable String name, boolean block) {
        for (var ancestor = element.parent(); ancestor != null; ancestor = ancestor.parent()) {
            var container = ancestor.queryContainer();
            if (container == null) {
                continue;
            }
            if (block && container.type() != ContainerType.SIZE) {
                continue;
            }
            if (name != null && !container.names().contains(name)) {
                continue;
            }
            return container;
        }
        return null;
    }

    /// The size the container units of `element` are a percentage of: its
    /// nearest container's on each axis it answers for, NaN on the others,
    /// where [dev.goldberry.css.value.CssLength] falls back to the window's as
    /// CSS falls back to the small viewport.
    public static Viewport unitsBasis(StyleElement element, Viewport viewport) {
        var container = nearest(element, null, false);
        if (container == null) {
            return viewport;
        }
        var block = container.type() == ContainerType.SIZE ? container.height() : Double.NaN;
        return new Viewport(container.width(), block);
    }

    @Override
    public String toString() {
        return (name == null ? "" : name + " ") + condition;
    }
}
