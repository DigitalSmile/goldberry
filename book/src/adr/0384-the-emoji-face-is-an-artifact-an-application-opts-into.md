# 384. The emoji face is an artifact an application opts into

Date: 2026-09-17

## Status

Accepted. Closes `docs/ARCHITECTURE.md` §17.1's "`goldberry-emoji` is not a
module, and core carries its attribution" — which that section called the only
obligation in the toolkit an application cannot discharge with a file.

## Context

`content-widgets.md`'s table has always quarantined OpenMoji in an optional
artifact, and given the reason: CC BY-SA asks for attribution **where the work
is seen**. An about box, a credits screen, a help page — not a line in a notice
file inside a jar.

`goldberry-core` shipped the font anyway, because the emoji slot is part of
§6.1's font chain and the chain lives in the text stack. So every application
that depended on Goldberry inherited a share-alike attribution obligation
whether or not it ever drew an emoji, and had no way to tell that it had.

§17.1 recorded the two as defensible and incompatible. They are not: the
*chain* is a text-stack decision and the *face* is a packaging one, and nothing
required them to live in the same artifact.

## Decision

**The face ships as `goldberry-emoji`; `:core` keeps the slot and loads the face
through a service.**

- `assets.EmojiFont` is a service interface in `:core`, which `uses` it.
  `goldberry-emoji` `provides` it with `OpenMojiFont`, and declares the provider
  in `META-INF/services` as well, so it is found on the class path and on the
  module path alike.
- A resource, not a service, was the obvious alternative and does not work: a
  resource in a named module is encapsulated, and opening a package to read one
  file is a wider hole than a provider.
- `BundledAssets.font(EMOJI)` with no provider throws `MissingEmojiFontException`,
  whose message names the artifact, the licence and what to do about it.
  `BundledAssets.hasEmojiFont()` is the cheaper question for an application that
  draws emoji when it can.
- `OpenMojiFont.CREDIT` is the sentence to display, as a constant, so an
  application meets the obligation without transcribing it.
- `:assets` prepares a **selection** now — `--only=inter,jetbrains-mono,lucide`
  for `:core`, `--only=openmoji` for `:emoji` — and each asset task empties its
  directory first, because an incremental build otherwise keeps shipping a face
  the module has stopped fetching.
- `PublishedModules` gains one line and the BOM, the umbrella and the POMs
  follow; the artifact is **optional**, like `goldberry-html` and
  `goldberry-gpu`.

## Consequences

- **An application that drew emoji before must now add a dependency.** That is
  the change, and it is the point: the obligation is visible at the moment it is
  taken on. The failure says so in a sentence rather than as a missing resource.
- `:core` is 1.4 MB smaller, which is a side effect and not a reason.
- The shaping test moved with the font: "emoji shape through the emoji face"
  belongs to the artifact that ships the face, and `:core` asserts the absence —
  that `hasEmojiFont()` is false and that the message names the artifact.
- `NOTICE` and `THIRD-PARTY-NOTICES.md` say which jar the font is in, and that an
  application adding it owes the credit. `licenses/openmoji.txt` stays where it
  is: the licence text is still disclosed by the repository whether or not a
  given build ships the font.
- §6.2's re-themed COLRv0 variant is unaffected and still not bundled. If it ever
  is, it is a derivative work and the statement of changes goes in the same two
  files.
