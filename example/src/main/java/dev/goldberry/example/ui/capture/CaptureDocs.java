package dev.goldberry.example.ui.capture;

import dev.goldberry.example.docs.DocLink;

/// Where the Capture screen's header and cards point in the guide.
///
/// The microphone and the camera have chapters of their own in the next
/// release of the guide; until then all three point at the media chapter,
/// whose modules they build on.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
final class CaptureDocs {

    private static final String MEDIA = "components/media";

    /// The screen's header.
    static final DocLink SCREEN = DocLink.page(MEDIA);

    /// The Microphone card.
    static final DocLink MICROPHONE = DocLink.page(MEDIA);

    /// The Camera card.
    static final DocLink CAMERA = DocLink.page(MEDIA);

    private CaptureDocs() {}
}
