# Installing

<p class="gb-lede">Add the BOM for the version, the umbrella artifact for the toolkit, and one natives jar per platform you run on.</p>

Every artifact is published under the group `dev.goldberry`. Versions are
calendar versions: `2026.1`, `2026.2`, `2026.2.1`.

> [!NOTE]
> The coordinates below are the newest release, `2026.2`, from Maven Central.
> What is on `master` is published as snapshots of the next line, under
> [Snapshots](#snapshots) below.

## Gradle

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation platform('dev.goldberry:goldberry-bom:2026.2')
    implementation 'dev.goldberry:goldberry'                 // common, natives, core and widgets

    // The native library, one classifier per platform you run on.
    runtimeOnly 'dev.goldberry:goldberry-natives::linux-x64'
    runtimeOnly 'dev.goldberry:goldberry-natives::linux-aarch64'
    runtimeOnly 'dev.goldberry:goldberry-natives::macos-aarch64'
    runtimeOnly 'dev.goldberry:goldberry-natives::windows-x64'
}
```

## Maven

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.goldberry</groupId>
      <artifactId>goldberry-bom</artifactId>
      <version>2026.2</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>dev.goldberry</groupId>
    <artifactId>goldberry</artifactId>
  </dependency>
  <dependency>
    <groupId>dev.goldberry</groupId>
    <artifactId>goldberry-natives</artifactId>
    <classifier>linux-x64</classifier>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

## Snapshots

Every push to `master` publishes a `-SNAPSHOT` of the next version, the
`goldberryVersion` in
[`gradle.properties`](https://github.com/DigitalSmile/goldberry/blob/master/gradle.properties),
to the Central Portal's snapshot repository. To follow `master`, add the
repository and use that version with `-SNAPSHOT`:

```groovy
repositories {
    mavenCentral()
    maven { url = 'https://central.sonatype.com/repository/maven-snapshots/' }
}
```

## Which natives jars to add

The BOM knows versions and not platforms. A POM has no notion of an operating
system, so the umbrella depends on the bindings jar without a classifier and
the platform jars are yours to add.

**Add all four.** `NativeLibrary` picks the right one at run time from
`os.name` and `os.arch`, so the application runs on every machine it is built
or run on. The one-platform form is the one that goes wrong quietly, on a
developer building on macOS for an application that ships to Linux.

| Classifier | Platform |
|---|---|
| `linux-x64` | Linux, x86-64, glibc 2.28 or newer |
| `linux-aarch64` | Linux, 64-bit ARM |
| `macos-aarch64` | macOS on Apple silicon |
| `windows-x64` | Windows, x86-64 |

## Optional modules

Each is one more line under the BOM. Nothing in the core knows they exist until
they are on the path.

| Artifact | Adds | Chapter |
|---|---|---|
| `goldberry-html` | `markdown-view` and `html-view` | [Markdown, HTML and the web](../components/content.md) |
| `goldberry-emoji` | the Noto Color Emoji face, drawn from its paint graphs | [Text, fonts and icons](../guide/text.md) |
| `goldberry-gpu` | GPU composition for every window, and `canvas3d` | [The GPU canvas](../components/gpu.md) |
| `goldberry-media` | `media-player`, `video-view` and `audio-player`, plus `ffmpeg-<target>` classifier jars for each platform | [Audio and video](../components/media.md) |

```groovy
implementation 'dev.goldberry:goldberry-html'
implementation 'dev.goldberry:goldberry-media'
runtimeOnly 'dev.goldberry:goldberry-media::ffmpeg-linux-x64'
```

## Logging

The toolkit logs through SLF4J and binds no implementation. Add one and the
toolkit's diagnostics appear. Add none and you get silence, SLF4J's own
no-provider warning included.

```groovy
runtimeOnly 'ch.qos.logback:logback-classic:1.6.3'
```

## The module path

Goldberry's modules are named: `dev.goldberry.core`, `dev.goldberry.widgets`,
`dev.goldberry.natives`, and `dev.goldberry.html`, `dev.goldberry.emoji`,
`dev.goldberry.media` and `dev.goldberry.gpu` for the optional ones. An
application on the module path requires the two it uses and opens the
packages the toolkit reads at run time:

```java
module com.example.app {
    requires dev.goldberry.core;
    requires dev.goldberry.widgets;

    // The reflective binder reads @Bind fields here, and the parser reads the
    // .kdl and .css resources beside them.
    opens com.example.app to dev.goldberry.core;
}
```

Native access is granted to the one module that touches native code:

```sh
java --enable-native-access=dev.goldberry.natives --module-path lib --module com.example.app/com.example.app.Hello
```

The `opens` is for what the toolkit reads **itself**: markup, a
`Stylesheet.resource`, a `FontSource.resource`. A resource directory is a
package of its own, so `fonts/` beside the class is `com.example.app.fonts`,
and that is the package to open. The `stream` forms,
`FontSource.stream(…, () -> Hello.class.getResourceAsStream("fonts/Forum.ttf"))`
and `Stylesheet.stream(…)`, read the file with your own code and need no
`opens` at all ([Text, fonts and icons](../guide/text.md#shipping-a-face)).

The natives jars are modules too, one per platform:
`dev.goldberry.natives.linux_x64`, `dev.goldberry.natives.linux_aarch64`,
`dev.goldberry.natives.macos_aarch64` and `dev.goldberry.natives.windows_x64`.
They stay on the module path beside everything else. Nothing has to require
them: the library is found there by name.

An application on the class path needs no `opens` and grants
`--enable-native-access=ALL-UNNAMED` instead.

## Read next

<div class="gb-cards">
<a class="gb-card" href="first-java-application.html"><strong>Your first Java application</strong><span>From an empty project to a window with a bound slider.</span></a>
<a class="gb-card" href="../applications.html"><strong>Building an application</strong><span>Values, actions, views and the application class.</span></a>
</div>
