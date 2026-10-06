# Logging and diagnostics

<p class="gb-lede">Goldberry logs through SLF4J and binds nothing. Add a provider and the start-up timeline, the frame timings and the platform's own libraries appear under names you can level.</p>

By the end of this chapter you can see where a slow start or a slow frame
went, silence a native library by name, read which way a window presents,
set the properties that change the toolkit's behaviour at run time, and
recognise the failure messages that come up most.

```groovy
runtimeOnly 'ch.qos.logback:logback-classic:1.6.3'
```

Add nothing and you get silence, including from SLF4J itself, which would
otherwise print a "no providers were found" warning.
Binding a provider is an application's decision and never a library's.

## A logback configuration

```xml
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder><pattern>%d{HH:mm:ss.SSS} %-5level %logger{0} - %msg%n</pattern></encoder>
  </appender>

  <!-- The toolkit. DEBUG is the window lifecycle and backend start-up;
       TRACE adds the start-up timeline and a line per frame. -->
  <logger name="dev.goldberry" level="${goldberry.log.level:-DEBUG}"/>

  <!-- What the platform's own libraries say. -->
  <logger name="native" level="warn"/>
  <logger name="native.glib.libayatana-appindicator" level="off"/>

  <root level="INFO"><appender-ref ref="CONSOLE"/></root>
</configuration>
```

`goldberry.log.level` is a variable this file reads, not a property the
toolkit knows. The showcase's `logback.xml` is the one above, and
`-Dgoldberry.log.level=TRACE` is how its run switches the toolkit to trace.

## The start-up timeline

At `TRACE`, one table is printed after the first frame:

```text
start-up timeline (866.6ms to here):
     533.8ms    +533.8ms  runtime starting
     559.6ms     +25.8ms  libgoldberry mapped (1.9ms)
     722.6ms    +162.9ms  SDL video subsystem up (99.2ms)
     866.6ms    +117.1ms  first frame presented
```

Each phase is measured from process start, so the first row says how long
the JVM took before the toolkit saw a line of your code. `Startup.mark("my
phase")` and `Startup.time("my phase", work)` add your own rows, and
`Startup.sinceProcessStart()` is what the showcase's status bar reads. What
the numbers should be, and how to make them smaller, is in
[Starting fast](../performance/startup.md).

## Frames

At `TRACE`, every painted frame logs one line:

```text
frame 312 in 1840us: buffer 12, paint 1105 (begin 40, draw 980, end 85), present 723
```

The stage timings are `FrameStats`, which `host.frames()` reads live and a
`hud` draws on screen. Two properties go deeper, and both are properties
rather than log levels because a diagnostic that costs an `isTraceEnabled()`
per element per frame would be measuring itself:

- `-Dgoldberry.trace.frames=true` counts what each frame did to the element
  tree: elements built, styles resolved, subtrees thrown away. `=all` reports
  the quiet frames too.
- `-Dgoldberry.trace.input=true` reports every pseudo-class the pointer and
  the keyboard set, one line per node of the chain.

[Measuring](../performance/measuring.md) says what to do with the numbers.

## The platform's own libraries

What GLib and SDL log is an ordinary SLF4J event, on
`native.<library>.<subsystem>`: `native.glib.libayatana-appindicator`,
`native.sdl.video`. Nothing of theirs reaches stderr past your configuration.
Level, route or switch them off by name like anything else.

`-Dgoldberry.log.native=false` installs no bridge and gives each library its
stderr back, which is what to reach for when something in the platform layer
is being debugged and a logging configuration might be hiding it.
`-Dgoldberry.log.glib.writer=true` also installs a GLib structured-log
writer, only if nothing else in the process sets one.

## Which way a window presents

Each window logs one line at `INFO` when it starts presenting a different
way, tagged `[GPU]` or `[CPU]`, with the reason.
The same fact is `window.presentation()`, with `label()` for a status bar,
and `onPresentationChange(listener)` for a change mid-run: a GPU that failed,
or `goldberry.gpu.composite=auto` taking the GPU for a layer and giving it
back.

## What this build can do

```java
Set<Capability> can = Goldberry.capabilities();
```

The desktop integrations are compiled into the native library only where the
machine that built it had the headers. `Goldberry.capabilities()` answers
from the library's own record, before any window is open, and is empty
where there is no native library at all. The
values are in [Windows, popups and the host](windows.md#capabilities).

## Properties an application can set

All read with `-Dname=value` on the command line, or set before the toolkit
starts.

| Property | Values | What it does |
|---|---|---|
| `goldberry.backend.videoDriver` | an SDL driver: `x11`, `wayland`, `dummy`, … | overrides the video driver. On a Linux Wayland session the toolkit asks for `x11,wayland` by default. `dummy` opens no window and has no popups |
| `goldberry.backend.vsync` | `false` | turns the frame loop's pacing to the display off |
| `goldberry.frame.rate` | a number; `0` | holds the loop to that many frames a second instead of the display's rate; `0` measures the unthrottled loop |
| `goldberry.paint.threads` | a count; `0` | pins Blend2D's worker count; `0` paints synchronously |
| `goldberry.gpu` | `off`, `auto` | whether any GPU is used at all |
| `goldberry.gpu.composite` | `never`, `auto`, `always` | whether a window presents through the GPU: never, while it shows GPU layers, or from its first frame. `always` is the default |
| `goldberry.gpu.driver` | `metal`, `vulkan`, `direct3d12` | names the GPU driver, with `goldberry-gpu` on the path |
| `goldberry.gpu.debug` | `true` | the GPU driver's validation layer |
| `goldberry.input.primary` | `ctrl`, `meta` | what `Primary` resolves to, instead of reading `os.name` |
| `goldberry.motion.reduced` | `reduce`, `full` | overrides the desktop's reduced-motion answer; anything else asks the desktop |
| `goldberry.popup.settle` | milliseconds | how long to disbelieve a focus-lost after a popup opens, for a compositor nobody has run against |
| `goldberry.native.library` | a path | loads `libgoldberry` from there instead of the natives jar |
| `goldberry.webview.library` | a path | the same for `libgoldberry-webview` |
| `goldberry.media.libdir` | a directory | loads FFmpeg from there, with `goldberry-media` on the path |
| `goldberry.media.ffmpegLog` | `true` | lets FFmpeg's own warnings through to stderr |
| `goldberry.log.native` | `false` | no bridge: native libraries write to stderr again |
| `goldberry.log.glib.writer` | `true` | also catches GLib's structured messages |
| `goldberry.trace.frames` | `true`, `all` | counts what each frame did to the element tree |
| `goldberry.trace.input` | `true` | reports every pseudo-class input sets |
| `goldberry.css.lint` | `true` | lints the application's own stylesheets against everything in force whenever the launcher reads them, and logs each finding at warn. The development switch: dead declarations, rules a lenient parse dropped, and `@media` blocks that can never apply |

`goldberry.golden.update`, `goldberry.golden.scales`, `goldberry.gpu.required`
and the other `*.required` properties belong to the toolkit's own test tasks
and do nothing in an application.

## Failure messages, and what they mean

| You see | It means | Go to |
|---|---|---|
| `SDL_Init failed: No available video device`, with a line naming `-XstartOnFirstThread` | on macOS, `main` is not on the process's first thread. Add the flag to the `java` command | [Windows, popups and the host](windows.md#threads) |
| `no action named "app.sav" is bound. Bound: …` | the document names an action no model in the inflater publishes. Usually the inflater was built from a list other than `models()`, or the name is misspelled | [Markup](markup.md#strict-by-default) |
| `nothing is bound to "app.gian". Bound: …` | the same, for a `bind=` | [Markup](markup.md#strict-by-default) |
| `"!prefs.frost" is not a binding path` | a `bind=` holds an expression. Only dotted paths are allowed | [Markup](markup.md#bind-is-a-path-and-nothing-else) |
| `unknown node "buton"; registered: …` | the node name is not in any catalogue on the path | [Markup](markup.md#parsing-and-inflating) |
| `… its package is not open to this module. Add opens com.example.app to dev.goldberry.core;` | a `@Model` in a named module, bound at run time, needs its package opened | [Model weaving](../weaving.md#a-model-in-a-named-module-opens-its-package) |
| `libgoldberry not found at …` | the natives jar for this platform is not on the path, or the library was not built | [Native image](../native.md), and `-Dgoldberry.native.library` above |
| `cannot find -lz` while building the native library | the linker needs `zlib1g-dev`, not only `zlib1g` | [Native image](../native.md) |
| `the emoji face is not on the module path …` | `Font.bundled(BundledFont.EMOJI, …)` without `goldberry-emoji` | [Text, fonts and icons](text.md#emoji) |
| `dropping "transition": width … is not a valid value` | a transition names a property outside the whitelist | [Styling](styling.md#transition-and-animation) |
| `unsupported at-rule "@font-face"` | the CSS subset has `@media`, `@starting-style` and `@keyframes` and nothing else. In an application's sheet the block is dropped with this warning; in the toolkit's own it is a `CssSyntaxException` | [Styling](styling.md#strict-and-lenient-sheets) |
| `app.css, line 42: dropping "lane::before": …` | a lenient sheet left out one rule that asked for something outside the subset; the rest of the sheet is in force | [Styling](styling.md#strict-and-lenient-sheets) |
| `app.css, line 7: "letter-spacing" is not a property this toolkit has` | every declaration of that property in the sheet does nothing; said once per property per sheet | [Styling](styling.md#properties) |
| `easing "ease-in-out" is read as ease-enter` | at info: CSS's keyword runs on the system curve the table names | [Styling](styling.md#transition-and-animation) |
| `nowhere to put a menu` logged by the showcase | the video driver has no popup windows, or the anchor was not painted yet | [Windows, popups and the host](windows.md#in-a-window-of-its-own) |

## Read more

- [Measuring](../performance/measuring.md): the HUD and the benchmarks
