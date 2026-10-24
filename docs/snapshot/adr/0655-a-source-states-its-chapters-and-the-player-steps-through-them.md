# ADR-0655: A source states its chapters, and the player steps through them

- **Status:** Accepted
- **Date:** 2026-10-24
- **Relates to:** ADR-0651 (the Java demuxers), ADR-0466 (a slider marks spans of its range), ADR-0464 (a slider says when a gesture ends)

## Context

`ffmpeg-out.md` phase 3 asks for "timeline with chapters from Matroska/MP4
metadata". Every other item of that phase was built before the plan: the
four widgets, subtitles, track menus, the network cache. Chapters were not.
The Matroska demuxer named the `Chapters` element only so that it could tell
where a Cluster of unknown size ended, and skipped it. MP4 skipped `udta/chpl`
and listed a QuickTime chapter track as a subtitle track that had no cues
worth showing. MP3 skipped `CHAP` frames, which is where every podcast keeps
its chapters, and the Ogg and FLAC readers skipped the comment headers that
carry the `CHAPTERnnn` tags.

Audiobooks and podcasts are what an `audio-player` is most often pointed at,
and a film's scenes are what a seek bar is most often aimed at. Without
chapters, neither can be found except by scrubbing.

## Decision

**Every container that can state chapters reports them, `MediaInfo` carries
them, and the transport shows and steps through them.**

- **One record, `Chapter(start, end, title)`**, in `dev.goldberry.media`. The
  end is optional because only the last chapter of a source of unknown length
  has none; the title is optional because Matroska and MP4 allow chapters
  without one.
- **`MediaInfo.chapters()`**, a sixth component. Its compact constructor
  settles what a container stated (`chapter.ChapterList.settle`): sorted by
  start, the second of two at one start dropped, those past the end dropped,
  and every end not stated filled in from the next start or the duration. A
  container states what it read and nothing more. `chapterAt(position)` is
  the one playing. The five-argument constructor stays, with none.
- **What each container reads:**
  - Matroska: the default edition's top-level `ChapterAtom`s, else the first
    edition not hidden; atoms hidden or disabled are left out, nested atoms
    are sub-chapters and are not listed, the title is the first
    `ChapterDisplay`. Read before the clusters, or through the SeekHead after
    them on a source that seeks.
  - MP4: a QuickTime chapter track (named by `tref/chap`, one text sample per
    chapter, timed by its table), else Nero's `udta/chpl`. A chapter track is
    not reported as a track.
  - MP3: ID3v2 `CHAP` frames titled by their `TIT2`, through the same frame
    walk as `APIC`, with 2.4's per-frame unsynchronisation.
  - Ogg (Opus, Vorbis, FLAC) and bare FLAC: the Xiph `CHAPTERnnn` and
    `CHAPTERnnnNAME` comments of the comment header
    (`chapter.VorbisComments`).
- **`MediaPlayer.nextChapter()` and `previousChapter()`**. Previous goes to the
  start of the chapter playing once it has played three seconds, else to the
  one before: a CD player's rule. Both answer false when there is nowhere to
  go, or the source cannot seek.
- **The transport** notches the seek bar where each chapter but the first
  starts, shows the title of the one playing after the elapsed time
  (`.media-chapter`), and answers `Page Up` and `Page Down`, consumed only
  when the source has chapters, so a page key around a player without them
  still scrolls.
- **`Slider.marks(List<Double>)`**, a fourteenth component, for the notches:
  points of the range drawn across the groove over the fill and under the
  thumb (`slider-mark`), placed as a span is, by percentage insets, so nothing
  in Java learns the groove's width. Both insets meet at the point and the
  theme's negative margins widen the box, so it stays centred whatever width
  a theme gives it. `--gb-slider-mark-bg` is the surface on nord-dark (a gap)
  and nord3 on nord-light, where a gap of nord5 in a nord4 groove is under
  1.2:1. A document cannot write marks, as it cannot write spans.

## Alternatives

- **Chapters as a track** (`MediaType.DATA`, as FFmpeg's demuxers expose MP4's
  chapter track). A chapter has no packets to decode and nothing to select.
  Every consumer would have had to find the track, read its packets and time
  them; a list on `MediaInfo` is what all of them want.
- **Each container settles its own list.** Five copies of the same three rules,
  and they had already drifted in the first draft: MP4's chapter track
  states durations, `chpl` and the Xiph comments state none, ID3 states either.
- **Marks as zero-width spans.** A span is drawn under the fill, so a chapter
  boundary in the played part would vanish, which is where a listener looks
  for "the chapter I am in".
- **`Shift`+`←`/`→` for chapters.** `Page Up` and `Page Down` are what mpv,
  VLC's hotkeys and most audiobook apps use; `Shift` with the arrows is
  unclaimed and kept for finer seeking later.

## Consequences

- A record pattern over `MediaInfo` names six components and one over
  `Slider` fourteen; the constructors callers use are unchanged.
- The ID3 reader's frame walk is shared by `APIC` and `CHAP`, and 2.4's
  per-frame flags are undone once for both.
- FFmpeg's Vorbis comment writer puts a chapter in the first second a second
  late (`00:00:01.700` for 0.7 s), and its FLAC muxer writes no chapters; the
  Ogg and FLAC fixtures are put right by two scripts beside
  `make-fixtures.sh`.
- Ordered Matroska editions are listed by time; a player plays the file in
  its own order regardless. Sub-chapters and MP4's `CTOC`-style tables of
  contents are not read.
