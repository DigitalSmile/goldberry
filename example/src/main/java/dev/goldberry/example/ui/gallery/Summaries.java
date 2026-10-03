package dev.goldberry.example.ui.gallery;

import java.util.regex.Pattern;

/// The rules a card's or a screen's summary is held to, in one place.
///
/// A summary is one or two sentences a reader takes in at a glance, so it has a
/// length limit. It says what the thing does, never which decision record
/// explains it: the record is for the toolkit's maintainers, and the card's link
/// goes to the guide, which is where a reader of the showcase wants to land.
public final class Summaries {

    /// The longest a summary may be, in characters.
    public static final int LIMIT = 280;

    /// A decision record cited by number, by the abbreviation, or by a link into
    /// the log. Written as character classes so this source does not cite one.
    private static final Pattern RECORD =
            Pattern.compile("\\b[Aa][Dd][Rr]s?\\b|\\b[Aa][Dd][Rr]-?\\s?\\d{3,4}\\b|book/src/[a][d][r]/");

    private Summaries() {}

    /// `text`, or an exception naming what is wrong with it.
    ///
    /// @param owner what the summary describes, for the message
    public static String require(String owner, String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException(owner + " needs a summary: one or two sentences a reader can take in");
        }
        if (text.length() > LIMIT) {
            throw new IllegalArgumentException(owner + "'s summary is " + text.length() + " characters; keep it under "
                    + LIMIT + " and let the guide say the rest");
        }
        if (citesARecord(text)) {
            throw new IllegalArgumentException(owner + "'s summary cites a decision record; say what the thing does"
                    + " and let the link open the guide");
        }
        return text;
    }

    /// Whether `text` cites a decision record.
    public static boolean citesARecord(String text) {
        return RECORD.matcher(text).find();
    }
}
