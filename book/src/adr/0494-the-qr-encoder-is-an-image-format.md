# 494. The QR encoder is an image format

Date: 2026-09-30

## Status

Accepted. Moves the package
[ADR-0391](0391-a-qr-code-is-a-specification-and-a-grid-of-squares.md) created
and changes nothing else it decided.

## Context

ADR-0391 put the QR encoder in `:core` "beside `image.gif` and `image.png` and
for their reason". Each is a specification small enough that owning it costs
less than linking it, with nothing in it that names a widget. The package it
was given was not beside them, though. It was `io.github.digitalsmile.goldberry.qr`,
a top-level package among `css`, `layout`, `paint` and `render`, which are the
toolkit's subsystems. The encoder is not a subsystem. It takes a payload and
returns a `QrMatrix`, the same shape of thing as the GIF decoder and the PNG
encoder.

[ADR-0172](0172-a-package-is-a-role-and-the-module-is-the-fence.md) says a
package is named for the role its contents play. The role is "an image format
the toolkit owns", and that role already has a parent package.

## Decision

**`…goldberry.qr` is `…goldberry.image.qr`.** All eleven types, the five tests
and `libqrencode-vectors.txt` move with it, unchanged. `:core` exports the new
name in place of the old. `:widgets`' `qr-code` and the showcase's Canvas screen
import it from there.

`tools/refactor/move_package.py` made the move. On the way it was found to walk
into `.claude/worktrees/`, which holds agents' whole-repository worktrees, and
would have rewritten another agent's copy. It now skips `.claude`.

## Consequences

- A source-incompatible change for an application that imported
  `io.github.digitalsmile.goldberry.qr`. Nothing has been released, so there is
  no deprecation shim. The snapshot is the only place the old name ever
  shipped.
- `image` now holds every format the toolkit owns: `gif`, `png`, `anim`, `qr`.

## Alternatives considered

- **`image.codec.qr`, with `gif` and `png` moved under `codec` as well.** It
  would be tidier, but it would churn two more exported packages for a word
  that adds nothing. The parent is already `image`.
