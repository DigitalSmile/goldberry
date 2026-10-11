package dev.goldberry.css;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.goldberry.css.container.ContainerType;
import dev.goldberry.css.container.QueryContainer;
import dev.goldberry.css.select.PseudoElement;
import dev.goldberry.css.select.Selector;

/// A [StyleElement] tree, built by hand.
///
/// Public because the CSS engine's stages are packages of their own and each of
/// them tests against this same hand-built tree.
///
/// Stands in for the real element tree. That is
/// exactly what [StyleElement] is for: the cascade can be built and tested
/// against something this small, and the real tree implements the same four
/// questions later.
public final class TestElement implements StyleElement {

    private final String type;
    private String id;
    private final Set<String> classes = new LinkedHashSet<>();
    private final Set<Selector.PseudoClass> states = new LinkedHashSet<>();
    private final List<TestElement> children = new ArrayList<>();
    private TestElement parent;
    private PseudoElement pseudoElement;
    private QueryContainer container;

    private TestElement(String type) {
        this.type = type;
    }

    /// `element("button.primary#apply")` — a tiny selector-shaped shorthand, so a
    /// test tree reads like the CSS it is being matched against.
    public static TestElement element(String description) {
        var element = new TestElement(leadingType(description));
        var rest = description.substring(element.type == null ? 0 : element.type.length());
        var i = 0;
        while (i < rest.length()) {
            var marker = rest.charAt(i);
            var end = i + 1;
            while (end < rest.length()
                    && rest.charAt(end) != '.'
                    && rest.charAt(end) != '#'
                    && rest.charAt(end) != ':') {
                end++;
            }
            var name = rest.substring(i + 1, end);
            switch (marker) {
                case '.' -> element.classes.add(name);
                case '#' -> element.id = name;
                case ':' -> element.states.add(Selector.PseudoClass.parse(name));
                default -> throw new IllegalArgumentException("bad element description: " + description);
            }
            i = end;
        }
        return element;
    }

    private static String leadingType(String description) {
        var end = 0;
        while (end < description.length()
                && (Character.isLetterOrDigit(description.charAt(end)) || description.charAt(end) == '-')) {
            end++;
        }
        return end == 0 ? null : description.substring(0, end);
    }

    public TestElement with(TestElement... kids) {
        for (var kid : kids) {
            kid.parent = this;
            children.add(kid);
        }
        return this;
    }

    /// This element as a query container laid out at `width` by `height`.
    public TestElement container(ContainerType type, double width, double height, String... names) {
        this.container = new QueryContainer(type, List.of(names), width, height);
        return this;
    }

    @Override
    public QueryContainer queryContainer() {
        return container;
    }

    /// This element, standing for `pseudo`: a part a `::placeholder` selects.
    public TestElement standingFor(PseudoElement pseudo) {
        this.pseudoElement = pseudo;
        return this;
    }

    @Override
    public PseudoElement pseudoElement() {
        return pseudoElement;
    }

    /// The element at the end of a chain of first children — how a test names the
    /// leaf of a tree it just built.
    public TestElement descend(int depth) {
        var current = this;
        for (var i = 0; i < depth; i++) {
            current = current.children.getFirst();
        }
        return current;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<String> classes() {
        return classes;
    }

    @Override
    public StyleElement parent() {
        return parent;
    }

    @Override
    public boolean hasState(Selector.PseudoClass state) {
        return states.contains(state);
    }

    /// Its place among the children its parent was built [#with], which is what
    /// the structural pseudo-classes read.
    @Override
    public int indexInParent() {
        return parent == null ? 0 : parent.children.indexOf(this);
    }

    @Override
    public int siblingCount() {
        return parent == null ? 1 : parent.children.size();
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
        var index = 0;
        for (var sibling : parent.children) {
            if (sibling == this) {
                return index;
            }
            if (Objects.equals(sibling.type, type)) {
                index++;
            }
        }
        return index;
    }

    @Override
    public int typeCount() {
        return parent == null
                ? 1
                : (int) parent.children.stream()
                        .filter(sibling -> Objects.equals(sibling.type, type))
                        .count();
    }

    @Override
    public String toString() {
        return type == null ? "<anonymous>" : "<" + type + ">";
    }
}
