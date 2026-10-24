package dev.goldberry.media.robustness;

import java.util.Arrays;
import java.util.SplittableRandom;

/// The robustness tests' budget and their damage: how many rounds of each kind
/// of mutation to run, from which seed, and the mutations themselves.
///
/// Deterministic. Every round draws from a [SplittableRandom] seeded by the
/// base seed, the fixture and the round, so a failure names a seed that
/// reproduces it, and the same build mutates the same bytes every run. Nothing
/// here reads a clock.
///
/// `-Dgoldberry.media.fuzz.rounds=<n>` raises the rounds, as the sanitizer lane
/// does; `-Dgoldberry.media.fuzz.seed=<n>` starts somewhere else.
public final class Fuzz {

    /// The rounds per fixture and kind of damage.
    public static final String ROUNDS_PROPERTY = "goldberry.media.fuzz.rounds";

    /// The base seed.
    public static final String SEED_PROPERTY = "goldberry.media.fuzz.seed";

    /// Rounds when the property is not set: enough to cross every branch of
    /// every kind of damage on every fixture, few enough for `test`.
    static final int DEFAULT_ROUNDS = 8;

    private static final long DEFAULT_SEED = 0x60_1D_BE_77_1E_5EEDL;

    /// Values a corrupted size or count field likes to hold.
    private static final int[] INTERESTING_INTS = {
        0,
        1,
        2,
        7,
        8,
        15,
        16,
        127,
        128,
        255,
        256,
        0x7FFF,
        0x8000,
        0xFFFF,
        0x10000,
        0x7FFF_FFFF,
        0x8000_0000,
        0xFFFF_FFFF,
        0xFFFF_FFFE,
        0x4000_0000
    };

    private static final byte[] INTERESTING_BYTES = {0, 1, 0x7F, (byte) 0x80, (byte) 0xFE, (byte) 0xFF};

    private Fuzz() {}

    /// The rounds per fixture and kind of damage.
    public static int rounds() {
        return Math.max(1, Integer.getInteger(ROUNDS_PROPERTY, DEFAULT_ROUNDS));
    }

    /// The seed of `round` of `what`: the base seed mixed with both.
    public static long seed(String what, int round) {
        var base = Long.getLong(SEED_PROPERTY, DEFAULT_SEED);
        return base ^ ((long) what.hashCode() << 32) ^ (round * 0x9E37_79B9_7F4A_7C15L);
    }

    /// A generator for `round` of `what`.
    public static SplittableRandom random(String what, int round) {
        return new SplittableRandom(seed(what, round));
    }

    /// `bytes` with `count` single bits flipped, anywhere in `[from, to)`.
    public static byte[] flipBits(byte[] bytes, SplittableRandom random, int count, int from, int to) {
        var copy = bytes.clone();
        if (to <= from) {
            return copy;
        }
        for (var i = 0; i < count; i++) {
            var at = random.nextInt(from, to);
            copy[at] ^= (byte) (1 << random.nextInt(8));
        }
        return copy;
    }

    /// `bytes` with `runs` runs of up to 16 bytes overwritten, each with one
    /// value a parser stumbles on or with noise.
    public static byte[] overwrite(byte[] bytes, SplittableRandom random, int runs) {
        var copy = bytes.clone();
        if (copy.length == 0) {
            return copy;
        }
        for (var i = 0; i < runs; i++) {
            var at = random.nextInt(copy.length);
            var length = Math.min(copy.length - at, 1 + random.nextInt(16));
            if (random.nextBoolean()) {
                Arrays.fill(copy, at, at + length, INTERESTING_BYTES[random.nextInt(INTERESTING_BYTES.length)]);
            } else {
                for (var j = at; j < at + length; j++) {
                    copy[j] = (byte) random.nextInt(256);
                }
            }
        }
        return copy;
    }

    /// `bytes` with a 32-bit value a size or count field would choke on
    /// written at `count` places, big- or little-endian, below `limit`.
    public static byte[] sizeFields(byte[] bytes, SplittableRandom random, int count, int limit) {
        var copy = bytes.clone();
        var end = Math.min(limit, copy.length) - 4;
        if (end <= 0) {
            return copy;
        }
        for (var i = 0; i < count; i++) {
            var at = random.nextInt(end);
            var value = random.nextInt(4) == 0
                    ? copy.length + random.nextInt(-8, 9)
                    : INTERESTING_INTS[random.nextInt(INTERESTING_INTS.length)];
            var bigEndian = random.nextBoolean();
            for (var b = 0; b < 4; b++) {
                var shift = bigEndian ? 24 - 8 * b : 8 * b;
                copy[at + b] = (byte) (value >>> shift);
            }
        }
        return copy;
    }

    /// `bytes` with up to 64 random bytes inserted at one place, and as many
    /// removed at another: every offset after them moves.
    public static byte[] shift(byte[] bytes, SplittableRandom random) {
        if (bytes.length < 2) {
            return bytes.clone();
        }
        var removeAt = random.nextInt(bytes.length);
        var removed = Math.min(bytes.length - removeAt, random.nextInt(65));
        var shorter = new byte[bytes.length - removed];
        System.arraycopy(bytes, 0, shorter, 0, removeAt);
        System.arraycopy(bytes, removeAt + removed, shorter, removeAt, bytes.length - removeAt - removed);
        var insertAt = random.nextInt(shorter.length + 1);
        var inserted = garbage(random, random.nextInt(65));
        var result = new byte[shorter.length + inserted.length];
        System.arraycopy(shorter, 0, result, 0, insertAt);
        System.arraycopy(inserted, 0, result, insertAt, inserted.length);
        System.arraycopy(shorter, insertAt, result, insertAt + inserted.length, shorter.length - insertAt);
        return result;
    }

    /// The first `length` bytes of `bytes`.
    public static byte[] truncate(byte[] bytes, int length) {
        return Arrays.copyOf(bytes, Math.max(0, Math.min(length, bytes.length)));
    }

    /// `length` random bytes.
    public static byte[] garbage(SplittableRandom random, int length) {
        var bytes = new byte[length];
        for (var i = 0; i < length; i++) {
            bytes[i] = (byte) random.nextInt(256);
        }
        return bytes;
    }

    /// One of the ways above, picked by `random`, with a strength fit for a
    /// buffer of `bytes.length`: what a mixed round does to one packet.
    public static byte[] anyDamage(byte[] bytes, SplittableRandom random) {
        return switch (random.nextInt(6)) {
            case 0 -> flipBits(bytes, random, 1 + random.nextInt(8), 0, bytes.length);
            case 1 -> overwrite(bytes, random, 1 + random.nextInt(4));
            case 2 -> sizeFields(bytes, random, 1 + random.nextInt(3), bytes.length);
            case 3 -> shift(bytes, random);
            case 4 -> truncate(bytes, bytes.length == 0 ? 0 : random.nextInt(bytes.length));
            default -> garbage(random, random.nextInt(1 + 2 * bytes.length));
        };
    }
}
