package io.github.digitalsmile.goldberry.media.subtitle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import io.github.digitalsmile.goldberry.media.codec.CodecId;
import io.github.digitalsmile.goldberry.media.io.MediaIOProvider;
import io.github.digitalsmile.goldberry.media.io.MediaIOs;
import io.github.digitalsmile.goldberry.media.io.Source;

/// Text subtitles, read in Java (`docs/goldberry-media.md` §6): an external
/// `.srt` or `.vtt` file, and the packets of a subtitle track in a container.
///
/// No FFmpeg is involved. The text formats are text, a container's subtitle
/// packet is one cue's worth of it with the times in the packet, and the result
/// is plain lines ([Cue]) that Goldberry draws itself. Bitmap subtitles (PGS,
/// DVB) are not text and are not read.
public final class Subtitles {

    /// A subtitle file's format.
    public enum Format {
        /// SubRip, `.srt`: numbered cues, `00:00:01,500 --> 00:00:03,000`.
        SUBRIP,
        /// WebVTT, `.vtt`: a `WEBVTT` header, `00:01.500 --> 00:03.000`.
        WEBVTT
    }

    /// `[hh:]mm:ss[,.]mmm`, hours of any length, and SubRip's comma or WebVTT's
    /// point before the milliseconds.
    private static final Pattern TIME = Pattern.compile("(?:(\\d+):)?(\\d{1,2}):(\\d{1,2})[,.](\\d{1,3})");

    private static final Pattern TIMING =
            Pattern.compile("^\\s*(" + TIME.pattern() + ")\\s*-->\\s*(" + TIME.pattern() + ")");

    /// What separates two cues: a line with nothing on it but spaces.
    private static final Pattern BLANK_LINE = Pattern.compile("\n\\s*\n");

    private Subtitles() {}

    /// Reads a subtitle file through the protocol that opens `source`, whatever its
    /// scheme, and parses it by its name or, failing that, its first line.
    ///
    /// Reads the whole file on the calling thread, so a network source is read
    /// off the UI thread.
    ///
    /// @throws IOException when the file cannot be read, or is neither SubRip nor
    ///                     WebVTT
    public static List<Cue> read(Source source, List<? extends MediaIOProvider> providers) throws IOException {
        byte[] bytes;
        try (var io = MediaIOs.open(source, providers)) {
            var out = new ByteArrayOutputStream();
            var buffer = ByteBuffer.allocate(16 * 1024);
            while (io.read(buffer.clear()) >= 0) {
                out.write(buffer.array(), 0, buffer.position());
            }
            bytes = out.toByteArray();
        }
        var text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        var format = detect(source.fileName(), text)
                .orElseThrow(() -> new IOException(source.uri() + " is neither SubRip nor WebVTT"));
        return parse(text, format);
    }

    /// The format a file is in: WebVTT when it says so on its first line, else by
    /// its extension, else SubRip when a cue's timing line is found in it.
    public static Optional<Format> detect(Optional<String> fileName, String text) {
        if (text.stripLeading().startsWith("WEBVTT")) {
            return Optional.of(Format.WEBVTT);
        }
        var name = fileName.map(value -> value.toLowerCase(Locale.ROOT)).orElse("");
        if (name.endsWith(".vtt")) {
            return Optional.of(Format.WEBVTT);
        }
        if (name.endsWith(".srt")) {
            return Optional.of(Format.SUBRIP);
        }
        return text.lines().anyMatch(line -> TIMING.matcher(line).find())
                ? Optional.of(Format.SUBRIP)
                : Optional.empty();
    }

    /// Every cue of `text`, in the order they start. A block that is not a cue
    /// (WebVTT's header, `NOTE`, `STYLE` and `REGION` blocks, a SubRip block with
    /// a broken timing line) is passed over, as players do.
    public static List<Cue> parse(String text, Format format) {
        var cues = new ArrayList<Cue>();
        var normal = text.replace("\r\n", "\n").replace('\r', '\n');
        for (var block : BLANK_LINE.splitAsStream(normal).toList()) {
            var lines = block.strip().lines().toList();
            for (var i = 0; i < lines.size(); i++) {
                var timing = TIMING.matcher(lines.get(i));
                if (!timing.find()) {
                    continue;
                }
                var start = time(timing.group(1));
                var end = time(timing.group(6));
                var body = String.join("\n", lines.subList(i + 1, lines.size()));
                var plain = CueText.fromMarkup(body);
                if (!plain.isEmpty() && end.compareTo(start) > 0) {
                    cues.add(new Cue(start, end, plain));
                }
                break;
            }
        }
        cues.sort(Comparator.comparing(Cue::start));
        return List.copyOf(cues);
    }

    /// The cue one packet of a container's subtitle track carries, shown from
    /// `start` to `end`: the packet's own times.
    ///
    /// - SubRip and WebVTT: the payload is the cue's text.
    /// - ASS, as FFmpeg's demuxers hand it over: `ReadOrder,Layer,Style,Name,
    ///   MarginL,MarginR,MarginV,Effect,Text`, and the text is the ninth field on.
    /// - MP4's `mov_text`: a big-endian 16-bit length, then that much UTF-8, then
    ///   style boxes, which are passed over.
    ///
    /// @return the cue, or empty for a codec that is not text, a packet with no
    ///         text, or one with no length of time
    public static Optional<Cue> fromPacket(CodecId codec, byte[] payload, Duration start, Duration end) {
        if (end.compareTo(start) <= 0) {
            return Optional.empty();
        }
        var text =
                switch (codec) {
                    case SUBRIP, WEBVTT -> CueText.fromMarkup(utf8(payload, 0, payload.length));
                    case ASS -> CueText.fromAss(assText(utf8(payload, 0, payload.length)));
                    case MOV_TEXT -> {
                        if (payload.length < 2) {
                            yield "";
                        }
                        var length = Math.min(((payload[0] & 0xff) << 8) | (payload[1] & 0xff), payload.length - 2);
                        yield CueText.lines(utf8(payload, 2, length));
                    }
                    default -> "";
                };
        return text.isEmpty() ? Optional.empty() : Optional.of(new Cue(start, end, text));
    }

    /// The text field of an ASS event: everything after the eighth comma.
    private static String assText(String event) {
        var at = 0;
        for (var commas = 0; commas < 8; commas++) {
            at = event.indexOf(',', at);
            if (at < 0) {
                // Not the packet shape FFmpeg promises: take it all as text.
                return event;
            }
            at++;
        }
        return event.substring(at);
    }

    private static String utf8(byte[] bytes, int offset, int length) {
        var text = new String(bytes, offset, length, StandardCharsets.UTF_8);
        // A C string's terminator, when a muxer kept it.
        var nul = text.indexOf('\0');
        return nul >= 0 ? text.substring(0, nul) : text;
    }

    private static Duration time(String value) {
        var matcher = TIME.matcher(value.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("not a time: " + value);
        }
        var hours = matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1));
        var minutes = Long.parseLong(matcher.group(2));
        var seconds = Long.parseLong(matcher.group(3));
        var fraction = matcher.group(4);
        // "5" after the comma is 500 ms, as in "00:00:01,5": pad to three digits.
        var millis = Long.parseLong((fraction + "00").substring(0, 3));
        return Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds).plusMillis(millis);
    }
}
