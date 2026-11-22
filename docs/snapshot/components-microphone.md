# Parked for `book/src/components/microphone.md`: a new chapter

Everything below the rule goes into the book at release. `BookTest` reads the
`## `name`` headings and the catalogue rows of this file as if they were in the
book until then.

Where each piece goes:

1. **The chapter**: everything below the rule becomes
   `book/src/components/microphone.md`, listed in `book/src/SUMMARY.md` after
   `components/media.md`. Doc comments in `dev.goldberry.mic` link
   `components/media.html` until it moves; point them at
   `components/microphone.html` then.
2. **In `book/src/components/index.md`**, a card after the media card:

   <a class="gb-card" href="microphone.html"><strong>Microphone</strong><span>level-meter, waveform-view, spectrum-view</span></a>

   and the three catalogue rows, in alphabetical order: `level-meter` before
   `line-chart`, `spectrum-view` between `sparkline` and `spinner`, and
   `waveform-view` before `wizard`:

   | [`level-meter`](microphone.md#level-meter) | Microphone |
   | [`spectrum-view`](microphone.md#spectrum-view) | Microphone |
   | [`waveform-view`](microphone.md#waveform-view) | Microphone |

---

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

### On macOS

macOS records for an application only once the user has allowed it, and asks
the first time a recording opens. The question quotes a sentence from the
application's `Info.plist`:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>Records your voice for voice messages.</string>
```

Without the key the system does not ask: it ends the process the first time
it records. The bundle is the application's own. The toolkit's Gradle plugin
writes none, so the key goes into whatever `Info.plist` the packaging writes;
`jpackage` takes a whole one from its `--resource-dir`. A program started
from a terminal has no bundle of its own, so macOS asks on the terminal's
behalf and remembers the answer for the terminal.

A user who refused allows it again in System Settings, under Privacy &
Security, then Microphone.

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

## The widgets

The three widgets read any `SampleSource` and draw what they hear. Each feeds
a tracker of its own while it is mounted and draws again at most once a frame,
however many blocks arrive; a widget shown again starts from silence. Their
sizes and colours are in `mic.css`, added beside the controls' sheets:

```java
var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
sheets.add(MicStyles.stylesheet());
```

In markup, `source=` names a source the application registered with the
document. A name nothing is bound to draws silence, so a preview of the
document shows the widget at rest.

| Token | Draws |
|---|---|
| `--gb-mic-track` | A level meter's track |
| `--gb-mic-level`, `--gb-mic-hot`, `--gb-mic-clip` | A level bar below −6 dBFS, to −1 dBFS, and past it |
| `--gb-mic-hold` | The held peak's mark, until it clips |
| `--gb-mic-wave`, `--gb-mic-wave-ahead` | A waveform's played part, and a clip's part not played yet |
| `--gb-mic-spectrum` | A spectrum's bars or line |

## `level-meter`

How loud a source is, as a bar.

```kdl
level-meter source="mic"
level-meter source="mic" orientation="vertical"
```

```java
new LevelMeter(recording)
```

The last block's RMS is the solid bar and its peak a fainter one past it. The
loudest peak of the last moment is a mark that holds for 1.5 s and then falls
at 20 dB a second. The scale runs 60 dB, from −60 dBFS to full scale; the bar
turns to the hot colour at −6 dBFS and the clip colour at −1, and a held peak
that reached −1 marks in the clip colour too.

| Attribute | Means |
|---|---|
| `source` | What to measure |
| `orientation` | `horizontal` (the default) fills left to right; `vertical` fills bottom to top, and adds the class `vertical` |

A meter is 8 pixels thick and at least 96 long; a vertical one is 96 tall.

## `waveform-view`

The outline of the last few seconds a source said, scrolling in from the
right.

```kdl
waveform-view source="mic" seconds=5
```

```java
new WaveformView(recording, Duration.ofSeconds(5))
```

| Attribute | Means |
|---|---|
| `source` | What to draw |
| `seconds` | How much of the past the view spans, 3 by default |

The window is cut into 256 buckets, each drawn from its lowest sample to its
highest at full scale. What has not been heard yet is a flat line.

**A recorded clip** is drawn whole by the plot the view itself draws with,
from the clip's own envelope. A voice message, two fifths played, filling the
height with its loudest moment:

```java
var outline = Envelope.of(samples, channels, 48);
new WaveformPlot(outline, 0.4, true, Attributes.NONE)
```

An outline with a third as many buckets as the box is wide, or fewer, is drawn
a bar a bucket with gaps between; a longer one is one continuous shape.

## `spectrum-view`

What frequencies a source is made of, now.

```kdl
spectrum-view source="mic"
spectrum-view source="mic" bands=48 shape="line"
```

```java
new SpectrumView(recording, 48, SpectrumShape.LINE, Attributes.NONE)
```

| Attribute | Means |
|---|---|
| `source` | What to analyse |
| `bands` | How many bands, 32 by default, up to 1024 |
| `shape` | `bars` (the default), or `line`: a line through the bands with the area beneath it shaded |

The spectrum is of the last 40 ms or so, through a Hann window, cut into bands
spaced evenly in log frequency from 50 Hz to 16 kHz (or just under half the
sample rate, where that is lower). Each band is as loud as its loudest bin, and
the view is 90 dB tall: a full-scale sine reaches the top in its band.

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
