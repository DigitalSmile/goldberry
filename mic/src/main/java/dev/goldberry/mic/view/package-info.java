/// The microphone's widgets: `level-meter`, `waveform-view` and
/// `spectrum-view`, each following a [dev.goldberry.mic.source.SampleSource]
/// (a recording, or a synthetic source) and drawing what it hears, and
/// [MicStyles], the stylesheet that sizes and colours them.
///
/// Each widget feeds its own tracker from the source's thread and draws again
/// on the UI thread at most once a frame, however many blocks arrive.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
@NullMarked
package dev.goldberry.mic.view;

import org.jspecify.annotations.NullMarked;
