package io.github.digitalsmile.goldberry.widgets.core.qrcode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.digitalsmile.goldberry.qr.Level;
import io.github.digitalsmile.goldberry.qr.QrEncoder;
import io.github.digitalsmile.goldberry.qr.QrMatrix;

/// The last few codes encoded, so that a rebuild is not a re-encode.
///
/// A widget is a value that is described again on every frame, and the dialog
/// this exists for is rebuilt on every keystroke in every field on it. Encoding
/// a version 5 code is Reed–Solomon over a hundred codewords and eight masks
/// scored in full — a fraction of a millisecond, and a fraction of a millisecond
/// sixty times a second for a picture that has not changed.
///
/// So the matrix for a payload is kept and handed back **by identity**. That is
/// also how the promise is tested: same payload, same instance, and a counter
/// that says the encoder ran once.
///
/// ## Why a few and not one
///
/// A single slot would thrash the moment two codes were on screen at once — a
/// share sheet showing a link beside a device code is not exotic — and each
/// would evict the other every frame, which is worse than no cache at all. Eight
/// is enough for any screen anybody would build and small enough that the
/// largest thing it can hold is a few hundred kilobytes of booleans.
final class QrCache {

    /// How many codes are kept.
    private static final int CAPACITY = 8;

    /// What a code is looked up by. The level is part of it because the same
    /// link at two levels is two different pictures.
    private record Key(String payload, Level level) {}

    /// Access-ordered, so the entry the iterator reaches first is the one
    /// nobody has asked for in the longest time.
    private static final Map<Key, QrMatrix> CODES = new LinkedHashMap<>(CAPACITY, 0.75f, true);

    /// How many codes have been encoded and kept. Read by the test that asserts
    /// a rebuild does not re-encode, and by nothing else.
    private static int encodings;

    private QrCache() {}

    /// The code for `payload` at `level`, encoding it only if it is new.
    ///
    /// @throws IllegalArgumentException if the payload does not fit in version 40
    static QrMatrix matrix(String payload, Level level) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(level, "level");
        var key = new Key(payload, level);
        synchronized (CODES) {
            var cached = CODES.get(key);
            if (cached != null) {
                return cached;
            }
        }
        // Encoded outside the lock: it is pure arithmetic over its argument, and
        // holding a monitor across it would make two windows encoding two
        // different codes wait for each other. Two threads asking for the same
        // new payload at the same moment both encode and one of the two answers
        // is kept, which costs one wasted encode and keeps the invariant that
        // matters -- that everybody afterwards gets the same instance.
        var encoded = QrEncoder.encode(payload, level);
        synchronized (CODES) {
            var raced = CODES.putIfAbsent(key, encoded);
            if (raced != null) {
                return raced;
            }
            encodings++;
            if (CODES.size() > CAPACITY) {
                var oldest = CODES.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            return encoded;
        }
    }

    /// How many codes this has encoded since the process started.
    static int encodings() {
        synchronized (CODES) {
            return encodings;
        }
    }

    /// Forgets everything, so a test starts from a known state.
    static void clear() {
        synchronized (CODES) {
            CODES.clear();
            encodings = 0;
        }
    }
}
