# 453. A face that moved module takes its declaration with it

Date: 2026-09-21

## Status

Accepted.

## Context

The Linux showcase's native image started, opened its window, painted, and
died on the first paragraph with an emoji in it:

```
java.io.IOException: /io/github/digitalsmile/goldberry/emoji/fonts/OpenMoji-color.ttf
is missing from goldberry-emoji, which means this jar was assembled without the asset step
  at ...emoji.OpenMojiFont.read(OpenMojiFont.java:62)
  at ...text.font.Fonts.emojiAt(Fonts.java:230)
  at ...widget.WidgetRenderer$1.paragraph(WidgetRenderer.java:141)
```

The message is `OpenMojiFont`'s own, and in this case it is wrong. The jar was
assembled with the asset step; the font is in it. What was missing is the
*declaration* that puts a resource inside a native image, so
`getResourceAsStream` answered null and the only diagnostic the class has for
null is the one about the asset step.

[ADR-0160] settled how resources reach an image: declared by glob in a
`META-INF/native-image/**/reachability-metadata.json` that travels in the jar,
not traced from a run. `:core`, `:widgets` and `:example` each ship one.
`:emoji` shipped none.

The best evidence that this was foreseen is in `:core`'s own metadata, in the
comment explaining why globs beat traces:

> The first image built here recorded nord-dark.css and not nord-light.css, and
> Inter but neither JetBrains Mono nor **OpenMoji** — because the run never
> switched theme and never drew mono text or an emoji. Each of those is a
> control the user can reach and an image that dies when they do.

At the time that was true and OpenMoji was covered, because it lived in `:core`
under `io/github/digitalsmile/goldberry/assets/fonts/` and the glob
`assets/fonts/*.ttf` over module `io.…goldberry.core` caught it. [ADR-0384]
then moved the face into `:emoji` — an artifact an application opts into,
because CC BY-SA wants credit where the work is seen — and [ADR-0387] moved the
resource under this module's own package, because a resource directory is a
package and the same one in two modules stops the application starting. Both
moves were right. Neither took the declaration along, and there was nothing to
notice: the glob still matched a directory, just not one that exists any more,
over a module that no longer holds the font.

It then hid for the same reason the other two failures of this batch hid. The
showcase's image ran three frames, which is the first screen; nothing drew an
emoji. The 300-frame walk of [ADR-0342] reaches a screen that does.

## Decision

**`:emoji` ships its own `reachability-metadata.json`**, declaring the one
resource it has, over its own module, under the path the asset step actually
writes to:

```json
{ "module": "io.github.digitalsmile.goldberry.emoji",
  "glob": "io/github/digitalsmile/goldberry/emoji/fonts/*.ttf" }
```

A glob rather than the one file name, to match what `:core` and `:widgets`
already spell and because the asset step's output is the authority on what it
wrote.

### Three files have to agree, so a test says so

The path appears in three places that nothing connected: the `--root` the asset
step is given in `emoji/build.gradle`, the glob in the metadata, and the
`RESOURCE` constant `OpenMojiFont` reads. Any two can be changed without a
build failing, and the symptom is an image that works until something draws an
emoji — which, as this record shows, can be a long time.

`DeclaredFontResourceTest` holds all three together, and its last assertion is
the one a text comparison cannot make: the font is really on the classpath
under the name that was declared, so a declaration naming a file the asset step
does not produce fails too.

## Consequences

**An emoji can be drawn in a native image.** Not only the showcase's: the
metadata travels in the `goldberry-emoji` jar, so any application that takes
the dependency and builds an image gets it without knowing it needed it, which
is [ADR-0160]'s whole point.

**`OpenMojiFont`'s diagnostic is still misleading in this case** and is left
alone. It names the likeliest cause of a null stream for a developer running on
a JVM, which is the common case; teaching it to distinguish "not in the jar"
from "not in the image" would mean asking whether it is in an image, and the
answer would be wrong on the day the check is needed. The test above is the
better guard: it makes the case impossible rather than better-reported.

**The other modules were checked, and `:html` had the same hole.** It ships
`markdown/view/markdown.css` and `html/view/html.css`, reads both by name, and
declared neither. It works today for a reason worth writing down: the
showcase *traces* a run, that run opens the Markdown and HTML screens, and the
traced metadata `:example` ships lists both files literally. So `:html`'s
stylesheets reach the showcase's image by luck and would not reach the image
of anyone else who took the module and traced a run that did not open a
document. That is exactly the failure [ADR-0160] was written about, surviving
inside the mechanism meant to prevent it. `:html` now declares its own.

**`:core`, `:widgets` and `:example` were already right.** `:natives` ships a
metadata file for foreign calls ([ADR-0339], [ADR-0451]) and no resource glob;
its library is linked into the image rather than read out of a jar, so there
is nothing there to declare.

**A repository-wide guard, because per-module was what got forgotten twice.**
`DeclaredResourcesTest` in `build-logic` holds every module's shipped
resources to that module's **own** globs. The "own" is the whole point and was
learned the hard way: the first version of the test pooled every module's
declarations, saw `:example`'s traced list covering `:html`, and passed. A
declaration that does not travel with the module it describes is not a
declaration.

[ADR-0160]: 0160-a-modules-own-resources-are-declared-not-traced.md
[ADR-0339]: 0339-a-foreign-call-is-registered-because-it-exists-not-because-a-run-reached-it.md
[ADR-0342]: 0342-a-window-is-resized-from-outside-and-the-run-says-what-it-cost.md
[ADR-0384]: 0384-the-emoji-face-is-an-artifact-an-application-opts-into.md
[ADR-0387]: 0387-a-resource-directory-is-a-package.md
[ADR-0451]: 0451-a-shape-is-declared-where-it-can-be-reached-not-where-it-is-linked.md
