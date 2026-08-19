# 131. A widget package announces itself

Date: 2026-08-19

## Status

Accepted. Replaces the hand-written catalog
[ADR-0130](0130-a-widget-inflates-itself.md) left in `Controls`.

## Context

ADR-0130 moved each widget's markup contract next to the widget and left
`Controls.inflater` as a table of nineteen lines:

```java
catalog.add("button", Button::inflate);
catalog.add("checkbox", Checkbox::inflate);
…
```

Better than three hundred lines of construction, and still wrong for the question
actually being asked, which was: *there will be dozens of widget packages — how
does an application wire them all?*

Under the table, it does it by hand. `Controls.inflater` knows exactly the
widgets `:widgets` happens to ship. A second module — charts, an editor, a
platform-specific control set — has its own `inflater`, and an application
wanting both merges two registries and keeps the merge in step with both. That is
the same copying ADR-0096 removed from binding, in a different place.

`Primitives` already showed the shape of the problem inside one module: two
inflaters, and every caller wanting `column` *and* `button` had to know that
`Controls.inflater` happened to fold `Primitives.inflater` in.

## Decision

**A widget declares its node name; the build collects them; `ServiceLoader` finds
them.**

```java
@Markup("button")
public record Button(…) implements Widget.Leaf {

    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) { … }
}
```

That is the whole registration. The build then produces, per module:

1. a `GoldberryCatalog` implementing `WidgetCatalog`, whose `register` calls
   `into.add("button", Button::inflate)` for every annotated class — each one an
   `invokedynamic` bootstrapped by `LambdaMetafactory`, the same call site an
   `@Action` gets (ADR-0126);
2. a `provides io.…widgets.WidgetCatalog with …GoldberryCatalog` **patched into
   the module's own `module-info.class`**;
3. a `META-INF/services` entry, for the same jar on the class path.

and an application writes:

```java
var inflater = Widgets.inflater(icons, model);
```

which gathers every catalog on the path. A second widget module is found by an
application that never names it.

### Why the descriptor is patched rather than written

A **named** module publishes services through its descriptor;
`META-INF/services` is ignored for one. So the `provides` has to be there — and
it cannot be written in source, because it would name a class that does not exist
until after `javac` has run. Patching `module-info.class` is what the class-file
API is for, and it is the same "rewrite the compiled artefact" move the rest of
the weaver makes. Both declarations are emitted because a jar has to work in both
worlds.

### Why `ServiceLoader` and not a scan

Because it is the only discovery mechanism GraalVM already resolves **at image
build time**. A classpath scan would be a runtime scan, which ADR-0127 spent the
whole redesign avoiding. `ServiceLoader` is the Java answer to this exact
question and the closed world already understands it.

### Why the interface is not a generated `Map`

So that a module needing to register conditionally — a widget behind a feature
flag, a platform-specific control — can hand-write a `WidgetCatalog` instead.
That is the escape hatch, not the road; nobody writes one today.

## Consequences

`Controls` went from 204 lines to 136 and no longer has an `inflater` at all —
it is the stylesheets and the `controlTypes()` list, which is what it always
should have been. `Primitives.inflater` is gone entirely: the structural widgets
carry `@Markup` like everything else, so §9's "built-ins and application widgets
register identically" is now literally true rather than nearly.

**`Widgets.inflater(Object...)` is a footgun and needed guarding.** During the
migration, `Widgets.inflater(actions, icons, bindings)` bound to the varargs
model-taking overload, compiled, and failed at run time reading `Actions` as a
model — eight tests caught it. An exact `(Actions, Icons, Bindings)` overload now
exists so the call means what it looks like. An overload that compiles and means
something else is worse than no overload.

**The parity test changed from equality to containment.** It asserted that the
inflater's names *equalled* `Primitives.builtInTypes()`, which was true when
`Primitives` had its own registry. One merged catalog carries `tabs`, `menu`,
`popover` and `hud` too, so it now asserts every declared name is registered, and
reports which one is missing when it is not.

**The module-info patch is not exercised by `:widgets`' own tests**, because that
source set runs on the class path, where the services file is what gets read. It
is checked structurally in `CatalogWeaverTest` against a descriptor built for the
purpose — that the `provides` is added, that `requires` and `exports` survive it,
and that patching twice is a no-op — and for real when the showcase runs, which it
does modularly. The class-path test asserts it is on the class path, so that this
note cannot go stale silently.

**A `@Markup` class must have the right `inflate`.** Java cannot say that in an
annotation, so the build checks it: a `@Markup` class without a
`public static Widget inflate(KdlNode, List<Widget>, Wiring)` is a build failure
naming the class, rather than a node that fails the first time a document uses
it. Two classes claiming one node name is refused for the same reason.
