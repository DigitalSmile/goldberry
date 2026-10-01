# Writing a widget

<p class="gb-lede">A widget is an immutable record that describes a box. Implement the few interfaces it needs, annotate it with its node name, and the build registers it for markup.</p>

By the end of this chapter you can write a leaf widget that paints, give it a
CSS type and parts, let a document build it, take input and focus, follow a
bound value, give it a role, and hold it to the same tests the catalogue
passes.

```java
@Markup("gauge")
public record Gauge(double value, @Nullable Observable<?> source, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Gauge>, Bindable<Gauge>, Semantics {

    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Gauge(node.numberProperty("value", 0), wiring.bound(node), Attributes.of(node));
    }
}
```

That is the whole registration. The build collects every `@Markup` class in
the module into a catalogue and declares it as a service, and an application
that never names your module gets `gauge` in its documents
([ADR-0131](../adr/0131-a-widget-package-announces-itself.md)).

## The three shapes

| Shape | Implements | For |
|---|---|---|
| composition | `Widget.Stateless` with `build(BuildContext)` | a widget made of other widgets. Pure: it is called more often than you can predict |
| state | `Widget.Stateful` with `createState()`, and a `State<W>` | a widget that remembers something across rebuilds: a scroll offset, a caret, an open tab |
| leaf | `Widget.Leaf`, usually with `Styled` and `Paints` | a widget that produces a box of its own |

A widget is a value. It holds no state, is rebuilt constantly, and must copy
any collection it is handed. The element tree is what persists: `:hover`,
focus and a `State` live on the element and survive a parent re-describing
its child ([ADR-0052](../adr/0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)).

```java
record Counter(String label) implements Widget.Stateful {
    public State<?> createState() { return new CounterState(); }
}

final class CounterState extends State<Counter> {
    private int clicks;

    @Override public Widget build(BuildContext context) {
        return new Button(widget().label() + ": " + clicks, () -> setState(() -> clicks++));
    }
}
```

`setState` runs the mutation now and defers the rebuild, coalesced with every
other change in the frame. Re-read `widget()` on every build, because a
rebuild can hand the same state a new widget value. `initState` is where a
subscription goes and `dispose` is where it is cancelled; after `dispose`,
`setState` throws rather than leaking quietly.

`BuildContext` answers `findAncestor(type)`, `findAncestorState(type)`,
`host()` for the window's `Host`
([ADR-0140](../adr/0140-a-widget-may-reach-its-window.md)), and
`token(name, fallback)` and `duration(name, fallback)` for a custom property
resolved at this node.

## Styled: a name for the cascade

```java
@Override public String cssType() { return "gauge"; }   // the default is the class name in kebab-case
```

`Styled` is what lets a stylesheet see the widget. `cssType()` is the type
selector, `id()` and `classes()` come from the attributes, and the state
flags `isDisabled()`, `isChecked()`, `isIndeterminate()`, `isInvalid()` and
`isAffixed()` are mirrored onto the element so a stylesheet, a hit test and
the semantics agree about them. Not every widget is `Styled`: a composition
that only returns other widgets has nothing to style, and a type name for it
would put a node in the cascade no author knows about.

`restyle(ComputedStyle)` is the inline layer, for a number no selector can
express: the position of the third of five segments, a thumb's length, a
scroll offset. It runs after the cascade and before the frame's animations
observe the style, so what a widget writes there transitions like anything
else ([ADR-0099](../adr/0099-an-indicator-travels-on-a-grid.md)). A fact a
selector could match on is a class, not a restyle.

### Parts

A control with two surfaces a theme styles differently is two cascade
nodes. The inner one is a part: a `Styled` leaf of its own, returned as a
child from the control's `children()`, with a type name a stylesheet selects
and no `@Markup`, so a document cannot build one on its own
([ADR-0065](../adr/0065-a-part-is-styleable-and-not-constructible.md)).

```java
record GaugeNeedle(Attributes attributes) implements Widget.Leaf, Styled, Paints {
    @Override public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style);
    }
}
```

`gauge gauge-needle { background: var(--gb-accent) }` then reaches it. The
catalogue's `check-indicator`, `toggle-thumb` and `scroll-thumb` are parts.

## Paints: from a style to a box

`Paints.render(style, children, context)` answers one question: given what
the cascade resolved for this node and the boxes its children produced, what
box is it? The `Box` is a value, and the retained render tree is reconciled
against it rather than mutated by the widget
([ADR-0069](../adr/0069-the-render-tree-is-retained.md)).

```java
@Override public Box render(ComputedStyle style, List<Box> children, Context context) {
    var shown = source != null && source.get() instanceof Number n ? n.doubleValue() : value;
    var fraction = Math.clamp(shown, 0, 1);
    var ink = style.color();
    var track = context.color("--gb-progress-track-bg", 0xFF4C566A);
    return Box.of()
            .style(style)
            .size(Length.points(96), Length.points(56))
            .painting((frame, size) -> {
                var cx = size.width() / 2;
                var cy = size.height() - 8;
                var radius = Math.min(cx, cy) - 4;
                frame.strokePath(Path.arc(cx, cy, radius, Math.PI, Math.PI), Stroke.round(4), track);
                frame.strokePath(Path.arc(cx, cy, radius, Math.PI, Math.PI * fraction), Stroke.round(4), ink);
            });
}
```

`Box.of().style(style)` takes the layout, the background, the border, the
opacity, the transform and the cursor from the resolved style, and the
widget adds what the stylesheet cannot decide: its content. `size` sets an
intrinsic size, `painting` draws into the content box through a `Painter`
that is handed a `Frame` translated to the box's corner and clipped to it,
and `children(...)` lays the children's boxes out inside. `Box.text(paragraph,
argb)` and `Box.icon(icon, argb)` are the other two kinds of content.

The `Context` is what a render pass offers beyond the style. `font(style)`
and `paragraph(style, text)` go through the window's font book and the
paragraph cache; always call `paragraph` rather than `Paragraph.of`, because
shaping costs 56 µs and the render tree keeps the measure callback it already
has only when the same instance comes back. `color(name, fallback)` and
`length(name, fallback)` read a custom property resolved for this node, so
`#cpu { --gb-progress-track-bg: … }` recolours one gauge. `nowMillis()` is
the frame's time and `reducedMotion()` the user's preference.

### Animating

A widget that moves by CSS declares nothing: a transition is the renderer's
to track. A widget that draws itself from `nowMillis()` says so with
`isAnimating()`, and the frame loop keeps coming while it is true
([ADR-0081](../adr/0081-a-perpetual-loop-has-no-state.md)):

```java
@Override public boolean isAnimating() { return indeterminate; }
```

It is a property of the description, so a progress bar that has been given a
value stops asking and nothing has to be started or stopped. The overload
`isAnimating(style, context)` is for a widget whose answer depends on the
clock.

## Attributes and binding

```java
@Override public Gauge withAttributes(Attributes attributes) { return new Gauge(value, source, attributes); }
@Override public Gauge bound(Observable<?> source)          { return new Gauge(value, source, attributes); }
@Override public Observable<?> binding()                   { return source; }
```

`Attributed<Gauge>` gives the widget `id`, `styled`, `keyed`, `tooltip`,
`contextMenu`, `onPointerEnter` and `onPointerExit` as chainable steps, and
the chain keeps its type: `new Gauge(0.4).id("cpu")` is a `Gauge`. The one
method a record writes is `withAttributes`, because only it knows its own
components. `Attributes.of(node)` reads `id`, `class`, `tooltip`,
`context-menu` and `name` off a markup node, with the id as the key.

`Bindable<Gauge>` is the chainable `bind=`, and `Widget.binding()` is what
the element subscribes to: a change marks the element as needing a build, by
the route a `setState` takes. The value is an `Observable` and never a
`Property`, so a widget reads and watches and cannot write
([ADR-0063](../adr/0063-data-flows-down-events-flow-up.md)). What the value
means is the widget's business: `Gauge` reads a `Number` and falls back to
its own `value` for anything else.

## The inflate method

```java
public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
    return new Gauge(node.numberProperty("value", 0), wiring.bound(node), Attributes.of(node));
}
```

`@Markup("gauge")` requires a `public static Widget inflate(KdlNode,
List<Widget>, Wiring)` on the same class, and the build fails naming the
class if it is missing. The inflater is depth first, so `children` are
already built. `KdlNode` reads `stringProperty`, `numberProperty(key,
fallback)`, `booleanProperty` and `flagProperty`, and `argument()` is the
primary content. `Wiring` holds the four registries and the readings nearly
every widget needs: `Wiring.label(node)` for the argument, `wiring.bound(node)`
for `bind=`, `wiring.action(node, "press")` for a `Runnable`,
`wiring.valued(node, "change")` for a `Consumer<String>`,
`wiring.icon(node)` for `icon=`, and `wiring.handle(node, "controller",
FormController.class)` for a named object.

The catalogue is written by the weaver's `--catalog` half, which every build
of a widget module runs. In Gradle that is one line:

```groovy
plugins { id 'goldberry.weave' }
```

The rest of that plugin, and the Maven form, is in
[Model weaving](../weaving.md#the-two-halves).

## Input and focus

A widget takes part in input by implementing `Handles`. Everything on it has
a default. A gauge the user can set adds a `Consumer<Double> onChange`
component and implements it:

```java
public record Gauge(..., Consumer<Double> onChange) implements ..., Handles {

    @Override public boolean isFocusable() { return source == null; }   // only an unbound gauge is set by hand

    @Override public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.PRESSED || !Float.isNaN(event.dragX())) {
            onChange.accept(event.local().fractionX());
            event.consume();
        }
    }

    @Override public void onKey(KeyEvent event) {
        if (event.is(Key.RIGHT)) { onChange.accept(Math.min(1, value + 0.05)); event.consume(); }
    }
}
```

`onPointer` sees the event on the target and then on each ancestor,
`onPointerCapture` sees it root-first before the target does, and
`consume()` stops it. A press captures the pointer until the release, so a
drag that leaves the box still arrives, and `dragX()` says how far it has
travelled ([Input and focus](input.md#gestures-where-the-drag-started)).

| Method | Answer |
|---|---|
| `isFocusable()` | whether `Tab` stops here. False by default |
| `focusScope()` | `NONE`, or `HORIZONTAL`, `VERTICAL` or `BOTH` for a composite whose descendants are one Tab stop ([ADR-0073](../adr/0073-a-composite-is-one-tab-stop.md)) |
| `localPart()` | the CSS type of the part `local()` is measured against, for a control bigger than the thing it points along |
| `gestureAnchor()` | a number to remember for a drag, handed back as `anchor()` |
| `wantsTextInput()` | whether to turn the platform's text input on while focused. A field says true; a board that wants arrow keys says false |
| `onFocusChanged(focused, fromKeyboard)` | focus arrived or left. A radio raises its change here, so an arrow key moves the value and the tick follows |
| `onFocusWithin(within, fromKeyboard)` | focus entered or left the subtree as a whole |
| `onText`, `onPreedit`, `caretArea()` | committed text, an input method's composition, and where the caret is for its candidate window |

A `canvas` takes the same facts through its own `Input`, which is how a
painter without a widget of its own gets a pointer and a caret.

## Semantics: a role and a name

```java
@Override public Role role()                     { return Role.STATUS; }
@Override public @Nullable String accessibleName() { return attributes.name(); }
```

Every focusable widget implements `Semantics`, and a sweep over the
catalogue's source fails on one that does not. `Role` is the closed set:
`BUTTON`, `CHECKBOX`, `RADIO`, `RADIO_GROUP`, `SWITCH`, `TEXT_FIELD`,
`SLIDER`, `COMBO_BOX`, `OPTION`, `ROW`, `TAB`, `MENU_ITEM`, `MENU_BUTTON`,
`SCROLL_VIEW`, `SEPARATOR`, `DISCLOSURE`, `FIGURE`, `GROUP`, `GRID`,
`DIALOG` and `STATUS`. The name is the user's text, never an id or a type,
and null is an answer when something else names the widget. `live()` says
whether the widget's arrival is worth interrupting a reader for, which only a
toast's is. Nothing reads the tree yet, and the bridge that would is on hold
([ADR-0440](../adr/0440-the-accessibility-bridge-is-on-hold-and-the-semantics-tree-stays.md));
the data is there so that a bridge is an adapter.

## A widget must not hold a `close()`

An `Icon`, a `Font`, a `Fonts` book, a native handle: anything with a
`close()` is opened in `Application.start` and closed in `stop`, and a widget
borrows it. A widget is rebuilt and thrown away constantly, and one that
owned a resource would leak one per rebuild or close one that a sibling still
draws with. The same rule is why markup names an icon against a registry
rather than building one.

## What the catalogue's tests hold a widget to

Every built-in exists three ways, as a record, a node and a CSS type, and
three sweeps over `Widgets.inflater().registered()` enforce it
([ADR-0059](../adr/0059-a-control-is-a-record-a-node-and-a-rule.md)). Hold
your own widgets to the same list:

- **`WidgetParityTest`.** The node inflates with nothing bound. The widget
  it builds is `Styled`, its `cssType()` is the node name, exactly one node
  in what it describes carries that type, and it renders to a box. The
  record built in Java and the one inflated from the equivalent markup are
  `equals`. A part is not constructible from a document.
- **`ImmutabilityTest`.** A collection handed to the constructor is copied,
  so a caller holding the original cannot reach in afterwards.
- **`ChainingTest`.** `styled(...)` keeps the type, `id(...)` is also the
  key, and a chain of withers builds the same value a constructor would.

```java
@Test
void javaAndKdlAgree() {
    var fromJava = new Gauge(0.4, null, new Attributes("cpu", Set.of("warm"), "cpu"));
    var fromKdl  = Widgets.inflater().inflateAll(KdlParser.parse("""
            gauge id="cpu" class="warm" value=0.4
            """)).getFirst();
    assertEquals(fromJava, fromKdl);
}
```

## A golden image for a widget

```java
@Test
void restsAtForty() {
    var actual = Offscreen.of(120, 72)
            .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
            .render(new Gauge(0.4, null, Attributes.NONE));
    assertClose(Image.decode(Files.readAllBytes(golden("gauge-40.png"))), actual);
}
```

Inside this repository a widget's goldens go through `GoldenImage` with a
scene, the 2 in 256 tolerance and the scale sweep, and `./gradlew
blessGoldens` rewrites them. Outside it, `Offscreen` is the same entry point
and the comparison is yours
([Testing an application](testing.md#comparing-with-a-tolerance)). A widget
that is deterministic under a frozen clock is photographable; one that reads
the wall clock in `render` is not.

## Read more

- [ADR-0052](../adr/0052-state-lives-on-the-element-and-rebuilds-are-deferred.md): state and rebuilds
- [ADR-0059](../adr/0059-a-control-is-a-record-a-node-and-a-rule.md): a record, a node and a rule
- [ADR-0065](../adr/0065-a-part-is-styleable-and-not-constructible.md): parts
- [ADR-0069](../adr/0069-the-render-tree-is-retained.md): the retained render tree
- [ADR-0081](../adr/0081-a-perpetual-loop-has-no-state.md): `isAnimating`
- [ADR-0131](../adr/0131-a-widget-package-announces-itself.md): the catalogue
- [Building an application](../applications.md#shipping-a-widget): where a widget goes in an application
- [Model weaving](../weaving.md): the build step that writes the catalogue
