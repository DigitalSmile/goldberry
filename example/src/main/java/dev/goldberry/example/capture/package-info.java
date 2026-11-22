/// What the Capture screen keeps besides its widgets: where it gets the
/// system's microphones and cameras from, and the throwaway clips it records
/// and plays back.
///
/// The clips are held in memory, and a sound clip is written to a temporary
/// WAV file only so a `MediaPlayer` can play it. Nothing is encoded: there is
/// no encoder in the toolkit, so a picture clip is the pictures themselves,
/// copied as they arrive.
///
/// Marked for NullAway, as every package in the repository is.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
@NullMarked
package dev.goldberry.example.capture;

import org.jspecify.annotations.NullMarked;
