# 391. A QR code is a specification and a grid of squares

Date: 2026-09-18

## Status

Accepted, closing `docs/gaps.md` G47.

## Context

Telegram signs a person in by QR code. TDLib hands the client a
`tg://login?token=…` link and renews it about every thirty seconds until a phone
scans one, and the connect dialog has to draw that link and redraw it when the
link changes. The same need arrives from two other directions — a share link
handed to a phone, a device invite — and none of them is about chat.

A QR code is two things that have nothing to do with each other. The first is
ISO/IEC 18004: mode selection, version selection, Reed–Solomon over GF(256),
eight mask patterns scored by a penalty rule, format and version bits. It is a
specification with one right answer, the same kind of thing as the GIF decoder
ADR-0329 wrote rather than linked. The second is a grid of square modules
painted crisply at whatever scale the window is at, which is painting.

An application that writes its own is the second toolkit ADR-0015 is about, and
it would write it badly: every part of the first half is the sort of thing that
produces a code which *looks* right and does not scan.

## Decision

**The encoder is `:core`'s and the widget is `:widgets`', and they share
nothing but a value.**

- `io.github.digitalsmile.goldberry.qr` is the encoder — `QrEncoder.encode`,
  and a `QrMatrix` out the other end. It is in `:core` beside
  `image.gif` and `image.png` and for their reason: a specification small enough
  that owning it costs less than linking it, and nothing in it names a widget.
  An application that wants a code in a PNG rather than on a screen reaches it
  directly and builds no widget tree.
- **No third-party dependency**, which was never in question — this repository
  vendors or writes its codecs — and no native one either. The whole encoder is
  nine files and about six hundred lines.
- **Three modes**, chosen as the narrowest that covers the whole payload:
  numeric, alphanumeric, or the bytes of its UTF-8. Not an optimal multi-segment
  split, which is a shortest-path problem over the payload and buys nothing on
  the payload this exists for — `tg://login?token=…` is lower-case base64 and is
  byte mode from the first character to the last. What the simple rule does buy
  is the case beside it: a numeric pairing code or an upper-case device link
  drops a version or two, which at a fixed pixel size is a visibly coarser and
  more scannable code.
- **Kanji mode is absent**, and so are ECI and structured append. Kanji is
  Shift-JIS, which an API taking a `String` has no way to be handed.
- The **quiet zone is not in the matrix**. §6.3's four light modules are a
  property of where a code is put rather than of the code, and a matrix carrying
  them could not be drawn on a background it already matched.

**`qr-code` is a leaf widget in `widgets.core.qrcode`, beside `image`.**

- `QrCode(payload, level, quietZone, attributes)`, `@Markup("qr-code")`,
  `value=`, `level=`, `quiet-zone=`. A misspelt `level=` falls back to `M`
  rather than throwing, because a document is reloaded on every keystroke while
  it is being written; a payload that no version holds still throws, because
  that is not a typo that fixes itself on the next character.
- **Sized by the stylesheet**, like `image`: a code has a size in *modules* and
  not in pixels, and how big a module should be is a question about the screen.
  `controls.css` answers it once at 160×160 and an application overrides it.
- **Two colour tokens, `--gb-qr-ink` and `--gb-qr-paper`, identical in both
  themes.** Near-black on white, and the dark theme does not get a vote: the
  standard's module convention is dark on light, plenty of phone scanners will
  not read an inverted code, and a sign-in screen that stopped working at night
  would be a theme deciding whether an application functions. The quiet zone is
  painted in the paper colour, because a code drawn straight onto a themed
  surface has no quiet zone wherever that surface is not white.
- **Semantics are an image's** — `Role.FIGURE`, named by the application's
  `name=`. The payload is never part of the accessible name. While it is valid
  it is a credential, and a screen reader announcing a sign-in token aloud is
  the reason.
- **A rebuild does not re-encode.** `QrCache` keeps the last eight codes keyed by
  payload and level and hands the same `QrMatrix` back by identity. The dialog
  this exists for is rebuilt on every keystroke in every field on it; encoding a
  version 5 code sixty times a second for a picture that has not changed is the
  defect, and identity is also how the test states the promise.

**Modules are whole device pixels, and that is arithmetic rather than a wish.**

- A module is quantized **twice, in that order**: the largest whole number of
  *logical* pixels that fits the box, and then that number times the display
  scale, floored. The code is centred in the box with the remainder as margin.
- The order is the part that was got wrong first and is the second half of the
  promise. Quantizing straight into device pixels gives the sharpest possible
  code and a code that **changes size with the scale**: a 108-pixel box holding
  37 modules gets 2 device pixels a module at 100% and 5 at 200%, because 5 is
  not 2 doubled, so the picture grows by a quarter when the window moves to a
  retina display. ADR-0157's scale-invariance check on the gallery golden caught
  it, which is exactly what that check is for. Through the logical number the
  answers are 2 and 4, and the cost is a few per cent of slack at a fractional
  scale — a code a few per cent smaller is a code, and a code with soft edges is
  not.
- It works because layout already rounds: Yoga runs with its point scale factor
  set to the display scale, so a box's corner is on the pixel grid before a
  painter ever sees it.
- **The modules are drawn as a `Path` and not as `fillRect`s**, and this is the
  one surprise in the change. `Frame.fillRect` takes floats, and a float is not
  enough: a module boundary at device pixel 152 is logical 101.333… at 150%, and
  the nearest `float` to that multiplied back by 1.5 is 152.000004 — which
  Blend2D dutifully draws as one pixel of `#1b1b1b` beside a run of `#1a1a1a`.
  `Path` carries doubles, whose round trip is off by a part in 10¹⁴ and lands on
  the pixel Blend2D would have chosen anyway. Setting the frame's transform to
  `1 / factor` and working in whole device pixels was the first attempt and is
  not available: `Frame.transform` *replaces* the transform rather than composing
  with it (ADR-0068), and what it would replace is the translation to the box's
  own corner.
- A box with no room for one **logical** pixel per module draws **nothing**. A
  21-module code in sixteen pixels is a grey square, and drawing it would be
  claiming a scanner could read it. Logical rather than device for the reason
  above: a widget that appeared when the window moved to another display would
  be worse than one that is absent on both.

**The encoder is verified against the specification and against other people's
software, never against itself.**

Four kinds of evidence, and the first three are in the repository:

1. **Worked examples, at the codeword level.** `01234567` at version 1 level M —
   the standard's own — and `HELLO WORLD` at version 1 level Q. Data and parity
   are asserted byte for byte, which is where a wrong table shows itself; a
   matrix comparison would only say that something somewhere differed.
2. **The tables the standard prints rather than derives.** All thirty-two
   format-information strings, all thirty-four version patterns, and §7.3.5's
   alignment-centre table for versions 2 to 40. The first two are read back out
   of a built grid rather than compared against the arithmetic that produced
   them, so they also pin *where* the bits go — a BCH computation that was right
   and written into the wrong modules would pass an arithmetic test and fail
   every scanner.
3. **Twenty-six whole matrices from libqrencode 4.1.1**, in
   `core/src/test/resources/…/qr/libqrencode-vectors.txt`. They span every level,
   versions 1 to 40, both block structures, the versions that carry version
   information, version 32's alignment exception and all three modes.
4. **A cross-check run during development, against both libraries that were
   already on the machine** — no third-party code was installed, and none is on
   the build path. `libqrencode.so.4` was called through its own ABI for 2117
   payloads across the four levels and all three modes; 1556 of them came out
   **identical module for module**, and all 2117 were rendered and decoded by
   `libzbar.so.0`, each reading back as exactly its own payload. The three QR
   codes in the showcase screenshot decode out of the PNG the same way.

The 561 that differed differ **only in the mask**, and in the same direction
every time: on a sample of 120, this encoder's chosen mask scored strictly lower
under §7.8.3's four penalty rules than libqrencode's in 119 cases and equal in
the other. libqrencode's mask evaluation is its own; the rules here are the
standard's, and each of the four is tested on a grid built to trip exactly it.

Two numbers that fell out of this deserve writing down, because both were real
bugs found by the cross-check and neither would have been found by a test
written against this code:

- The data placement **steps over** the vertical timing line rather than renaming
  the column pair around it, so the walk continues at 5, 3, 1 and not 4, 2, 0. A
  version that only renamed visited column 4 twice and column 0 never.
- The level H block counts from version 32 up were off by one row — the table
  that no formula produces, and the one place in the whole specification where
  three hundred and twenty numbers have to be transcribed correctly.

## Consequences

- `:core` exports one more package and gains no dependency. The encoder has no
  decoder: nothing in this toolkit reads a QR code, and the interesting half of
  Reed–Solomon is the half that corrects errors rather than the half that
  produces them.
- The catalogue is 72 widgets. `qr-code` is documented in `core-widgets.md` §1
  rather than in `content-widgets.md`, and not because it was convenient: §1 is
  where `image` and `canvas` are, `content-widgets.md` is a list of **modules**
  that wrap native engines, and a QR code needs neither a module nor an engine.
- The showcase's Canvas screen gains a card beside its `image` one, with the
  same link at levels L, M and H — so the price of error correction is a picture
  rather than a sentence. One gallery golden is re-blessed and it is the only
  one that moves: no screen was added, so no digit and no tab moved either. The
  three codes in that golden were lifted back out of the PNG and decoded with
  libzbar, which is the most direct statement of the whole change that exists.
- The two colour tokens are the first pair in the sheet that is **deliberately
  identical in both themes**. That is a precedent worth being careful with, and
  the rule it sets is narrow: a token may ignore the theme when the thing it
  colours is read by a machine rather than by a person.
- An application can now put a code anywhere a picture goes, including into a
  PNG through `Offscreen`, without the widget catalogue being involved at all.
