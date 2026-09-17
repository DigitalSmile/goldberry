# The small items, and a sweep of the list that holds them

Working notes for the batch that started on 2026-09-17, after
`docs/widgets-finishing.md` closed the last of the widget work. Two halves:

1. **A truth pass over `book/src/TODO.md`.** Every unstruck entry was read
   against the code. Entries the code had already answered are struck and moved
   to *Answered*; entries still open whose *description* had rotted are
   corrected. The list is the only map of what is left, and a map with fixed
   roads on it is worse than no map.
2. **The small items themselves** — the entries that were open, cheap, and had
   no milestone waiting on them. One ADR per decision, in `book/src/adr/`.

Status legend: **done** means code, tests and ADR have landed. **in progress**
means it is being built now. **open** means not started. **answered** means
decided not to build, with the reason in the ADR.

## 1. Layout

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| `flex-basis` | §8's last unimplemented flex property | 0373 | done |
| `align-content` | how wrapped lines share the cross axis | 0374 | done |
| Silent overflow | something that says a box was clipped out of the window | 0375 | done |

## 2. Text

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| Three key maps | one table `Editor`, `TextField` and `TextAreaBox` share | 0376 | done |
| A bidi caret | a caret that walks mixed-direction text | — | open — the largest item left, and the one whose *shaping* is approximate too |

## 3. Widgets and input

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| A `tabs` indicator that travels | §3.1's shared effect, which `segmented` has | 0377 | done |
| The platform primary modifier | §2.3's `Cmd` on macOS, `Ctrl` elsewhere | 0378 | done |
| A disabled container | the widget contract's "disabled propagates down" | 0379 | done |

## 4. Images

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| An animated GIF | the frames after the first, and a clock | 0382 | done |
| `Image.decode` off the UI thread | a seam a painter can use | — | answered: `ImageLoader.shared()` is the seam |
| WebP encoding, and an animated one | libwebp's encoder and `webpdemux` | 0385 | done |
| JPEG encoding | a codec Blend2D does not ship | 0385 | answered: a third library or a written one, and a lossless WebP is a third of a PNG |

## 5. The desktop

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| Reduced motion, detected | §13's switch read from the OS rather than set | 0383 | done |
| The density the user wants | the same question for §1.3 | 0383 | answered: no desktop has one to read |

## 6. Where the documents disagree (`ARCHITECTURE.md` §17.1)

| Item | Decision | ADR | Status |
|------|----------|-----|--------|
| The platform primary modifier | build it, as an explicit modifier | 0378 | done |
| A disabled container | build it | 0379 | done |
| A `tooltip`'s radius and rank | the code follows `design-system.md` | 0380 | done |
| `text style="body"` | the code follows `core-widgets.md` | 0381 | done |
| `goldberry-emoji` | the module is built, and core stops carrying the font | 0384 | done |
| One module or two | settled already; the section is stale | — | done |
| `goldberry-charts` as an artifact | the table is amended | — | done |
| "Zero new natives" | the sentence is amended | — | done |
| Dual y-axes | `charts.md` wins; the table is amended | — | done |

## 7. What the truth pass found

Every unstruck entry in `book/src/TODO.md` was read against the code. What came
of it:

**Eleven entries left the open list.** Nine were closed by this batch (§1–§5
above). Two had been closed months earlier and nobody struck them: `statistic`'s
sparkline was built on 2026-08-23 and the entry still said it was waiting for
`canvas`, and `html-view`'s text selection shipped with ADR-0301 while a second
entry in the same file already said so. Two more were CI facts rather than gaps
— layout verification has passed on all three runners since the first green
snapshot, and AsmJit's W^X path on Apple Silicon has been *reached* rather than
merely *reachable* since the showcase painted frames on `macos-14`.

**Seven entries were still open and described the code wrongly**, which is worse
than being out of date, because each was an argument resting on a fact that had
moved:

| Entry | What had changed |
|---|---|
| `Measured` has a consumer whose reason is a sibling's geometry | It has several — `MasonryCell` and `TableHead` both report for somebody else |
| `Styled.restyle` has one caller | Three, and two of them write a colour rather than a count |
| `tree` moved from deferred to specified | `table` has since followed it and the entry still called it deferred |
| Content modules: "none of them exists" | `:html` does, and the next bullet said so |
| The toggle's compact density | Answered by ADR-0356, and still written as an open question |
| 106 golden images | About 245 |
| `Element.update` invalidates a subtree wholesale | ADR-0315 narrowed it; what is left is the case where the cascade *could* see the change |

**The "seven open disagreements" paragraph was wrong in both directions.** Five
of the seven were taken on 2026-09-17; one of the remaining two had been settled
in `core-widgets.md` itself a month before; and three disagreements §17.1 records
had never been counted in the paragraph at all.

## 8. Where the numbers came from

Each ADR in this batch is one decision. The ones that changed a picture say so:
five tooltip goldens were re-blessed for §3's radius and rank, and nothing else
in the corpus moved — which is the claim that `flex-basis`, `align-content`, the
travelling underline, the shared key map and the propagated `:disabled` are all
behaviour-preserving where they were meant to be.

## 9. The showcase

The gallery gained a thirteenth screen, **Emoji** — the sheet the emoji artifact
exists for, built from the face's own `cmap` and carrying the CC BY-SA credit on
screen ([ADR-0386](../book/src/adr/0386-a-sheet-of-emoji-is-the-fonts-own-contents.md)).
`:example` is the first application here to opt into an artifact with an
obligation attached, which is what ADR-0384 asks an application to do.

Two things came back from building it. `Fonts` used to *throw* when a stylesheet
named a family whose face was not on the path, which a render pass cannot
afford — it falls back with one line in the log now. And the gallery goldens are
taken with a one-font renderer that ignores `font-family`, so the new screen's
golden is the one image in that file rendered through a book.

## 10. What this batch did not do

- **A bidi caret.** The largest item left on the list, and the one whose ground
  is unstable: `Paragraph.isBidiApproximate` says the *shaping* does not promise
  visual order for mixed-direction text, so a correct caret over an approximate
  layout would be a precise answer to the wrong question. It needs the shaping
  first.
- **JPEG encoding**, answered above.
- **The AccessKit bridge**, which is M5's and is the biggest thing the catalog
  is waiting on: every widget already declares its role, its name and its live
  region, and nothing carries them to a screen reader. Several TODO entries —
  a toast that is not announced, `Role` with no link or list, a trail that is not
  a landmark — are one piece of work behind it.
- **The release.** `docs/releasing.md` §Status is unchanged by this batch: the
  publishing chain is built and has still never run, and what it waits on is an
  account rather than code.
