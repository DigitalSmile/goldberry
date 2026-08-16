package io.github.digitalsmile.goldberry.css;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// A [StyleElement] tree, built by hand.
///
/// Stands in for the element tree of ADR-0004, which does not exist yet. That is
/// exactly what [StyleElement] is for: the cascade can be built and tested
/// against something this small, and the real tree implements the same four
/// questions later.
final class TestElement implements StyleElement {

    private final String type;
    private String id;
    private final Set<String> classes = new LinkedHashSet<>();
    private final Set<Selector.PseudoClass> states = new LinkedHashSet<>();
    private final List<TestElement> children = new ArrayList<>();
    private TestElement parent;

    private TestElement(String type) {
        this.type = type;
    }

    /// `element("button.primary#apply")` — a tiny selector-shaped shorthand, so a
    /// test tree reads like the CSS it is being matched against.
    static TestElement element(String description) {
        var element = new TestElement(leadingType(description));
        var rest = description.substring(element.type == null ? 0 : element.type.length());
        var i = 0;
        while (i < rest.length()) {
            var marker = rest.charAt(i);
            var end = i + 1;
            while (end < rest.length() && rest.charAt(end) != '.' && rest.charAt(end) != '#'
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
        while (end < description.length() && Character.isLetterOrDigit(description.charAt(end))) {
            end++;
        }
        return end == 0 ? null : description.substring(0, end);
    }

    TestElement with(TestElement... kids) {
        for (var kid : kids) {
            kid.parent = this;
            children.add(kid);
        }
        return this;
    }

    /// The element at the end of a chain of first children — how a test names the
    /// leaf of a tree it just built.
    TestElement descend(int depth) {
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

    @Override
    public String toString() {
        return type == null ? "<anonymous>" : "<" + type + ">";
    }
}
