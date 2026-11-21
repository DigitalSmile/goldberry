package dev.goldberry.html.style;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.StyleElement;
import dev.goldberry.css.select.Selector;
import dev.goldberry.html.model.Element;

/// An element of a mail, as the toolkit's selector matcher sees an element.
///
/// **A mail's `<style>` is matched against the mail, not against the widgets it
/// becomes.** The widget tree is the fold's: an anchor is a button, a paragraph is
/// a row of words, a `tbody` is flattened away and an inline element is not a
/// node at all but a class on each of its words. A selector written for the
/// document, `ul > li` or `td a` or `p:first-child`, would match that tree wrongly
/// or not at all. So each element of the model is wrapped in one of these, the
/// matcher runs on the document's own shape, and what a rule decided is handed to
/// the widgets as a class.
///
/// Type, id, classes, parent, siblings and position come from the model; every
/// state is false, because a document is matched once, not as the pointer moves.
/// A rule whose subject is in a state is the exception, and [AuthorSheet] takes
/// the state off before it gets here.
final class StyleNode implements StyleElement {

    /// The element, or null for the document's own node above its top-level
    /// elements.
    private final @Nullable Element element;

    private final @Nullable StyleNode parent;
    private final Set<String> classes;
    private final List<StyleNode> children = new ArrayList<>();
    private int index;

    private StyleNode(@Nullable Element element, @Nullable StyleNode parent) {
        this.element = element;
        this.parent = parent;
        this.classes = element == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(element.classes()));
    }

    /// The node standing for the document itself: no type and nothing to match,
    /// there so that two top-level elements are siblings, as `h1 + p` needs.
    static StyleNode document() {
        return new StyleNode(null, null);
    }

    /// The node for `element`, as the next child of `parent`.
    static StyleNode child(Element element, @Nullable StyleNode parent) {
        var node = new StyleNode(element, parent);
        if (parent != null) {
            node.index = parent.children.size();
            parent.children.add(node);
        }
        return node;
    }

    @Override
    public @Nullable String type() {
        return element == null ? null : element.tag().toLowerCase(Locale.ROOT);
    }

    @Override
    public @Nullable String id() {
        return element == null ? null : element.id();
    }

    @Override
    public Set<String> classes() {
        return classes;
    }

    @Override
    public @Nullable StyleElement parent() {
        return parent;
    }

    @Override
    public boolean hasState(Selector.PseudoClass state) {
        // `:root` is the one state a document has: being its outermost element.
        return state == Selector.PseudoClass.ROOT && parent != null && parent.element == null;
    }

    /// Where this node sits among its parent's element children.
    ///
    /// **Only the ones built so far**, and that is enough: an element is matched as
    /// the walk reaches it, when every sibling before it exists and none after it
    /// does — and `:first-child`, `+` and `~` look only backwards. `:last-child`
    /// and `:nth-last-child` look forwards and so never match, which is the price
    /// of matching in one pass.
    @Override
    public int indexInParent() {
        return index;
    }

    @Override
    public int siblingCount() {
        return parent == null ? 1 : Integer.MAX_VALUE;
    }

    @Override
    public List<? extends StyleElement> siblings() {
        return parent == null ? List.of(this) : List.copyOf(parent.children);
    }

    @Override
    public List<? extends StyleElement> children() {
        return List.copyOf(children);
    }

    @Override
    public int indexOfType() {
        if (parent == null) {
            return 0;
        }
        var count = 0;
        for (var sibling : parent.children) {
            if (sibling == this) {
                return count;
            }
            if (Objects.equals(sibling.type(), type())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int typeCount() {
        return parent == null ? 1 : Integer.MAX_VALUE;
    }
}
