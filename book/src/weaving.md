# Model weaving

A Goldberry model is plain Java. You write fields; the toolkit makes assignments
to them observable.

**You do not have to run the weaver.** A plain jar binds a model reflectively and
needs no build step at all; weaving is what a **GraalVM native image** is built
from ([ADR-0155](adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md)). The
short version is [below](#you-probably-do-not-need-to-run-any-of-this); this page
starts with what the weaver does, because that is the form everything else is
described against.

```java
@Model
public final class Settings {
    @Bind("app.gain")                          private int gain = 40;
    @Bind(value = "app.theme", restyle = true) private String theme = "dark";

    @Action("app.louder") private void louder() { gain++; }
    @Action("app.pick")   private void pick(String name) { theme = name; }
}
```

```kdl
slider bind="app.gain" min=0 max=100 change="app.pick"
button "Louder" press="app.louder"
```

```java
public final class Hello implements Application {

    private final Settings settings = new Settings();

    @Override public List<Object> models() { return List.of(settings); }

    @Override public Widget root() {
        return Widgets.inflater(icons, settings).inflate(document);
    }
}
```

`gain++` moves the slider and asks for a frame. Changing `theme` restyles first.
There is no `Property`, no `set`/`get`, no listener registration, and no
`repaint()` anywhere in the application.

## The five annotations

| | On | Means |
|---|---|---|
| `@Model` | a class | it holds values: its `@Bind` fields are rewired |
| `@Actions` | a class | it holds only methods, acting on somebody else's values |
| `@Bind("a.b")` | a field | markup names this value; `restyle = true` means a rule depends on it |
| `@Action("a.b")` | a method | markup names this handler; no argument, or one the toolkit can parse from a string |
| `@Markup("button")` | a widget class | this is the node name a document writes for it |

`@Markup` is the widget-author's half: the build collects every annotated class in
a module into a `WidgetCatalog`, declares it in the module descriptor, and
`Widgets.inflater(...)` finds every catalog on the path. A module that ships
widgets is found by an application that never names it
([ADR-0131](adr/0131-a-widget-package-announces-itself.md)).

## What the weaver actually does

It rewrites the **compiled class**, between `compileJava` and anything that reads
its output, using the JDK 25 class-file API (JEP 484). For the class above,
`Settings.class` comes out with:

1. `implements BoundModel`, and a lazily created `FieldListeners`;
2. a synthesised `goldberry$set$gain(int)` — compare, store, notify;
3. every `putfield gain` rewritten into a call to it — in that class and in any
   other class in the same build that assigns to it, which is what lets an
   `@Actions` class beside the model change its values
   ([ADR-0134](adr/0134-a-write-is-rewritten-wherever-it-is.md));
4. `bindings()` and `actions()`, built from the annotations, the second as one
   `invokedynamic` per action bootstrapped by `LambdaMetafactory`.

For a module with `@Markup` widgets it also writes a `GoldberryCatalog`, patches
`provides WidgetCatalog with …` into `module-info.class`, and drops a
`META-INF/services` entry — both, because a jar has to work on the module path and
on the class path, and the module system ignores `META-INF/services` for a named
module.

Step 3 is a one-for-one instruction swap: `putfield` pops *objectref, value*, and
so does an instance call taking one argument.

### Why it has to be a build step to see the write

A field write cannot be intercepted any other way. `getfield` and `putfield` are
not virtual, so no subclass and no proxy can see one — **the class that declares
the field is the only place the write can be observed.** Doing that to the
compiled class in the build is the one option that needs no `-javaagent`, no
`opens`, and nothing generated at runtime, which is also what lets the result go
into a GraalVM native image ([ADR-0127](adr/0127-the-binding-schema-fits-a-closed-world.md)).

## You probably do not need to run any of this

**Model weaving is for a native image.** An ordinary jar binds the same
annotations at run time and needs no build step at all
([ADR-0155](adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md)):

| | Woven | Bound at run time |
|---|---|---|
| Who | a GraalVM native image | everything else — `gradle run`, `mvn exec:java`, an IDE, `java -jar` |
| Build step | the weaver, over the compiled classes | none |
| A change notifies | inside the assignment that made it | at the next **sweep** |
| Needs | nothing | the model's package open to the toolkit, in a named module |
| `Models.isWoven` | `true` | `false` |

Everything else is identical. The same `Models.bindings`, the same
`Models.actions`, the same paths, the same values, the same refusals — and
`RuntimeAgreesWithWovenTest` drives one model class both ways through the same
actions to keep it that way.

### The sweep, and the one line it sometimes costs

Reading is exact either way: an `Observable` over a woven field and one over a
`VarHandle` both see the field itself. What the reflective form cannot do is see
the *write*, so it compares each field against what it last held and notifies
what moved. That sweep runs

- after every action a document dispatches — across **every** model, because an
  `@Actions` record writes to the model beside it;
- at the top of every frame, over the models `Application.models()` named;
- wherever you call `Models.refresh(model)`.

Which leaves one case: a field written from neither an action nor anything that
leads to a frame.

```java
job.onFinished(text -> {
    model.status = text;
    Models.refresh(model);   // a no-op, returning false, once this is woven
});
```

### A model in a named module opens its package

The reflective form needs private access, and JPMS is what grants it:

```java
opens com.example.app to io.github.digitalsmile.goldberry.core;
```

A classpath application needs nothing — the unnamed module is open. The refusal
names the package and that exact line if you forget. The woven form needs neither,
which is one more reason an image is the cheaper artifact.

### Turning weaving on

```
./gradlew build -Pgoldberry.nativeImage=true    # the whole build, woven
./gradlew :example:weaveModels                  # one module, to look at
```

The **catalog** half of the weaver is not optional and is not affected by any of
this — see below.

### What it never touches

Classes with neither marker are not rewritten — not even re-serialised, unless
they assign to some model's `@Bind` field. Reads are
left alone: `getfield` is already the fastest thing that could happen. And
weaving is idempotent, so running it twice over the same tree writes nothing the
second time.

## The two halves

The weaver does two unrelated jobs to the same tree, and a build asks for them
separately:

| Flag | Does | Needed by |
|---|---|---|
| `--models` | rewires `@Bind` fields, writes the `@Action` call sites | a **native image** only |
| `--catalog` | writes the module's `WidgetCatalog` from its `@Markup` widgets, patches `provides` into `module-info.class`, writes `META-INF/services` | **every** build |

Neither flag means both, which is what every pre-ADR-0155 integration already
wrote.

The catalog half has no runtime equivalent and never will: finding annotated
classes while the program runs means scanning the path, which is the thing a
`provides` exists to avoid ([ADR-0131](adr/0131-a-widget-package-announces-itself.md)).
So a module that ships widgets runs the weaver whatever it is building; a module
that only keeps a model runs it only for an image.

## Adding it to a project

The weaver is one jar with **no dependencies beyond the JDK**, and a `main` that
takes directories of compiled classes. Every integration below is a way of
calling that.

### Gradle

Apply the plugin to any module that ships `@Markup` widgets, or that you want to
be able to weave for an image:

```groovy
plugins {
    id 'goldberry.weave'
}
```

It registers four `JavaExec`s — `weaveCatalog` and `weaveModels`, each for the
main and test source sets. The catalog pair hangs off `classes` and `testClasses`
so `jar`, `run` and every `Test` task reach through it; the model pair joins them
only under `-Pgoldberry.nativeImage=true`.

In a build that consumes Goldberry from a repository rather than from this
source tree, the equivalent is:

```groovy
configurations { goldberryWeaver }
dependencies { goldberryWeaver "io.github.digitalsmile:goldberry-weaver:$goldberryVersion" }

def weave = tasks.register('weaveModels', JavaExec) {
    dependsOn tasks.compileJava
    classpath = configurations.goldberryWeaver
    mainClass = 'io.github.digitalsmile.goldberry.weaver.WeaverMain'
    def classes = tasks.compileJava.flatMap { it.destinationDirectory }
    argumentProviders.add({ [classes.get().asFile.absolutePath] } as CommandLineArgumentProvider)
    inputs.dir(classes)
    outputs.dir(classes)
    outputs.upToDateWhen { false }      // in place, and idempotent
}
tasks.named('classes') { dependsOn weave }
```

### Maven

There is **no first-class Maven plugin.** `exec-maven-plugin` runs the weaver as
it stands, bound to `process-classes`, which is the phase that exists for exactly
this. Add `<argument>--catalog</argument>` (or `--models`) before the directory to
run one half; with neither it runs both:

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <version>3.5.0</version>
  <executions>
    <execution>
      <id>weave-models</id>
      <phase>process-classes</phase>
      <goals><goal>java</goal></goals>
      <configuration>
        <mainClass>io.github.digitalsmile.goldberry.weaver.WeaverMain</mainClass>
        <arguments>
          <argument>${project.build.outputDirectory}</argument>
        </arguments>
        <classpathScope>compile</classpathScope>
      </configuration>
    </execution>
    <execution>
      <id>weave-test-models</id>
      <phase>process-test-classes</phase>
      <goals><goal>java</goal></goals>
      <configuration>
        <mainClass>io.github.digitalsmile.goldberry.weaver.WeaverMain</mainClass>
        <arguments>
          <argument>${project.build.testOutputDirectory}</argument>
        </arguments>
      </configuration>
    </execution>
  </executions>
  <dependencies>
    <dependency>
      <groupId>io.github.digitalsmile</groupId>
      <artifactId>goldberry-weaver</artifactId>
      <version>${goldberry.version}</version>
    </dependency>
  </dependencies>
</plugin>
```

A real Mojo would be nicer — one `<plugin>` block, incremental, no
`<mainClass>` to get wrong — and is a small amount of work whose only awkward
part is that this repository builds with Gradle and would have to write
`META-INF/maven/plugin.xml` itself. It is not built, and this is honest about
that rather than implying otherwise. Nothing above is a workaround for a missing
feature: `process-classes` is where class post-processing belongs, and the weaver
is a program that post-processes classes.

### Any other build, or none

```
java -jar goldberry-weaver.jar target/classes
```

It prints one line per class it wove, exits 0, and exits 1 with a message naming
the member when it refuses a model.

## If you forget

Nothing. A model that is annotated and not woven is bound at run time, which is
the ordinary case — that is ADR-0155. All five annotations are `RUNTIME`-retained
so that the reflective binder can read them.

The one thing you can forget is the `opens` line, in a named module, and the
refusal quotes it back at you.

## What it refuses, and why

Each of these is a failure naming the member — at build time when the weaver runs,
and on the first `Models` call when it did not — because a binding that fails
silently is a control that renders perfectly and never moves. **Both forms refuse
the same list**, which is the point: a model that builds as an image builds as a
jar.

| Refused | Because |
|---|---|
| `static` `@Bind` field | A binding belongs to an instance; a static one is shared by every window in the process |
| `final` `@Bind` field (unless a `Property`) | A value that cannot change is not something to subscribe to |
| an array | Only the *assignment* is observed, so `values[0] = x` would notify nobody. Hold a `List` and assign a new one |
| a path that is not `a.b.c` | The grammar `Bindings` enforces at runtime, checked first ([ADR-0062](adr/0062-bind-is-a-path-and-nothing-else.md)) |
| two members claiming one name | Two features quietly sharing one name presents as a value changing by itself |
| an `@Action` taking two arguments | A control reports either *that* something happened or *what* it should become, never both |
| an `@Action` parameter that is not `String`, `double`, `int`, `boolean` or a box | A valued action crosses as the string the document wrote down |
| a `static` `@Action` | An action changes a model, and a static one has no model to change |
| an abstract or empty `@Model` | Nothing to weave into, or nothing to publish |
| `@Actions` with a `@Bind` field | A class that holds values is a `@Model` |
| `@Actions` with no `@Action` method | It publishes nothing |
| both `@Model` and `@Actions` on one class | A class holds values or it does not |
| a `@Model` extending a `@Model` | Each would get its own listener store and the subclass's would shadow the superclass's, so inherited fields would notify nobody |
| `@Bind(restyle = true)` on a `Property` | No writes to it are rewired, so there is nowhere to put the call |
| `@Markup` without `public static Widget inflate(KdlNode, List<Widget>, Wiring)` | Nothing for the node name to build. Java cannot say this in an annotation, so the build says it |
| two classes claiming one `@Markup` name | A document writing it would get whichever the build saw last |

## Known limits

**Notification is deferred when nothing wove the class.** The sweep points above
cover every path a document takes; a write outside all of them waits for
`Models.refresh`. Woven, there is no deferral at all.

**The order a registry lists its names in differs between the two forms.** The
weaver publishes in class-file order; reflection cannot recover that —
`getDeclaredFields` and `getDeclaredMethods` promise no order — so the reflective
form sorts by member name. It shows up only in the `Bound: ...` list a strict
registry prints when it refuses a name.

**Reading through a binding boxes a primitive.** `Models.observable(model,
"app.gain").get()` on an `int` field allocates, where the old `Property<Integer>`
handed back a box it already held. Writes got faster and reads got slower; the
numbers are in [ADR-0125](adr/0125-a-raw-field-is-woven-into-a-binding.md).
