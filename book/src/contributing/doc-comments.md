# Writing a doc comment

<p class="gb-lede">A doc comment tells the reader what the object is, how to use it, and how it works. Then it points at the chapter of this guide that says more. It does not cite the decision log.</p>

## Who reads it

A doc comment is read in three places: in an IDE as a tooltip over a name, on
a javadoc site after a release, and in the source by the next maintainer. Only
the last of those has the repository. The first two have the published jar and
a browser, so everything a comment leans on has to be reachable from there.
This guide is, at `https://goldberry.dev/docs/`. The decision log is not: it is
read on GitHub, and a reader with a tooltip that names a record has nowhere to
go.

So a comment explains, and links the guide. The reasoning behind a choice lives
in the log, which is read on GitHub.

## The shape

A type's comment has four parts, in this order. The first is required; the
others appear when there is something to say.

1. **What it is**, in one sentence a user of the type would write. Name the
   thing, not the mechanism. *A row of a word and, sometimes, an icon that
   opens a target when pressed* says what a `Link` is; *the state the inflater
   builds for `link`* says where it came from.
2. **How to use it.** For a widget, the markup and the Java that make one. For
   an API, the call. A short fenced sample beats a paragraph.
3. **How it works.** The mechanics a user needs in order to predict the
   object: what it holds, what it closes, which thread it runs on, what
   happens at the edges. The reason for a choice that would otherwise surprise
   is a sentence here, in plain words: *the icon is closed by the state that
   made it, because nothing else knows it exists*. A citation is not a reason.
4. **Read more**, as the last paragraph: one link to the chapter of this guide
   that covers the type, anchored to its heading when it has one.

```java
/// Text that does something when pressed: an in-app action, an external
/// target, or both.
///
/// ```kdl
/// link action="app.show-docs" "Read the docs"
/// link href="https://goldberry.dev" "Goldberry"
/// ```
///
/// `action=` runs through the application's action registry. `href=` is
/// handed to the desktop's own handler for its scheme, and the link says so:
/// it carries a trailing `external-link` icon and its accessible name ends in
/// "opens outside this window", because a colour cannot say that. The toolkit
/// keeps no history, so `visited` is the application's to set.
///
/// Read more: [Text and links](https://goldberry.dev/docs/components/text.html#link).
public record Link(...) { }
```

A member's comment is one or two sentences: what it does and, when the reader
would otherwise be surprised, why. It links the guide only when it needs a
different page than its type does.

A `package-info.java` says what the package is for and whom it is exported to,
and its last paragraph links the chapter the package belongs to. Every package
of a published module has such a link, so a reader who lands anywhere in the
javadoc is one click from the guide. `SourceDocsTest` in build-logic holds that.

## The link

A link into this guide is absolute, names the page mdBook writes, and anchors
a heading when one fits:

```text
https://goldberry.dev/docs/components/forms.html#text-input
https://goldberry.dev/docs/guide/input.html#focus
https://goldberry.dev/docs/layout/index.html
```

In a `///` comment it is a Markdown link, `[Fields and forms](https://…)`. In
a `/** */` comment it is `<a href="https://…">Fields and forms</a>`. The text
is the chapter's title, or the heading's, so the reader knows where they are
going. Keep the line short enough for the formatter: a `///` line at the top of a
file may run to 120 columns, but inside a type the formatter wraps a line
longer than 120 minus its indent (116 at four spaces, 112 at eight), and the
half it pushes down is not a comment any more. A line that is one backtick
code span is never broken. `tools/book/guide_links.py` reports a line the
formatter would break.

An anchor is the heading as mdBook writes it: lower case, a hyphen for each
space, code marks and punctuation dropped. *The cascade: four layers* is
`the-cascade-four-layers`; *`text-input`* is `text-input`. `SourceDocsTest`
fails on a link to a page the book does not have, or to a heading the page does
not have, so a renamed heading is found by the build and not by a reader.

## What a comment does not do

- **Cite a record.** A record number means nothing to a reader of the javadoc.
  Say the rule: *data flows down and events flow up, so the widget never writes
  the value it shows*. `SourceDocsTest` fails on a record number in a Java,
  Gradle or workflow file.
- **Cite a section of a working document.** A section of a specification under
  `docs/` is a file in the repository, not a page a user has. State the rule, and link the
  chapter that states it.
- **Quote a specification at itself.** A comment that says *§2: "External
  links carry a trailing icon"* is a footnote. *An external link carries a
  trailing icon* is documentation.
- **Narrate the history.** What a type used to do, and the bug that changed
  it, belong in the log and in `git log`. The comment describes the type as
  it is. The exception is a test, whose comment says what broke and how the
  test would catch it again, because that is what the test is for.
- **Link a type another module owns with `[Type]`.** Doclint resolves a
  Markdown reference against the imports, and an import for a doc link alone
  is kept by the formatter only when the type is on the compile path. A name
  from another module goes in backticks, with a link to the guide.

## A test's comment

A test is documentation of a rule, so its comment says the rule, what breaks
when the rule is broken, and how the test sees it. Its `@DisplayName` reads as
a sentence with no numbers in it: *data flows down and events flow up*, not
a record number. An assertion message explains the failure to the person reading a
red build, and says what to do about it.

## Which chapter a package links

The chapter a package's types link is the one that documents them to a user.
Where two fit, the more specific one wins: a `Slider` links *Values and
progress*, not *The catalogue*.

| Module and package | Chapter |
|---|---|
| `dev.goldberry` (the host, the application, the launcher) | [Building an application](../applications.md), [Windows, popups and the host](../guide/windows.md) |
| `dev.goldberry.bind.*` | [Markup](../guide/markup.md#the-four-registries), [Building an application](../applications.md#values) |
| `dev.goldberry.css.*` | [Styling](../guide/styling.md); contrast under [The design system](../guide/design-system.md#colour) |
| `dev.goldberry.drive` | [Testing an application](../guide/testing.md#driving-input) |
| `dev.goldberry.frame`, `dev.goldberry.stats` | [What a frame costs](../performance/index.md), [Measuring](../performance/measuring.md) |
| `dev.goldberry.icon`, `dev.goldberry.image.*`, `dev.goldberry.text.*` | [Text, fonts and icons](../guide/text.md); the QR encoder under [Canvas, images and QR codes](../components/drawing.md#qr-code) |
| `dev.goldberry.input.*` | [Input and focus](../guide/input.md) |
| `dev.goldberry.kdl`, `dev.goldberry.reload`, `dev.goldberry.widgets.markup` | [Markup](../guide/markup.md) |
| `dev.goldberry.layout` | [How layout works](../layout/index.md) |
| `dev.goldberry.motion` | [The design system](../guide/design-system.md#motion); the clock under [Testing an application](../guide/testing.md#the-virtual-clock) |
| `dev.goldberry.offscreen` | [Testing an application](../guide/testing.md#pictures) |
| `dev.goldberry.paint.*` | [Architecture](../overview/architecture.md#the-three-trees), [Keeping frames cheap](../performance/frames.md) |
| `dev.goldberry.platform` | [Logging and diagnostics](../guide/logging.md#what-this-build-can-do) |
| `dev.goldberry.render.*` | [Windows, popups and the host](../guide/windows.md); the backends under [Architecture](../overview/architecture.md#the-backend-spi) |
| `dev.goldberry.widget.*` | [Writing a widget](../guide/writing-a-widget.md) |
| `dev.goldberry.widgets` and `widgets.core` | [The catalogue](../components/index.md), the [Layout](../layout/index.md) chapters |
| `dev.goldberry.widgets.controls.*` | [Buttons, badges and chips](../components/buttons.md), [Choices](../components/choices.md), [Values and progress](../components/values.md) |
| `dev.goldberry.widgets.data.*` | [Charts](../components/charts.md) |
| `dev.goldberry.widgets.form.*` | [Fields and forms](../components/forms.md) |
| `dev.goldberry.widgets.menu`, `widgets.shell.tray` | [Menus and the tray](../components/menus.md) |
| `dev.goldberry.widgets.nav.*` | [Navigation](../components/navigation.md) |
| `dev.goldberry.widgets.overlay.*` | [Overlays](../components/overlays.md) |
| `dev.goldberry.widgets.panel.*` | [Panels](../components/panels.md); `list`, `table` and `tree` under [Collections](../components/collections.md); `masonry` and `split` under Layout |
| `dev.goldberry.widgets.text` | [Text and links](../components/text.md) |
| `dev.goldberry.widgets.core.web`, `widgets.shell.web`, `dev.goldberry.html.*`, `dev.goldberry.content.*`, `dev.goldberry.markdown.*` | [Markdown, HTML and the web](../components/content.md) |
| `dev.goldberry.media.*` | [Audio and video](../components/media.md) |
| `dev.goldberry.gpu.*` | [The GPU canvas](../components/gpu.md) |
| `dev.goldberry.natives.*` | [Architecture](../overview/architecture.md#the-native-boundary), [Native image](../native.md) |
| `dev.goldberry.weaver` | [Model weaving](../weaving.md) |
| `dev.goldberry.log.*` | [Logging and diagnostics](../guide/logging.md) |
| `dev.goldberry.emoji` | [Text, fonts and icons](../guide/text.md#emoji) |
| `dev.goldberry.assets.*` (the build-time preparer) | [Building from source](building.md) |
| `dev.goldberry.example.*` | [Your first Java application](../getting-started/first-java-application.md), [Building an application](../applications.md) |
| `dev.goldberry.build.*` | [Building from source](building.md), [Tests and gates](testing.md), [Releasing](releasing.md) |
| A test, in any module | [Tests and gates](testing.md); the test-scope API under [Testing an application](../guide/testing.md) |

## Checking

```sh
./gradlew :build-logic:test --tests '*SourceDocsTest*'
./gradlew javadoc
```

The first holds the rules above: no record number in a source, every guide
link lands on a page and a heading that exist, every published package links
the guide, and no Java source names a file under `docs/`. The second is
doclint over the published modules, which finds a `[Type]` that does not
resolve. Before either, `python3 tools/book/guide_links.py <paths>` reports the
same for the paths given, plus a section sign that is not a public standard's
and a `///` line the formatter would break; `--anchors <chapter.md>` lists the
headings a link may name.
