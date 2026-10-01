/*
 * News. Add an entry at the top; the newest one also becomes the pill above
 * the headline. Fields:
 *   date   YYYY-MM-DD
 *   title  one line
 *   text   a short paragraph (optional)
 *   href   a link, into the book or elsewhere (optional)
 *   pill   short text for the pill above the headline (optional; title is used if absent)
 * `limit` is how many entries the page shows.
 */
window.NEWS = {
  limit: 4,
  items: [
    {
      date: "2026-10-01",
      title: "The documentation: a guide in six parts",
      pill: "The documentation is a guide",
      text: "goldberry.dev/docs/ is a guide now: an overview, getting started on the JVM and as a native binary, every layout and every widget with an example, performance, and a developer guide. The decision log is its last part, and every markup sample in it is a test.",
      href: "docs/"
    },
    {
      date: "2026-09-27",
      title: "The GPU lane: composition, canvas3d and 4K60 video",
      pill: "The GPU lane, canvas3d and 4K60 video",
      text: "goldberry-gpu binds SDL_GPU. Windows composite through the GPU by default and on the CPU where they cannot, canvas3d is a layer an application renders into, and video-view shows its pictures through a GPU layer: 4K60 with every picture shown.",
      href: "https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0485-the-audio-clock-never-jumps-and-4k60-plays-every-picture.md"
    },
    {
      date: "2026-09-24",
      title: "goldberry-media: FFmpeg driven from Java",
      text: "Audio and video playback with hardware decode, track switching, text subtitles, network streams read through a cache, and the operating system's own decoders behind the Decoder SPI.",
      href: "https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0460-media-is-ffmpeg-driven-from-java-not-libvlc.md"
    },
    {
      date: "2026-09-23",
      title: "Upstreams moved",
      text: "Blend2D 0.21.3, HarfBuzz 14.5.0, SDL 3.4.16, md4c 0.6.0 and Gradle 9.7.1. The emoji face is now Noto Color Emoji, shipped as goldberry-emoji and drawn from its paint graphs.",
      href: "https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0456-the-emoji-face-is-noto-drawn-from-its-paint-graphs.md"
    },
    {
      date: "2026-09-19",
      title: "Snapshots on every push",
      text: "Every push to master publishes the next line's -SNAPSHOT of the toolkit, the BOM and the optional modules to the Central Portal snapshot repository.",
      href: "docs/"
    }
  ]
};
