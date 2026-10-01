# Markup

<p class="gb-lede">A KDL document is a widget tree written down, and the names in it resolve against what your application registers.</p>

By the end of this chapter you can write a document, parse it, inflate it
against your models, wire its buttons and bindings, reload it while the window
is open, and know where a document stops and Java begins.

```kdl
column id="settings" {
  text class="heading" "Preferences"
  checkbox bind="prefs.frost" change="app.set-frost" "Frosted sidebar"
  slider bind="app.gain" min=0 max=100 change="app.set-gain"
  row {
    spacer
    button class="primary" icon="plus" press="app.create" "New"
  }
}
```

```java
var nodes    = KdlParser.resource(Hello.class, "settings.kdl");
var inflater = Widgets.inflater(icons, models().toArray());
Widget settings = inflater.inflate(nodes.getFirst());
```

The result is an ordinary `Widget`. Put it in a `build` method, in a `Tab`, or
return it from `root()`.

## The syntax, as Goldberry reads it

Goldberry parses [KDL 2.0](https://github.com/kdl-org/kdl/blob/main/SPEC.md)
with a parser of its own, so that every node carries its line and column
([ADR-0051](../adr/0051-kdl-is-parsed-here-and-reloading-is-forgiving.md)).

```kdl
// A node is a name, then arguments, then properties, then a child block.
button class="primary" press="app.save" "Save" {
  // children go here
}

/* Block comments nest: /* like this */ and the outer one still closes. */
text "A long label that continues" \
     "on the next line"
/- text "A slashdash takes this whole node out"
progress value=0.5 max=1 indeterminate=#false
```

| Piece | What it is to a widget |
|---|---|
| node name | the widget type: `button`, `column`, `radio-group` |
| string argument | the primary content, `"Save"` on a button, `"Preferences"` on a text |
| `key=value` properties | attributes. A repeated key keeps the last value |
| `{ … }` children | the widget's children, in source order |
| `#true` `#false` `#null` | the keywords. Bare `true` is not a boolean and is refused |
| numbers | decimal, hex, octal or binary, with `_` separators |
| `"…"` and `#"…"#` | quoted strings with escapes, and raw strings fenced by `#` |
| `//`, `/* */`, `/-` | line comments, nesting block comments, and the slashdash that comments out a node, an argument, a property or a child block |
| `;` and `\` | nodes on one line, and a node continued on the next |

Two pieces of KDL are refused by name rather than ignored: type annotations
such as `(u8)123`, and `"""` multi-line strings. A document that says
something the toolkit would discard should fail, not look as though it worked.

A parse error is a `KdlSyntaxException` with the line and column. So is an
unknown node name, and the message lists every name that is registered.

## Parsing and inflating

`KdlParser` turns text into nodes. `KdlInflater` turns nodes into widgets.

```java
List<KdlNode> fromText  = KdlParser.parse("button \"Save\"");
List<KdlNode> fromFile  = KdlParser.resource(Hello.class, "window.kdl");

KdlInflater<Widget> inflater = Widgets.inflater(icons, settings, actions);
Widget one   = inflater.inflate(fromFile.getFirst());
List<Widget> all = inflater.inflateAll(fromFile);
```

`KdlParser.resource` reads a file beside the class, so `window.kdl` sits in
the same package as `Hello.java`. `KdlInflater.byId(nodes, "bar")` finds a
node in the document by its `id`, and refuses a document that uses one id
twice.

Widget names need no registration. Every module that ships `@Markup` widgets
announces its catalogue as a service, and `Widgets.inflater` finds every
catalogue on the path
([ADR-0131](../adr/0131-a-widget-package-announces-itself.md)). A module you
never name still contributes its node names.

## The four registries

A document names things it cannot build. Each kind of name resolves against a
registry of its own, and the four answer four different questions
([ADR-0170](../adr/0170-a-document-names-an-object-and-a-label-hands-focus-down.md)).

| Attribute | Registry | What the name is | Where it comes from |
|---|---|---|---|
| `press="app.save"` | `ActionRegistry` | a method | an `@Action` on a model in `models()` |
| `change="app.set-gain"` | `ActionRegistry` | a method taking one value | an `@Action(String)`, `(double)`, `(int)` or `(boolean)` |
| `bind="app.gain"` | `BindingRegistry` | a value that changes | a `@Bind` field on a model |
| `icon="plus"` | `Icons` | a resource built once | `Icons.strict().bind("plus", icon)` |
| `controller="app.signup-form"`, `validator="app.port-rule"` | `Named` | an object that neither changes nor closes | `Named.strict().bind(name, object)` |

`Widgets.inflater(icons, models...)` reads the first two registries off the
models themselves and keeps the icons explicit, because an icon is parsed and
scaled to one size and markup must not be able to build one per reload
([ADR-0043](../adr/0043-icons-are-stroked-paths.md)).
`Widgets.inflater(named, icons, models...)` adds the fourth.

> [!IMPORTANT]
> Hand the inflater the same list `models()` returns. Two lists that must
> agree will not, and the symptom is a window that throws on its first frame.

### Strict by default

The registries a model publishes are strict. A name the document writes and
nothing bound fails at inflation, with the text quoted and the bound names
listed:

```text
no action named "app.sav" is bound. Bound: app.create, app.save, app.set-gain
nothing is bound to "app.gian". Bound: app.gain, prefs.frost
no icon named "pluss" is registered. Registered: plus, palette
```

That is the point of a registry: `press="delte"` is a typo, and a button that
silently does nothing is the hardest kind of bug to notice
([ADR-0062](../adr/0062-bind-is-a-path-and-nothing-else.md)).

A lenient registry resolves an unknown name to nothing. That is what a preview
or a golden image wants, and `Widgets.inflater()` with no arguments binds
nothing at all. A bound node then draws its argument as the fallback, so
`text bind="user.name" "Name here"` reads *Name here* in a preview.

### `bind=` is a path and nothing else

```kdl,ignore
text bind="app.status"
text bind="prefs.frost"
```

A path is a name, or names joined by dots. `bind="!prefs.frost"` is refused
at inflation:

```text
"!prefs.frost" is not a binding path. A path is a name, or names joined by
dots — `frost`, `prefs.frost`. Expressions are not part of the markup contract
(ADR-0062).
```

Negation, formatting and arithmetic stay in Java, where they are already
testable. A bound widget is handed an `Observable` with no `set`, so markup
reads a value and cannot write it. What the user did goes back up as an action
([ADR-0063](../adr/0063-data-flows-down-events-flow-up.md)). The path and the
Java lookup `Models.observable(settings, "app.gain")` are one name against one
registry ([ADR-0129](../adr/0129-a-value-is-named-one-way.md)).

## The attributes every node has

| Attribute | What it does |
|---|---|
| `id="bar"` | the `#id` a stylesheet selects, the name `host.anchor` and `host.focus` look up, and the node's reconciler key |
| `class="primary danger"` | the `.class` names, space separated. Replaces, never accumulates |
| `tooltip="…"` | text the toolkit shows after a delay, on hover and on keyboard focus |
| `context-menu="rows"` | the name of a menu a right-click opens. See [Input and focus](input.md#context-menus) |
| `name="…"` | the accessible name, for an icon-only control whose label is empty |
| `disabled=#true` | on every control: refuses its action, leaves the Tab order, matches `:disabled` |

There is no `key=` attribute. In markup the `id` is the key, and the two
cannot disagree. A list item whose identity is a row of a model gets its key
from Java, with `.keyed(row)`.

The widget-specific attributes are in each widget's chapter under
[Components](../components/index.md): `min=` and `max=` on a `slider`,
`label=` and `required=` on a `field`, `value=` on a `radio`.

## Hot reload

Markup and stylesheets can be re-read while the window is open. The reload
package has two classes: `ReloadableSource` parses a file and keeps the last
version that parsed, and `HotReload` watches the file's directory and applies
changes on the UI thread.

```java
import dev.goldberry.reload.HotReload;
import dev.goldberry.reload.ReloadableSource;

private final ReloadableSource<Stylesheet> styles = ReloadableSource.load(
        Path.of("src/main/resources/com/example/app/app.css"),
        css -> Stylesheet.parse(CascadeLayer.APPLICATION, css)
);

private final ReloadableSource<List<KdlNode>> document = ReloadableSource.load(
        Path.of("src/main/resources/com/example/app/window.kdl"),
        KdlParser::parse
);

@Override public void start(Host host) {
    reload = HotReload.watch(List.of(styles, document), Goldberry.ui(), source -> {
        if (source == styles) {
            host.restyle();            // stylesheets() reads styles.current()
        } else {
            // Your own root is a Widget.Stateful: hand it the new nodes and let
            // its State re-inflate them inside setState.
            screen.reload(document.current());
        }
    });
}

@Override public List<Stylesheet> stylesheets() {
    return List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), styles.current());
}

@Override public void stop() {
    reload.close();
}
```

The first parse is strict: an application that starts with a broken file says
so. After that a broken save is the normal case. The last good version stays in
force, the failure is logged once with its position, and the next save gets
another go. An identical save is ignored. Saving a file is several events, so
the watcher waits for a short quiet period before reading.

Because the names a document uses resolve against registries, a reloaded
document stays wired to the same handlers the old one had. That is why markup
names an action and cannot be one.

> [!NOTE]
> On macOS the JDK's `WatchService` polls, so a change can take a couple of
> seconds to be noticed.

## What markup cannot say

A document is data. It has no loop, no conditional, and no way to build a
widget from a list. Those are Java, in a `build` method
([ADR-0222](../adr/0222-a-showcase-is-a-window-a-bar-and-seven-screens.md),
[ADR-0110](../adr/0110-the-showcase-is-a-gallery-of-screens.md)):

```java
@Override public Widget build(BuildContext context) {
    var rows = new ArrayList<Widget>();
    for (var member : company.members()) {
        rows.add(new Text(member.name()).keyed(member.id()));
    }
    return new Column(rows);
}
```

A document cannot hand a `Runnable` to a widget, cannot compute a colour from
a value, and cannot shorten a list when the model does. It can name every one
of those things, and Java supplies them.

## How Java and markup compose

A document inflates to a `Widget`, so it goes wherever a widget goes. The
showcase keeps each screen's static cards in a document whose root is a
`masonry`, and appends the cards only Java can write:

```java
Masonry cards = Panes.wallOf(inflater, "basic.kdl");   // the document's root
var all = new ArrayList<>(cards.children());
all.add(new ClickCounter(model, actions));              // a card markup cannot write
return new Masonry(all, cards.columns(), cards.minColumnWidth(), cards.attributes());
```

The other direction works too. A `button` in a document resolves `press=`
against an `@Actions` record the application built in Java, and the window's
own commands live on a record of `Runnable`s beside the view model
([Building an application](../applications.md#more-than-one-model)).

## Read more

- [ADR-0051](../adr/0051-kdl-is-parsed-here-and-reloading-is-forgiving.md): the parser, and why reloading is forgiving
- [ADR-0062](../adr/0062-bind-is-a-path-and-nothing-else.md): a `bind` is a path
- [ADR-0063](../adr/0063-data-flows-down-events-flow-up.md): one-way binding
- [ADR-0129](../adr/0129-a-value-is-named-one-way.md): one name for a value
- [ADR-0131](../adr/0131-a-widget-package-announces-itself.md): catalogues as services
- [ADR-0170](../adr/0170-a-document-names-an-object-and-a-label-hands-focus-down.md): the fourth registry
- [Model weaving](../weaving.md): what the build does to a model
