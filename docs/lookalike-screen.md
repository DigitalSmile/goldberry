# The Lookalike screen

Working notes for the showcase screen that rebuilds a music streaming app from
the stock widgets and one stylesheet. The decision is ADR-0623, parked in
`docs/snapshot/adr/` until the release.

## Status — 2026-10-11

| Piece | State | Where |
|---|---|---|
| Library: tracks, playlists, albums, the sample | **done** | `ui/lookalike/library/` — `Track`, `Playlist`, `Album`, `Cover`, `Library`, `SampleLibrary`, `Times` |
| Player rules: one `Session`, a sealed set of `Command`s | **done** | `ui/lookalike/player/` — `Session`, `Command`, `Repeat` |
| Widgets: sidebar, playlist page, track rows, now-playing panel, player bar | **done** | `ui/lookalike/view/` — `StreamingApp` holds the session and the timer |
| The screen, registered in the gallery | **done** | `LookalikeChapter`, `Gallery.TABS` between Application and Guide |
| The stylesheet | **done** | `chapter-lookalike.css`, listed in `ShowcaseStyles.CHAPTERS` |
| Unit tests | **done** | `TimesTest`, `LibraryTest`, `SessionTest` (rules), `LookalikeChapterTest` (wiring, on the window's clock) |
| Gallery tests | **done** | `ShowcaseShellTest` pins the order; golden `gallery-lookalike.png`; `GalleryTextScaleTest` and `GalleryDocsTest` pass |
| ADR | **parked** | `docs/snapshot/adr/0623-…`, row in `docs/snapshot/README.md` |

## What the screen does

- **Sidebar.** Home and Search (static), Your Library with filter chips, and a
  row per playlist. A row opens the playlist; the queued one is green and shows
  an equaliser while it plays. Liked Songs counts its hearts.
- **Playlist page.** Tinted from the cover's colour into the window's black.
  The big green button plays the playlist from the top, or pauses and resumes
  it when it is already the queue. Shuffle toggles. A row plays its track; the
  heart beside it likes the track without playing it. An album shelf follows.
- **Now-playing panel.** Opened from the bar: the track large, a card about
  its artist, and what `Next` would play.
- **Player bar.** Track and heart; shuffle, previous (restarts after 3 s),
  play/pause, next, repeat (off → all → one); a seek slider with the clock
  either side; the panel toggle, mute and a volume slider.

## Rules worth knowing

- Repeat-one holds only for a track that **ended**; `Next` always moves on.
- With repeat off, the end of the list stops at the top; with repeat on it goes
  round.
- Unliking a song while Liked Songs is the queue keeps the current track
  current; unliking the current track moves to its neighbour; unliking the last
  one stops.
- The shuffle is seeded by the queue's id.

## Not done, and not planned

- Search, Home, the queue and lyrics buttons are drawn and do nothing.
- No audio: the clock moves, nothing is heard. `:media` could play a file per
  track if that is ever wanted.
- Covers are gradients; there are no bitmaps.
