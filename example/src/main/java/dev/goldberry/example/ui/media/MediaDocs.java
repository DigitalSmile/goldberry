package dev.goldberry.example.ui.media;

import dev.goldberry.example.docs.DocLink;

/// The sections of the media chapter the Audio and Video screens' cards open.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
final class MediaDocs {

    private static final String CHAPTER = "components/media";

    /// The module, its natives and what a build decodes.
    static final DocLink MODULE = DocLink.to(CHAPTER, "the-module");

    /// A `MediaPlayer`: opening a source, the transport and the status.
    static final DocLink PLAYER = DocLink.to(CHAPTER, "a-player");

    /// Track menus, subtitles, and sources read over the network.
    static final DocLink TRACKS = DocLink.to(CHAPTER, "tracks-subtitles-and-the-network");

    /// Decoder providers, the system's decoders, and hardware decoding.
    static final DocLink CODEC = DocLink.to(CHAPTER, "bringing-a-codec");

    /// `media-player`: the picture with its controls over it.
    static final DocLink MEDIA_PLAYER = DocLink.to(CHAPTER, "media-player");

    /// `video-view`: the pictures and nothing else.
    static final DocLink VIDEO_VIEW = DocLink.to(CHAPTER, "video-view");

    /// `audio-player`: compact controls.
    static final DocLink AUDIO_PLAYER = DocLink.to(CHAPTER, "audio-player");

    /// `media-controls`: the transport bar on its own.
    static final DocLink MEDIA_CONTROLS = DocLink.to(CHAPTER, "media-controls");

    private MediaDocs() {}
}
