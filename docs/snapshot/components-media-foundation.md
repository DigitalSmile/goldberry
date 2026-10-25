# Guide text held back: Media Foundation on by default

Held back until the release (ADR-0664). Two pieces; each says where it goes.
They say what is, and cite no record.

## `book/src/components/media.md`, the system decoders table

The `mediafoundation-video`, `mediafoundation-audio` row becomes:

```markdown
| `mediafoundation-video`, `mediafoundation-audio` | The same, where Windows has the decoders: HEVC needs the HEVC Video Extensions, and Windows Server has no AC-3 or E-AC-3 | Windows; `-Dgoldberry.media.mediaFoundation=false` turns them off |
```

## `book/src/overview/limitations.md`, the Windows codecs row

The whole row is replaced (this supersedes the sentence
`components-media-engine.md` changes in the same row):

```markdown
| HEVC, AC-3 and E-AC-3 on some Windows | They play through Windows' own decoders, which a machine may not have: HEVC needs the HEVC Video Extensions, Windows Server has no AC-3 or E-AC-3, and the N editions decode none of these until the Media Feature Pack is installed. VP8, VP9, AV1, Opus, Vorbis, FLAC and MP3 play everywhere through the built-in decoders |
```
