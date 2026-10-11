package dev.goldberry.css.property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;

/// The property table: every name the engine reads, looked up by name.
///
/// ```java
/// Properties.expand("padding");                  // padding-top, -right, -bottom, -left
/// Properties.copy("padding", parent, style);     // what padding: inherit means
/// Properties.longhand("color").orElseThrow().inherited(); // true
/// ```
///
/// Built once and never changed. Custom properties (`--name`) are not in it:
/// the cascade resolves them before any property is read.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#properties).
public final class Properties {

    private static final Map<String, Property> BY_NAME = index();
    private static final List<Longhand> LONGHANDS = BY_NAME.values().stream()
            .filter(Longhand.class::isInstance)
            .map(Longhand.class::cast)
            .toList();

    private Properties() {}

    /// The property called `name`, in any case.
    public static Optional<Property> find(String name) {
        return Optional.ofNullable(
                BY_NAME.get(Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT)));
    }

    /// The longhand called `name`, or empty when `name` is a shorthand, an alias
    /// or nothing at all.
    public static Optional<Longhand> longhand(String name) {
        return find(name).filter(Longhand.class::isInstance).map(Longhand.class::cast);
    }

    /// Whether a declaration called `name` is one the engine reads.
    public static boolean isReadable(String name) {
        return find(name).map(Property::readable).orElse(false);
    }

    /// Every name in the table, in the order the table lists them.
    public static Set<String> names() {
        return BY_NAME.keySet();
    }

    /// Every longhand, in the order the table lists them.
    public static List<Longhand> longhands() {
        return LONGHANDS;
    }

    /// The longhands `name` stands for: itself for a longhand, the longhands of
    /// its target for an alias, and for a shorthand the longhands of each name
    /// it writes, in order.
    ///
    /// @throws IllegalArgumentException if `name` is not in the table
    public static List<Longhand> expand(String name) {
        var property = find(name).orElseThrow(() -> new IllegalArgumentException("no property \"" + name + "\""));
        var into = new ArrayList<Longhand>();
        expand(property, into);
        return List.copyOf(into);
    }

    /// `into`, with every longhand `name` stands for taken from `from`.
    ///
    /// @throws IllegalArgumentException if `name` is not in the table
    public static ComputedStyle copy(String name, ComputedStyle from, ComputedStyle into) {
        Objects.requireNonNull(from, "from");
        var style = Objects.requireNonNull(into, "into");
        for (var longhand : expand(name)) {
            style = longhand.copy(from, style);
        }
        return style;
    }

    private static void expand(Property property, List<Longhand> into) {
        switch (property) {
            case Longhand longhand -> into.add(longhand);
            case Inert _ -> {}
            case Alias alias -> expand(indexed(alias.target()), into);
            case Shorthand shorthand -> {
                for (var name : shorthand.longhands()) {
                    expand(indexed(name), into);
                }
            }
        }
    }

    /// A name the table was checked to hold when it was built.
    private static Property indexed(String name) {
        return Objects.requireNonNull(BY_NAME.get(name), name);
    }

    /// The table by name, checked as it is built: no name twice, and every name
    /// a shorthand or an alias points at is in it.
    private static Map<String, Property> index() {
        var all = new ArrayList<Property>();
        all.addAll(Longhands.all());
        all.addAll(Shorthands.all());
        all.addAll(Shorthands.aliases());
        all.addAll(Shorthands.inert());
        all.addAll(LogicalProperties.table());
        var byName = new LinkedHashMap<String, Property>();
        for (var property : all) {
            if (byName.putIfAbsent(property.name(), property) != null) {
                throw new IllegalStateException("\"" + property.name() + "\" is in the property table twice");
            }
        }
        for (var property : all) {
            switch (property) {
                case Longhand _, Inert _ -> {}
                case Alias alias -> requireTarget(byName, alias.name(), alias.target());
                case Shorthand shorthand -> {
                    for (var name : shorthand.longhands()) {
                        requireTarget(byName, shorthand.name(), name);
                    }
                }
            }
        }
        return Collections.unmodifiableMap(byName);
    }

    /// Refuses a shorthand or alias whose `target` is missing, or is an alias:
    /// an alias of an alias is a chain nobody needs to follow.
    private static void requireTarget(Map<String, Property> byName, String owner, String target) {
        switch (byName.get(target)) {
            case Longhand _, Shorthand _, Inert _ -> {}
            case Alias _ -> throw new IllegalStateException(owner + " points at " + target + ", which is an alias");
            case null -> throw new IllegalStateException(owner + " points at " + target + ", which is not a property");
        }
    }
}
