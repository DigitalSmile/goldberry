# 130. A widget inflates itself

Date: 2026-08-19

## Status

Accepted. Relates to `docs/ARCHITECTURE.md` §9's inflater and its parity
invariant.

## Context

`Controls.inflater` was 300 lines of one shape:

```java
inflater.register("button", (node, children) -> new Button(
        node.argument().map(v -> v.asString()).orElse(""),
        icons.resolve(node.stringProperty("icon")),
        actions.resolve(node.stringProperty("press")),
        node.booleanProperty("disabled"),
        Attributes.of(node)));
```

nineteen times, in one file, none of which was near the widget it built.

Two costs. The first is that a widget's markup contract lived somewhere else than
the widget: `Button.java` documents what a button *is*, and what `button` means in
KDL was three hundred lines away in a file about the catalog. §9's parity
invariant says every built-in must be constructible in all three forms — Java,
KDL, CSS — and two of the three were in one file and the third in another.

The second is that the file was mostly repetition.
`node.argument().map(v -> v.asString()).orElse("")` appeared eight times.
`change == null ? null : value -> change.accept(String.valueOf(value))` appeared
three. Neither is a decision; they are the same sentence written out again, and
they made the parts that *did* differ hard to see.

## Decision

**Each widget gets a `static Widget inflate(KdlNode, List<Widget>, Wiring)`, and
`Controls` becomes a list of names.**

```java
catalog.add("button", Button::inflate);
catalog.add("checkbox", Checkbox::inflate);
…
```

`Wiring` is the three registries §9 asks for — [Actions], [Icons], [Bindings] —
travelling together, because a factory generally needs more than one of them and
threading three parameters through nineteen registrations was three chances to
pass the wrong one. It also carries the readings that were repeated:
`Wiring.label(node)`, `wiring.bound(node)`, `wiring.icon(node)`,
`wiring.numeric(node, "change")`.

`Inflatable.Catalog` binds one `Wiring` to an inflater so the table is names and
factories and nothing else. It is a class and not a `Map`, because the order names
are registered in is the order an unknown node is reported against — and that
list is the most useful thing an error message about a typo can say.

`Primitives` uses the same catalog, which is §9's "built-ins and application
widgets register identically" made literally true: there is no privileged path,
only a first caller.

## Consequences

`Controls.java` went from 443 lines to 204, and `Primitives` from 82 to 77. The
lines did not vanish — they moved next to the records they build, where each one
sits under a javadoc paragraph explaining the attribute it reads.

**Adding a widget is now a method and one line**, instead of a fifteen-line lambda
in a file about something else.

**The helpers moved with the bodies.** `requiredValue` and `colour` are on
`Wiring` because two widgets each need them; `readings` moved into `Hud`, which
was the only caller and where `Reading` already lived.

**A widget class now names `Wiring`**, and therefore `Icons` and `Actions`. That is
not new coupling — every one of these already took an `Observable` or a `Runnable`
in its constructor — but it does mean the widget package depends on the catalog
package, where before the arrow pointed one way. The alternative was a registry of
factories somewhere in between, which is a third place for a widget's markup
contract to live and was the problem to begin with.

**`inflate` is a static method, matched by convention rather than by a type.**
Java cannot require a static method on an interface, so nothing stops a new widget
from omitting one — except the parity test, which already fails when a widget is
not constructible from KDL, and which is why that test was worth having.
