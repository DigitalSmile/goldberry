# Ahead-of-time binding

<p class="gb-lede">An ordinary jar binds its models while it runs and needs no build step. A native image, or a library that ships widgets, adds one build step: the weaver.</p>

## When you need it

| You are building | Add the weaver? | What it does for you |
|---|---|---|
| An application run on the JVM: `java -jar`, `gradle run`, an IDE | No | Nothing to add. Models are bound at run time |
| A GraalVM native image | Yes, `--models` | Binds the models at build time, so the image needs no reflection |
| A library with `@Markup` widgets | Yes, `--catalog` | Registers the widgets, so a document finds them by name |

The annotations and the behaviour are the same either way. A model that works
in a jar works in an image.

## What it does

A model is plain Java. Markup binds to its fields and calls its methods:

```java
@Model
public final class Settings {
    @Bind("app.gain") private int gain = 40;

    @Action("app.louder") private void louder() { gain++; }
}
```

```kdl
slider bind="app.gain" min=0 max=100
button "Louder" press="app.louder"
```

`gain++` moves the slider. [Building an application](applications.md) covers
the annotations. Java cannot observe a field write from outside the class, so
the weaver rewrites the compiled classes:

- each assignment to a `@Bind` field, in the model or in any class beside it,
  becomes a call that stores the value and notifies the controls bound to it;
- the model gets a table of its bindings and actions, built from the
  annotations, so nothing is looked up reflectively;
- a class that neither is a model nor writes to one is left untouched.

It runs between `compileJava` and `jar`, on the `.class` files, using the JDK's
class-file API. It needs no agent, no `opens` and no code generated at run time.

## Adding it to a build

The weaver is one jar, `dev.goldberry:goldberry-weaver`, with no dependencies
beyond the JDK. It takes directories of compiled classes and rewrites them in
place. `--models` and `--catalog` select one half; with neither, it runs both.

### Gradle

```groovy
configurations { goldberryWeaver }
dependencies { goldberryWeaver "dev.goldberry:goldberry-weaver:$goldberryVersion" }

def weave = tasks.register('weaveModels', JavaExec) {
    dependsOn tasks.compileJava
    def classes = tasks.compileJava.flatMap { it.destinationDirectory }
    classpath = files(configurations.goldberryWeaver, classes, sourceSets.main.compileClasspath)
    mainClass = 'dev.goldberry.weaver.WeaverMain'
    argumentProviders.add({ [classes.get().asFile.absolutePath] } as CommandLineArgumentProvider)
    inputs.dir(classes)
    // A stamp file, not outputs.dir(classes): sharing javac's output directory
    // makes Gradle recompile the module from scratch on every build.
    outputs.file(layout.buildDirectory.file('tmp/weaveModels/stamp'))
    outputs.upToDateWhen { false }
    doLast { layout.buildDirectory.file('tmp/weaveModels/stamp').get().asFile.text = 'woven' }
}
tasks.named('classes') { dependsOn weave }
```

### Maven

Run it with `exec-maven-plugin` in the `process-classes` phase:

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <version>3.5.0</version>
  <executions>
    <execution>
      <id>weave</id>
      <phase>process-classes</phase>
      <goals><goal>java</goal></goals>
      <configuration>
        <mainClass>dev.goldberry.weaver.WeaverMain</mainClass>
        <arguments>
          <argument>${project.build.outputDirectory}</argument>
        </arguments>
        <classpathScope>compile</classpathScope>
      </configuration>
    </execution>
  </executions>
  <dependencies>
    <dependency>
      <groupId>dev.goldberry</groupId>
      <artifactId>goldberry-weaver</artifactId>
      <version>${goldberry.version}</version>
    </dependency>
  </dependencies>
</plugin>
```

### Any other build

```
java -cp goldberry-weaver.jar:target/classes:<compile classpath> \
     dev.goldberry.weaver.WeaverMain target/classes
```

Put the classes and their compile classpath on the classpath. The weaver loads
your types to rebuild the methods it rewrites, and without them it fails with
`Could not resolve class`. It prints one line per class it changed and exits
with 1, naming the member, when it refuses a model.

## Widget catalogues

A module that ships widgets annotates each with `@Markup("name")`. The
`--catalog` half collects them into the module's `WidgetCatalog` and declares
it in both `module-info.class` and `META-INF/services`, so the module works on
the module path and on the class path. `Widgets.inflater(...)` then finds every
catalogue on the path, and an application uses the widgets without naming the
module.

There is no run-time equivalent: finding annotated classes while the program
runs would mean scanning the class path. A widget library runs `--catalog` in
every build. [Writing a widget](guide/writing-a-widget.md) covers the rest.

## Without it: the sweep

A model that is not woven is bound at run time. Reads are exact. Writes are
found by comparing each field with its last value, which happens:

- after every action a document dispatches;
- at the start of every frame, for the models `Application.models()` returns;
- whenever you call `Models.refresh(model)`.

A field written from anywhere else, such as a background callback, needs the
explicit call:

```java
job.onFinished(text -> {
    model.status = text;
    Models.refresh(model);   // does nothing once the class is woven
});
```

In a named module, the model's package must be open to the toolkit:

```java
opens com.example.app to dev.goldberry.core;
```

The error message quotes this line if it is missing. A class-path application
needs nothing.

## What it refuses

The weaver at build time, and the run-time binder on first use, refuse the same
models. Each refusal names the member.

| Refused | Because |
|---|---|
| a `static` `@Bind` field | A binding belongs to one instance |
| a `final` `@Bind` field, unless a `Property` | A value that cannot change has nothing to notify |
| an array | Only assignment is observed, so `values[0] = x` notifies nobody. Assign a new `List` |
| a path that is not `a.b.c` | The binding path grammar |
| two members with one name | One name, one value |
| an `@Action` with two parameters | An action reports that something happened, or what it should become |
| an `@Action` parameter that is not `String`, `double`, `int`, `boolean` or a box | A valued action arrives as the string the document wrote |
| a `static` `@Action` | An action changes a model instance |
| an abstract or empty `@Model` | Nothing to bind |
| `@Actions` with a `@Bind` field, or with no `@Action` | A class with values is a `@Model`; one with no actions publishes nothing |
| `@Model` and `@Actions` on one class | Pick one |
| a `@Model` extending a `@Model` | Inherited fields would notify nobody |
| `@Bind(restyle = true)` on a `Property` | Writes to a `Property` are not rewritten, so there is nowhere to restyle from |
| `@Markup` without `public static Widget inflate(KdlNode, List<Widget>, Wiring)` | The node name has nothing to build |
| two classes with one `@Markup` name | A document would get whichever loaded last |
