# Building an application

How a Goldberry application is put together: what the classes are, what each one
is allowed to know, and where a thing goes when you are not sure.

The rule underneath all of it is one sentence: **data flows down, events flow up**
([ADR-0063](adr/0063-data-flows-down-events-flow-up.md)). Everything below is that
sentence turned into files.

## The four kinds of class

```
Values ────────► Views ────────► Actions ────────► Values
   (read)          (report)         (assign)         (notify)
```

| | What it is | What it may know |
|---|---|---|
| **Values** | a `@Model` class of plain fields | nothing. No widget, no window, no toolkit type beyond `@Bind` |
| **Actions** | an `@Actions` record nested in the values | the values. Not widgets, not the window |
| **Views** | `Widget` records — or a `.kdl` document | the values it reads and the actions it calls |
| **Application** | one `implements Application`, and **no annotation** | all three, plus the `Host`. The only class that knows a window exists |

Nothing points backwards. Values do not know actions exist; actions do not know
views exist; a view cannot write a value, because what it is handed is an
`Observable` with no `set` on it.

## Values

A class of fields. Not a record — a record's components are final and a bound
field has to be assignable.

```java
@Model
public final class Settings {

    @Bind("app.gain")                               private Number gain = 40;
    @Bind(value = "app.theme", restyle = true)      private String theme = "dark";
    @Bind(value = "app.bytesRead", repaint = false) private long bytesRead;

    // Projections are fine: a question about the fields with one answer.
    public Theme theme() {
        return "light".equals(theme) ? Theme.NORD_LIGHT : Theme.NORD_DARK;
    }
}
```

Each field declares what changing it costs:

- **the binding** — always. Anything bound to `app.gain` is told.
- **a frame** — by default; `repaint = false` for a value nothing on screen shows
  ([ADR-0135](adr/0135-a-frame-is-asked-for-by-the-value-that-moved.md)).
- **a restyle** — `restyle = true` when a *rule* depends on it, not a widget. A
  theme and a density, and almost nothing else
  ([ADR-0133](adr/0133-a-restyle-is-declared.md)).

Assignment is what is observed, so hold a `List` and replace it rather than
editing one in place. The build refuses to bind an array for exactly this reason.

## Actions

A record wrapping the values, **nested inside them**. One method per thing a
control can ask for.

```java
@Model
public final class Settings {

    @Bind("app.gain") private Number gain = 40;

    @Actions
    public record Commands(Settings values) {

        @Action("app.louder") public void louder() { values.gain = values.gain.doubleValue() + 1; }
        @Action("app.pick")   public void pick(String name) { values.theme = name; }
    }
}
```

**`@Actions`, not `@Model`.** A class of methods holds no values and publishes no
paths; calling it a model said otherwise
([ADR-0139](adr/0139-actions-are-annotated-as-actions.md)). A class carrying both
markers, or an `@Actions` class with a `@Bind` field, is a build failure saying
which one it should be.

**Name it for its domain — `Commands`, `Editing`, `Playback` — and not
`Actions`.** A nested type called `Actions` shadows the annotation, so `@Actions`
would resolve to your own record and you would have to write the annotation out in
full. The showcase does exactly that and pays for it, on purpose, so there is one
worked example of the wart.

`values.gain = …` notifies, even though the assignment is in a different class:
the build rewrites a write to a `@Bind` field wherever it appears
([ADR-0134](adr/0134-a-write-is-rewritten-wherever-it-is.md)).

**A record**, because it holds one thing and holds it immutably: no state of its
own, `equals` that means what it says, a constructor nobody writes. Wanting a
mutable field here is the signal that the thing is *state* — put it with the
values, where the rest of the state is.

**Nested**, because a nestmate reaches a private field. That is the whole reason,
and it is worth the one file: a sibling top-level class works too, but forces
every value open to the package
([ADR-0137](adr/0137-a-model-keeps-its-fields.md)). Nesting is scoping, not
coupling — the values class holds no reference to `Actions` and compiles with it
deleted.

Three shapes, in order of preference:

| Shape | Fields | When |
|---|---|---|
| values with a nested `@Actions` record | `private` | the default |
| one class, values and methods together | `private` | a model with three fields |
| values and a sibling `@Actions` class | package-private | you want two files and will pay for them |

An `@Action` method takes no argument, or one the toolkit can parse from a string
(`String`, `double`, `int`, `boolean`). A button reports *that* something
happened; a slider reports *what* it should become.

## Views

Two forms, and they resolve names identically.

**Markup**, for structure:

```kdl
column class="settings" {
  slider bind="app.gain" min=0 max=100 change="app.set-gain"
  button "Louder" press="app.louder"
}
```

**Java**, for anything a document should not carry — a loop, a conditional, a
widget built from a list:

```java
public record Panel(Settings settings, Settings.Commands actions) implements Widget.Stateless {

    @Override public Widget build(BuildContext context) {
        return new Column(
                new Slider(0, 100, Models.observable(settings, "app.gain"), actions::setGain),
                new Button("Louder", actions::louder));
    }
}
```

`bind="app.gain"` and `Models.observable(settings, "app.gain")` are the same
lookup against the same registry. There is one name for a value, not two
([ADR-0129](adr/0129-a-value-is-named-one-way.md)).

### What a view may not do

Write. A widget is handed the `Observable` half of a value and there is no `set`
to call — so a control built from markup cannot reach the model even by accident,
and "who changed this?" always has an answer.

### Widget state versus application state

Two different things, and putting one where the other goes is the commonest
mistake.

| | Lives in | Dies when |
|---|---|---|
| a scroll offset, a caret, which tab is open, a hover | `State` on the widget | the widget is unmounted |
| the gain, the theme, the document being edited | the values class | the application exits |

Ask: *would a second screen showing this need the same answer?* If yes, it is
application state.

## The application

One class. The only one that knows a window exists.

```java
public final class Hello implements Application {

    private final Settings settings = new Settings();
    private final Settings.Commands actions = new Settings.Commands(settings);
    private Icons icons;

    /// Everything markup may name, and everything the window follows.
    @Override public List<Object> models() {
        return List.of(settings, actions);
    }

    @Override public void start(Host host) {
        // Native resources are opened here and closed in stop(): a widget is a
        // value that gets rebuilt and thrown away, so nothing with a close()
        // belongs in a build method.
        icons = Icons.strict().bind("plus", Icon.bundled("plus", 16));
        host.shortcut("Ctrl+T", actions::toggleTheme);
    }

    @Override public Widget root() {
        return new Panel(settings, actions);
    }

    @Override public List<Stylesheet> stylesheets() {
        return Controls.stylesheets(settings.theme());
    }

    @Override public void stop() {
        icons.close();
    }

    public static void main(String[] args) {
        Goldberry.run(new Hello());
    }
}
```

`models()` is the whole of the wiring. From that one list the toolkit gets:

- what a document's `bind=` and `press=` resolve against;
- when to repaint — any value that asks;
- when to restyle — any value declared `restyle = true`.

There is no `repaint()` call anywhere in an application, and there should not be
one ([ADR-0128](adr/0128-a-change-is-its-own-frame-request.md)).

### More than one model

The list is a list because a window has actions of its own — "open the menu",
"toggle the HUD" — that need a `Host` and therefore have no business on a view
model. Put them on the `Application` itself, annotate it `@Model`, and add `this`:

```java
/// The window's own actions. Two Runnables, so this knows what they are called
/// and nothing about who performs them.
@Actions
public record WindowActions(Runnable openMenu) {
    @Action("app.open-menu") public void open() { openMenu.run(); }
}
```

```java
public final class Hello implements Application {

    private final WindowActions window = new WindowActions(this::openMenu);

    @Override public List<Object> models() { return List.of(settings, actions, window); }

    private void openMenu() { host.popup(…); }
}
```

The `Application` itself is **not** a `@Model`. It owns the window, the lifecycle
and the native resources; making it also a thing markup resolves names against
puts two unrelated roles on one class
([ADR-0138](adr/0138-a-window-s-actions-are-a-model-of-their-own.md)).

Two models may not claim one name; the build says which two.

## Inflating a document

```java
var inflater = Widgets.inflater(icons, models().toArray());
var window   = inflater.inflate(KdlParser.resource(Hello.class, "window.kdl").getFirst());
```

**From `models()`, not from a list written out again.** Two lists that must agree
are two lists that will not: the showcase shipped for one commit with `actions`
missing from the inflater and present in `models()`, so every test passed and the
window threw `no action named "app.toggle-theme" is bound` on the first frame.

`icons` is the one registry that cannot be derived: an `Icon` owns native memory
and has to be closed, so markup may *name* one and must never build one.

Widget names need no registration at all. Every module on the path that ships
widgets announces itself, so `Widgets.inflater` already knows `button` and
`column` and anything a third widget module brought with it
([ADR-0131](adr/0131-a-widget-package-announces-itself.md)).

## Shipping a widget

If you are writing widgets rather than using them:

```java
@Markup("gauge")
public record Gauge(double value, Observable<?> source, Attributes attributes)
        implements Widget.Leaf, Styled, Paints {

    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Gauge(node.numberProperty("value", 0), wiring.bound(node),
                Attributes.of(node));
    }
}
```

That is the whole registration. The build collects every `@Markup` class in the
module into a catalog and declares it as a service; an application that never
names your module gets your widget.

Every built-in must be constructible three ways — Java, KDL, and styleable by CSS
— and a test enforces it. Hold your own widgets to the same rule; it is what
makes a document portable between an application and a preview tool.

## Where does it go?

| I have… | It goes… |
|---|---|
| a value a widget shows | a `@Bind` field on the values class |
| a value nothing shows, but something watches | `@Bind(…, repaint = false)` |
| a value a stylesheet depends on | `@Bind(…, restyle = true)` |
| something a button does | an `@Action` on the actions class |
| something only Java calls | a plain method on the actions class |
| a derived answer about the values | a method on the values class |
| a scroll offset, a caret | `State` on the widget |
| an icon, a font, a native handle | opened in `start`, closed in `stop` |
| a menu, a popup, an accelerator | the `Application`, which has the `Host` |
| a new node name for markup | `@Markup` on the widget |
| an action that needs the `Host` | a small `@Actions` record of `Runnable`s, built by the application |

## The package layout that follows

```
com.example.app
├── Hello.java              Application: the Host, the lifecycle. No annotation.
├── Settings.java           @Model values, with a nested @Actions record Commands
├── WindowActions.java      @Actions record: the actions that need the Host
├── window.kdl              structure
├── app.css                 appearance
└── ui/
    ├── Panel.java          Widget records
    └── Gauge.java          @Markup, if you ship widgets
```

Views in their own package, because they should be replaceable without touching
the model. Everything else is flat: there is not enough of it to file.

## What the build does to all this

Your model is plain Java; the build makes assignments to it observable, using the
JDK's class-file API on the compiled class. That is one Gradle plugin or one
Maven `<execution>`, and it is the subject of [its own page](weaving.md) —
including what happens when you forget it, which is a loud error naming the
missing step rather than a control that renders perfectly and never moves.
