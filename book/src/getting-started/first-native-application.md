# Your first native application

<p class="gb-lede">The counter from the previous page as one executable: no JDK on the target machine, the native library inside the file, and a window up in a tenth of a second.</p>

A GraalVM native image is a closed world. It has to know every class, every
resource and every foreign function before it runs, and it cannot bind a model
by reflection. Goldberry was designed for that
([ADR-0127](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0127-the-binding-schema-fits-a-closed-world.md)), and the
toolkit's jars carry most of what an image needs. Four things are yours:

1. **Weave the model**, so that assignments notify without reflection.
2. **Declare your own resources**, by glob.
3. **Trace one run**, for what depends on your application.
4. **Run `native-image`.**

The recipe below is the showcase's own, which CI builds on all three platforms
on every release tag ([ADR-0337](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0337-the-native-showcase-is-built-on-every-platform.md)).
[Native image](../native.md) explains every flag and every trap in depth.

## Before you start

- A GraalVM Community for JDK 25, with `GRAALVM_HOME` pointing at it. A stock
  JDK has no `native-image`.
- On Linux, a C toolchain and zlib's development package:

```sh
sudo apt install build-essential zlib1g-dev        # Debian, Ubuntu
sudo dnf install gcc glibc-devel zlib-devel libstdc++-static
```

Without `zlib1g-dev` the build spends a minute on analysis and fails at the
last step with `cannot find -lz`.

<div class="gb-steps">
<div>

**Weave the model.** The weaver rewrites `Counter.class` so that every write
to a `@Bind` field calls a synthesized setter that notifies. It is one jar
with no dependencies and a `main` that takes a directory of classes. The task
needs the classes and everything they were compiled against, because it
regenerates stack-map frames ([Model weaving](../weaving.md#gradle)).

```groovy
configurations { goldberryWeaver }
dependencies { goldberryWeaver 'dev.goldberry:goldberry-weaver' }

def weaveModels = tasks.register('weaveModels', JavaExec) {
    dependsOn tasks.compileJava
    def classes = tasks.compileJava.flatMap { it.destinationDirectory }
    classpath = files(configurations.goldberryWeaver, classes, sourceSets.main.compileClasspath)
    mainClass = 'dev.goldberry.weaver.WeaverMain'
    argumentProviders.add({ ['--models', classes.get().asFile.absolutePath] } as CommandLineArgumentProvider)
    inputs.dir(classes)
    outputs.file(layout.buildDirectory.file('tmp/weaveModels/stamp'))
    outputs.upToDateWhen { false }
    doLast { layout.buildDirectory.file('tmp/weaveModels/stamp').get().asFile.text = 'woven' }
}
tasks.named('jar') { mustRunAfter weaveModels }
```

The weaver's version comes from the BOM, like every other artifact. The stamp
file is deliberate: declaring the compiler's own output directory as this
task's output makes Gradle recompile the whole module on every build
([ADR-0398](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0398-the-build-declares-what-it-actually-writes.md)).

</div>
<div>

**Declare your resources.** The document and the stylesheet are read by name
at run time, and a trace only records what one run happened to touch. Globs
are finite ([ADR-0160](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0160-a-modules-own-resources-are-declared-not-traced.md)).
The file goes in a directory the agent never writes to, so a new trace cannot
overwrite it:

`src/main/resources/META-INF/native-image/com.example/hello-manual/reachability-metadata.json`

```json
{
  "resources": [
    { "module": "com.example.hello", "glob": "com/example/hello/*.kdl" },
    { "module": "com.example.hello", "glob": "com/example/hello/*.css" },
    { "module": "com.example.hello", "glob": "logback.xml" },
    { "glob": "dev/goldberry/natives/**" }
  ]
}
```

The last line is the native library itself. It lives in the classifier jar,
which the image carries as a class-path resource and unpacks to a temporary
file on first use ([ADR-0159](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0159-a-native-image-carries-its-own-library.md)).

The `logback.xml` line is there if you log through Logback. Logback asks a
`ClassLoader` for the file, so the agent records it without a module, and in
an image built from the module path it is then not there at all: Logback
starts with no appenders and the image prints nothing
([Native image](../native.md#two-metadata-directories-traced-and-written)).

</div>
<div>

**Trace one run.** What the toolkit's jars cannot declare is what depends on
your run: the service the widget catalogue is found through, the upcall stubs
the native code calls back into, the JDK's text resources, and whatever your
logging configuration reflects over. GraalVM's agent records them. Run the
woven application once, headless, for a hundred frames, and keep the output
under `src/main/resources` so it is reviewed in a diff
([ADR-0156](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0156-the-image-s-metadata-is-traced-not-written.md)):

```groovy
tasks.register('nativeImageMetadata', Exec) {
    dependsOn weaveModels, tasks.jar
    def output = layout.projectDirectory.dir('src/main/resources/META-INF/native-image/com.example/hello')
    doFirst {
        def split = splitPaths()
        def command = ["${System.getenv('GRAALVM_HOME')}/bin/java",
                '--enable-native-access=dev.goldberry.natives',
                "-agentlib:native-image-agent=config-output-dir=${output.asFile.absolutePath}",
                '-Dgoldberry.backend.videoDriver=dummy']
        if (System.getProperty('os.name').toLowerCase().contains('mac')) {
            command += '-XstartOnFirstThread'
        }
        command += ['--module-path', split.modules, '-cp', split.natives,
                '--module', 'com.example.hello/com.example.hello.Hello', '--frames=120']
        commandLine command
    }
}
```

`splitPaths` is a small helper, used by both tasks. Each natives classifier
jar names its own module, `dev.goldberry.natives.linux_x64` and so on, but
nothing `requires` one, and an image takes only the modules that are
resolved. So for an image they go on the class path, where the library is a
plain resource
([Native image](../native.md#the-natives-jar-is-a-module-now)):

```groovy
def splitPaths = {
    def jars = configurations.runtimeClasspath.files
    def natives = jars.findAll { it.name.startsWith('goldberry-natives-') && it.name =~ /-(linux|macos|windows)-/ }
    def modules = (jars - natives) + [tasks.jar.archiveFile.get().asFile]
    [modules: modules*.absolutePath.join(File.pathSeparator), natives: natives*.absolutePath.join(File.pathSeparator)]
}
```

The agent writes `reachability-metadata.json` beside your manual one.
`native-image` reads every `META-INF/native-image/**` it finds, so the two
are merged for the tool and kept apart for you.

</div>
<div>

**Build the image.**

```groovy
tasks.register('nativeImage', Exec) {
    dependsOn weaveModels, tasks.jar
    def target = layout.buildDirectory.file('native/hello')
    doFirst {
        def split = splitPaths()
        commandLine "${System.getenv('GRAALVM_HOME')}/bin/native-image",
                '--module-path', split.modules,
                '-cp', split.natives,
                '--module', 'com.example.hello/com.example.hello.Hello',
                '-o', target.get().asFile.absolutePath,
                '--enable-native-access=dev.goldberry.natives',
                '-H:+ReportExceptionStackTraces'
    }
}
```

```sh
./gradlew nativeImageMetadata nativeImage
./build/native/hello
```

The result is one file. No launcher, no `lib/` directory, nothing to set.

</div>
</div>

## What the toolkit's jars bring

Nothing above names a Goldberry resource, a foreign function or a class
initialization policy, because the jars carry their own metadata and
`native-image` reads it from every jar on the path:

| Jar | Carries |
|---|---|
| `goldberry-natives` | the descriptor of every bound C function, written because the function exists rather than because a run reached it ([ADR-0339](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0339-a-foreign-call-is-registered-because-it-exists-not-because-a-run-reached-it.md)), and the class-initialization policy that makes a downcall handle a constant ([ADR-0173](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0173-a-bound-function-is-a-holder-and-its-handle-is-a-constant.md)) |
| `goldberry-core` | the fonts, the icon table, the two themes |
| `goldberry-widgets` | the catalogue's stylesheets |
| `goldberry-html`, `goldberry-media` | their stylesheets, when they are on the path |

> [!WARNING]
> **The one thing that fails silently is speed.** A downcall handle that is not
> a compile-time constant costs a factor of 450 per call, and the image builds,
> runs and paints correctly at forty times the frame cost. The policy that
> prevents it travels in the natives jar. If you pass your own
> `--initialize-at-run-time` or `--initialize-at-build-time` flags, read
> [Native image](../native.md#the-one-flag-the-frame-rate-depends-on) first.

## What to expect

| | The showcase, measured from `exec` |
|---|---|
| The file | 41 MiB on Linux, library included |
| The window | open at about 120 ms |
| The first frame | about 520 ms with the GPU module, 365 ms without |
| A headless frame | about 1.0 ms |

The counter is a much smaller application and starts no slower
([ADR-0506](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0506-start-up-is-timed-from-the-kernels-clock-and-a-native-window-is-up-in-a-tenth-of-a-second.md)).

> [!NOTE]
> A screen the trace never reached contributes nothing to the metadata. After
> adding a screen, re-run `nativeImageMetadata` and read the diff. The
> showcase's own image once shipped without the light theme's stylesheet for
> exactly this reason, which is why resources are declared by glob and not
> traced.

## Read next

<div class="gb-cards">
<a class="gb-card" href="../native.html"><strong>Native image</strong><span>Every flag, both metadata directories, and why the library is carried rather than linked.</span></a>
<a class="gb-card" href="../weaving.html"><strong>Model weaving</strong><span>What the weaver does to a class, and what it refuses.</span></a>
<a class="gb-card" href="../performance/startup.html"><strong>Starting fast</strong><span>Where the first half second goes, on the JVM and in an image.</span></a>
</div>
