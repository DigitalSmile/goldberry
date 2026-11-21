<!-- Destination: a new chapter, book/src/components/microphone.md, listed in
     book/src/SUMMARY.md after components/media.md, with a card in
     components/index.md. Phase 3 of docs/camera-and-mic.md adds a
     `## \`level-meter\``, `## \`waveform-view\`` and `## \`spectrum-view\``
     section each to this chapter, which BookTest then wants. Doc comments in
     dev.goldberry.mic link components/media.html until this moves; point them
     at components/microphone.html then. -->

# Microphone

`goldberry-mic` records from the microphones there are, and works out what the
sound says: how loud it is, what its spectrum is, what its outline looks like.
It is an optional module with no native library of its own:

```kotlin
dependencies {
    implementation("dev.goldberry:goldberry-mic")
}
```

## Recording

```java
try (var microphones = Microphones.open();
        var recording = microphones.record(RecordingSpec.voice())) {
    var meter = new LevelTracker();
    recording.onSamples(meter);
    ...
}
```

`Microphones.list()` asks the system for the recording devices plugged in now,
and `systemDefault()` is the system's choice: a recording from it follows the
user's setting when it changes. A recording from one device of the list stays
on that device, and is `Lost` when it is unplugged.

`RecordingSpec.voice()` asks for 48 kHz mono in 10 ms blocks. The samples are
32-bit float, interleaved, whatever the device runs at; the conversion is done
before they arrive.

A recording hands each block to its `onSamples` listeners on a thread of its
own, in order. The block is borrowed: its samples are valid during the call,
and a listener that keeps them copies them. The same frames go into a buffer
`read(FloatBuffer)` takes from, for code with a thread of its own; what it does
not take within `RecordingSpec.buffer()` (two seconds by default) is dropped,
and `droppedFrames()` counts it.

| Status | Means |
|---|---|
| `Active` | Recording |
| `Paused` | Stopped by `pause()`; `resume()` starts it again |
| `Lost` | The device went away or the system took it; final |
| `Closed` | Closed; final |

`onStatus` tells a listener each change. `onChange` on `Microphones` tells one
about devices plugged in and taken away; both are heard while the
application's window loop runs, on its thread. A program with no window lists
the devices again instead.

On macOS the application's bundle needs an `NSMicrophoneUsageDescription`
string, or the system ends the process the first time it records.

## Levels, spectra and outlines

The analysis reads any `SampleSource`, a recording or a `SyntheticSource`, and
each tracker is itself a listener, fed on the source's thread and read from
any other:

| Tracker | Reports |
|---|---|
| `LevelTracker` | The last block's `Levels` (peak and RMS) and a peak that holds for 1.5 s and then falls at 20 dB a second |
| `SpectrumAnalyzer` | The `Spectrum` of the last `size` frames through a Hann window, in dBFS per bin, and `bands(count, low, high)` spaced in log frequency |
| `EnvelopeTracker` | The `Envelope` of the last stretch heard: each bucket's lowest and highest sample, oldest first |

A full-scale sine reads 0 dBFS at its peak, and its bin of the spectrum reads
0 dB too. Silence reads −120 dB rather than minus infinity.

## Testing without a microphone

A `SyntheticSource` makes its samples from a `Signal` (silence, a tone, noise,
a clip, or a mix of them) and delivers them only when it is advanced, on the
thread that advances it:

```java
var source = SyntheticSource.of(Signal.tone(440, 0.5));
var meter = new LevelTracker();
source.onSamples(meter);
source.advance(Duration.ofMillis(500));
assertEquals(-6.0, meter.reading().levels().peakDecibels(), 0.1);
```

Every signal is a function of the frame number, so the same frames come out
however the time is cut up, and a picture of a meter is the same every run.
