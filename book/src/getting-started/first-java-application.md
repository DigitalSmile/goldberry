# Your first Java application

<p class="gb-lede">A counter: a model with one value and one action, a document that names them, a stylesheet, and the application class that opens the window. About sixty lines, and no listener anywhere.</p>

The finished project is six files:

```
hello/
├── build.gradle
├── settings.gradle
└── src/main/
    ├── java/
    │   ├── module-info.java
    │   └── com/example/hello/
    │       ├── Counter.java        the values and the actions
    │       └── Hello.java          the application
    └── resources/com/example/hello/
        ├── hello.kdl               the screen
        └── hello.css               its look
```

<div class="gb-steps">
<div>

**Create the build.** The `application` plugin runs the program on the module
path and passes the one JVM flag native access needs. On macOS the UI thread
has to be the first thread, so the second flag is added there
([ADR-0039](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0039-macos-needs-the-first-thread.md)).

```groovy
plugins {
    id 'java'
    id 'application'
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation platform('dev.goldberry:goldberry-bom:2026.2')
    implementation 'dev.goldberry:goldberry'
    runtimeOnly 'dev.goldberry:goldberry-natives::linux-x64'
    runtimeOnly 'dev.goldberry:goldberry-natives::linux-aarch64'
    runtimeOnly 'dev.goldberry:goldberry-natives::macos-aarch64'
    runtimeOnly 'dev.goldberry:goldberry-natives::windows-x64'
}

application {
    mainModule = 'com.example.hello'
    mainClass = 'com.example.hello.Hello'
    applicationDefaultJvmArgs = ['--enable-native-access=dev.goldberry.natives']
    if (System.getProperty('os.name').toLowerCase().contains('mac')) {
        applicationDefaultJvmArgs += '-XstartOnFirstThread'
    }
}
```

`settings.gradle` is one line: `rootProject.name = 'hello'`.

</div>
<div>

**Declare the module.** Two `requires`, and one `opens` so the toolkit can read
the model's fields and the resources beside it
([ADR-0395](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0395-a-resource-is-opened-to-whoever-reads-it.md)).

```java
module com.example.hello {
    requires dev.goldberry.core;
    requires dev.goldberry.widgets;

    opens com.example.hello to dev.goldberry.core;
}
```

</div>
<div>

**Write the model.** A class of fields marked `@Bind`, with the actions nested
inside it as a record. Assigning a field is what notifies the window, so there
is no setter to call and no event to raise
([ADR-0134](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0134-a-write-is-rewritten-wherever-it-is.md)).

```java
package com.example.hello;

import dev.goldberry.bind.Action;
import dev.goldberry.bind.Actions;
import dev.goldberry.bind.Bind;
import dev.goldberry.bind.Model;

@Model
public final class Counter {

    @Bind("counter.label") private String label = "Pressed 0 times";

    private int presses;

    @Actions
    public record Commands(Counter values) {

        @Action("counter.press")
        public void press() {
            values.presses++;
            values.label = "Pressed " + values.presses + (values.presses == 1 ? " time" : " times");
        }

        @Action("counter.reset")
        public void reset() {
            values.presses = 0;
            values.label = "Pressed 0 times";
        }
    }
}
```

The record is called `Commands` and not `Actions`: a nested type named
`Actions` would shadow the annotation
([Building an application](../applications.md#actions)).

</div>
<div>

**Write the screen.** The document names the value with `bind=` and the
actions with `press=`. The text after `bind=` is what a preview shows when
nothing is bound.

```kdl
column id="hello" {
  text class="heading" "Hello, Goldberry"
  text id="count" bind="counter.label" "Pressed 0 times"
  row id="buttons" {
    button class="primary" press="counter.press" "Press me"
    button press="counter.reset" "Reset"
  }
}
```

</div>
<div>

**Give it a look.** The toolkit's stylesheets draw the controls. Yours lays
out the window. Every colour is a token, so the same rule is right on both
themes.

```css
#hello   { padding: 24px; gap: 12px; }
#buttons { gap: 8px; }
#count   { color: var(--gb-accent); }
```

</div>
<div>

**Write the application.** The one class that knows a window exists. It names
the models, inflates the document, lists the stylesheets, and hands itself to
the launcher.

```java
package com.example.hello;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.Application;
import dev.goldberry.Goldberry;
import dev.goldberry.css.CascadeLayer;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Widgets;

public final class Hello implements Application {

    private final Counter counter = new Counter();
    private final Counter.Commands commands = new Counter.Commands(counter);
    private final Stylesheet styles = Stylesheet.resource(CascadeLayer.APPLICATION, Hello.class, "hello.css");

    @Override
    public String title() {
        return "Hello";
    }

    @Override
    public List<Object> models() {
        return List.of(counter, commands);
    }

    @Override
    public Widget root() {
        return Widgets.inflater(models().toArray())
                .inflate(KdlParser.resource(Hello.class, "hello.kdl").getFirst());
    }

    @Override
    public List<Stylesheet> stylesheets() {
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(styles);
        return sheets;
    }

    public static void main(String[] args) {
        Goldberry.launch(new Hello(), args);
    }
}
```

</div>
<div>

**Run it.**

```sh
./gradlew run
```

A window opens with a heading, a line of text and two buttons. Press the
first and the line counts. Nothing in the application repainted: assigning
`label` told the bound `text` and asked the window for a frame
([ADR-0128](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0128-a-change-is-its-own-frame-request.md)).

</div>
</div>

## What just happened

- `models()` is the whole of the wiring. The document's `bind=` and `press=`
  resolve against the objects in that list, and the same list is what the
  window subscribes to ([ADR-0129](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0129-a-value-is-named-one-way.md)).
- The inflater is built from `models()` and not from a second list, so the two
  cannot disagree. A name the document uses that no model declares fails when
  the document is inflated, naming the text, rather than on the first click
  ([ADR-0062](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0062-bind-is-a-path-and-nothing-else.md)).
- On the JVM the model is bound reflectively. The same class, woven, is what a
  native image runs ([Model weaving](../weaving.md)).

## Change it while it runs

Point a watcher at the stylesheet on disk and the window restyles when the
file is saved. The same works for a document.

```java
private final ReloadableSource<Stylesheet> styles = ReloadableSource.load(
        Path.of("src/main/resources/com/example/hello/hello.css"),
        css -> Stylesheet.parse(CascadeLayer.APPLICATION, css)
);

private HotReload reload;

@Override public void start(Host host) {
    reload = HotReload.watch(List.of(styles), Goldberry.ui(), changed -> host.restyle());
}

@Override public List<Stylesheet> stylesheets() {
    var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
    sheets.add(styles.current());
    return sheets;
}

@Override public void stop() {
    reload.close();
}
```

`HotReload` and `ReloadableSource` are in `dev.goldberry.reload`. The callback
runs on the UI thread, which is why the watcher takes an executor. A file that
fails to parse keeps its last good value and logs the failure.
[Markup](../guide/markup.md) covers reloading a document.

## Switch the theme

Make the theme a bound value with `restyle = true`, and switching it is one
assignment ([ADR-0133](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0133-a-restyle-is-declared.md)):

```java
@Bind(value = "app.light", restyle = true) private boolean light;

public Theme theme() {
    return light ? Theme.NORD_LIGHT : Theme.NORD_DARK;
}

@Actions
public record Commands(Counter values) {
    @Action("app.set-light") public void setLight(boolean light) { values.light = light; }
}
```

```kdl
toggle bind="app.light" change="app.set-light" "Light"
```

`stylesheets()` is re-read only when a restyle is asked for, so returning
`Controls.stylesheets(settings.theme())` from it costs nothing between
switches.

## Where to go from here

<div class="gb-cards">
<a class="gb-card" href="first-native-application.html"><strong>Your first native application</strong><span>The same project as one executable with GraalVM.</span></a>
<a class="gb-card" href="../applications.html"><strong>Building an application</strong><span>What each class may know, and where a thing goes when you are not sure.</span></a>
<a class="gb-card" href="../components/index.html"><strong>The catalogue</strong><span>Every widget, with markup and Java for each.</span></a>
<a class="gb-card" href="../guide/styling.html"><strong>Styling</strong><span>Selectors, tokens, themes, density, transitions.</span></a>
</div>

> [!TIP]
> `Goldberry.launch` reads `--frames=N` and `--size=WxH` from the arguments,
> and `-Dgoldberry.backend.videoDriver=dummy` runs without a display. Together
> they are how an application paints three frames on a CI runner and exits.
> [Testing an application](../guide/testing.md) has the rest.
