# Chapters, and the read-ahead that follows the bitrate: the guide's pieces

Parked until the release, as `docs/snapshot/README.md` says. Seven pieces, each
for one place in the guide; the records are ADR-0655 and ADR-0657. The prose
follows the guide's rule: it says what is, and cites no record.

## `book/src/components/media.md`, `### A player`, after the `MediaProbe` paragraph

> A source's chapters are on its `MediaInfo`: `chapters()` lists them in order,
> each a `Chapter` with its start, its end and its title, and
> `chapterAt(position)` is the one playing. They are read from Matroska's
> chapters, an MP4's chapter track or `chpl` list, an MP3's ID3 `CHAP` frames,
> and the `CHAPTER001=` comments of Ogg and FLAC. `nextChapter()` seeks to the
> next one's start; `previousChapter()` seeks to the start of the one playing
> once it has played three seconds, and to the one before it until then. Both
> return false when there is nowhere to go.

## `book/src/components/media.md`, `### Tracks, subtitles and the network`, the last paragraph

Replace "A URL is read through a cache and buffered to a high-water mark, which
the seek bar shades," with:

> A URL is read through a cache and buffered to a high-water mark, which the
> seek bar shades. Once the source is open, the cache reads half a minute of it
> ahead at its own bitrate, as far as the cache has room,

## `book/src/components/media.md`, `## media-player`, `### Styling`

After `.media-now-playing`, add `.media-chapter`, and after the sentence about
the tokens add:

> A source with chapters notches the seek bar where each starts, and
> `.media-chapter` is the title of the one playing, after the elapsed time.

## `book/src/components/media.md`, `## media-player`, `### Keyboard`

After "`Home` goes to the start," add:

> `Page Up` and `Page Down` go to the chapter before and the next, when the
> source has chapters,

## `book/src/components/media.md`, `## audio-player`, `### Styling`

Add `media-chapter` to the list of pieces, after `media-time`.

## `book/src/components/media.md`, `## media-controls`, `### Styling` and `### Keyboard`

Add `media-chapter` to the list of pieces, after `media-time`. In the keyboard
paragraph, after `Home`, add "`Page Up` and `Page Down`,".

## `book/src/components/values.md`, `## slider`, after the `spans` sentence

> `marks(List<Double>)` notches points of the range across the groove, over the
> fill and under the thumb (`slider-mark`, `--gb-slider-mark-bg`), such as where
> a seek bar's chapters start. A document cannot write them either.
