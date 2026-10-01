# Concept

<p class="gb-lede">Goldberry draws every pixel itself, describes a screen as data, and keeps the native code behind one boundary. This page is the idea in five parts and what each one buys.</p>

## One rendering pipeline, everywhere

Goldberry does not wrap the platform's widgets and does not embed a browser. A
button on Linux, Windows and macOS is the same button: the same CSS rule, the
same Yoga layout, the same Blend2D rasterization, the same HarfBuzz-shaped text
in the same embedded fonts. The platform supplies a window, input events, the
clipboard and a surface to present into, through SDL3. Everything above that
line is Java and runs identically on every desktop.

The result is deterministic. A frame is the same bytes on every machine, which
is what lets the toolkit test itself with golden images and lets an application
do the same ([ADR-0002](../adr/0002-cpu-rasterization-with-blend2d.md),
[ADR-0003](../adr/0003-sdl3-as-the-only-desktop-backend.md)).

## A screen is a description

A widget is an immutable Java record with a pure `build()`. The toolkit calls
`build()`, diffs the result against the element tree it kept from the last
frame, and updates only what changed. State that must survive a rebuild, a hover
or a caret, lives on the element rather than on the widget
([ADR-0004](../adr/0004-three-tree-retained-declarative-model.md),
[ADR-0052](../adr/0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)).

The same tree can be written as KDL markup, and every built-in widget exists
three ways: a record, a node name and a CSS type
([ADR-0059](../adr/0059-a-control-is-a-record-a-node-and-a-rule.md)).

<div class="gb-pair">

```kdl
row id="confirm" {
  spacer
  button press="dismiss" "Cancel"
  button class="danger" press="delete" "Delete"
}
```

```java
new Row(
        new Spacer(),
        new Button("Cancel", this::dismiss),
        new Button("Delete", this::delete).styled("danger")
)
```

</div>

Markup names things and never contains code. A `press=` names an action, a
`bind=` names a value, an `icon=` names an icon. Each resolves against a
registry the application supplied, and a name that resolves to nothing fails
when the document is inflated rather than when the user clicks
([ADR-0062](../adr/0062-bind-is-a-path-and-nothing-else.md)).

## Data flows down, events flow up

A control never owns its value. It is handed an `Observable` and draws what it
reads. When the user acts, the control raises an action, the application's
handler assigns a field, and the new value flows back down. A checkbox whose
tick will not move is a handler that did not change the state, which is where
the bug is ([ADR-0063](../adr/0063-data-flows-down-events-flow-up.md)).

The model is plain Java: a class of fields marked `@Bind`, and methods marked
`@Action`. On the JVM the toolkit binds them reflectively at run time. For a
native image a build step rewrites the compiled class so that every assignment
notifies directly, with no reflection anywhere
([ADR-0155](../adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md)).
[Model weaving](../weaving.md) is the whole story.

## Real CSS, real flexbox

Styling is a genuine subset of CSS: selectors, the cascade in layers, custom
properties, inheritance, transitions on a frame clock, transforms and opacity.
Layout is flexbox, through Yoga bound directly rather than reimplemented
([ADR-0005](../adr/0005-css-subset-and-kdl-as-the-contracts.md)).

Every colour a control uses is a `--gb-*` token, so switching from the Nord
dark theme to the Nord light one restyles every control though no rule names a
colour. Density is three tokens on `:root`. Markup and stylesheets hot-reload
while the application runs ([Styling](../guide/styling.md)).

## Light to ship

The toolkit has no third-party Java dependencies. The native libraries are
statically linked into one `libgoldberry` per platform, shipped as a classifier
jar and bound through hand-written FFM calls that live in one module
([ADR-0007](../adr/0007-jpms-modules-enforce-the-native-boundary.md),
[ADR-0010](../adr/0010-hand-written-ffm-bindings.md)).

An application runs as a plain jar on any JDK 25, or compiles with GraalVM
into one executable with the library inside it. The native showcase is 41 MiB,
opens its window in about 120 ms and paints a headless frame in about a
millisecond ([Native image](../native.md),
[ADR-0506](../adr/0506-start-up-is-timed-from-the-kernels-clock-and-a-native-window-is-up-in-a-tenth-of-a-second.md)).

## What it is built on

| Library | Role | Where it lives |
|---|---|---|
| Blend2D | 2D rasterization, with its PNG, JPEG and QOI codecs | inside `libgoldberry` |
| Yoga | flexbox layout | inside `libgoldberry` |
| HarfBuzz | text shaping | inside `libgoldberry` |
| SDL3 | windows, input, clipboard, popups, tray, `SDL_GPU` | inside `libgoldberry` |
| md4c | Markdown parsing | inside `libgoldberry` |
| libwebp | WebP decoding | inside `libgoldberry` |
| FFmpeg | audio and video decoding | `goldberry-media`, as separate shared libraries |
| Inter, JetBrains Mono | the UI and code typefaces | inside `goldberry-core` |
| Lucide | 1544 icons as path data | inside `goldberry-core` |
| Noto Color Emoji | the emoji face | `goldberry-emoji` |

Everything in the table is open source, and the licences travel inside every
jar under `META-INF/`
([ADR-0015](../adr/0015-licensing-and-third-party-disclosure.md)).

## Read next

<div class="gb-cards">
<a class="gb-card" href="architecture.html"><strong>Architecture</strong><span>The layers, the three trees, the frame loop and the module graph.</span></a>
<a class="gb-card" href="limitations.html"><strong>Limitations</strong><span>What is not built, what is deliberately out, and where each is tracked.</span></a>
<a class="gb-card" href="../getting-started/requirements.html"><strong>Getting started</strong><span>Install it and open a window.</span></a>
</div>
