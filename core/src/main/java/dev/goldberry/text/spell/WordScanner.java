package dev.goldberry.text.spell;

import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// Finds the words in a line that a spell checker should be asked about.
///
/// A word is a run of letters, with an apostrophe inside it kept
/// ("don't", "l'eau") and a hyphen between two words splitting them. What is
/// not asked about at all:
///
/// - anything in a piece of text with `://` in it, or starting `www.`: a
///   link;
/// - anything in a piece with `@` in it, or starting `#`: an address, a
///   mention, a tag;
/// - a run with a digit in it, such as `mp3` or `2nd`;
/// - a single letter;
/// - a word in capitals, which is usually an acronym, and one with a capital
///   after a small letter, which is usually a name from code (`iPhone`,
///   `getValue`);
/// - text in a script that does not put spaces between its words (Chinese,
///   Japanese, Thai and their neighbours), where a run of letters is a
///   sentence rather than a word.
///
/// A piece of text here is what lies between two spaces.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public final class WordScanner {

    private WordScanner() {}

    /// A word to ask about: `[start, end)` of the line, and its text.
    public record Word(int start, int end, String text) {}

    /// The scripts whose runs of letters are not words.
    private static final Set<UnicodeScript> UNSPACED = Set.of(
            UnicodeScript.HAN,
            UnicodeScript.HIRAGANA,
            UnicodeScript.KATAKANA,
            UnicodeScript.THAI,
            UnicodeScript.LAO,
            UnicodeScript.KHMER,
            UnicodeScript.MYANMAR,
            UnicodeScript.TIBETAN);

    /// The words of `line` worth checking, in order.
    public static List<Word> words(String line) {
        var words = new ArrayList<Word>();
        var at = 0;
        var length = line.length();
        while (at < length) {
            while (at < length && Character.isWhitespace(line.charAt(at))) {
                at++;
            }
            var pieceEnd = at;
            while (pieceEnd < length && !Character.isWhitespace(line.charAt(pieceEnd))) {
                pieceEnd++;
            }
            if (pieceEnd > at && !isSkippedPiece(line.substring(at, pieceEnd))) {
                wordsIn(line, at, pieceEnd, words);
            }
            at = pieceEnd;
        }
        return List.copyOf(words);
    }

    private static boolean isSkippedPiece(String piece) {
        var lower = piece.toLowerCase(Locale.ROOT);
        return lower.contains("://") || lower.startsWith("www.") || piece.indexOf('@') >= 0 || piece.startsWith("#");
    }

    /// The runs of word characters in `[from, to)`: letters, marks and digits,
    /// with apostrophes between two of them.
    private static void wordsIn(String line, int from, int to, List<Word> into) {
        var at = from;
        while (at < to) {
            while (at < to && !isWordCharacter(line.codePointAt(at))) {
                at += Character.charCount(line.codePointAt(at));
            }
            var end = at;
            while (end < to) {
                var cp = line.codePointAt(end);
                if (isWordCharacter(cp)) {
                    end += Character.charCount(cp);
                } else if (isApostrophe(cp) && end > at && end + 1 < to && isWordCharacter(line.codePointAt(end + 1))) {
                    end++;
                } else {
                    break;
                }
            }
            if (end > at) {
                var text = line.substring(at, end);
                if (isWorthChecking(text)) {
                    into.add(new Word(at, end, text));
                }
            }
            at = end;
        }
    }

    private static boolean isWordCharacter(int cp) {
        return Character.isLetterOrDigit(cp)
                || Character.getType(cp) == Character.NON_SPACING_MARK
                || Character.getType(cp) == Character.COMBINING_SPACING_MARK;
    }

    private static boolean isApostrophe(int cp) {
        return cp == '\'' || cp == '’';
    }

    private static boolean isWorthChecking(String word) {
        var letters = 0;
        var upper = 0;
        var lowerSeen = false;
        var capitalAfterSmall = false;
        for (var i = 0; i < word.length(); ) {
            var cp = word.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isDigit(cp) || UNSPACED.contains(UnicodeScript.of(cp))) {
                return false;
            }
            if (Character.isLetter(cp)) {
                letters++;
                if (Character.isUpperCase(cp)) {
                    upper++;
                    capitalAfterSmall |= lowerSeen;
                } else if (Character.isLowerCase(cp)) {
                    lowerSeen = true;
                }
            }
        }
        return letters >= 2 && upper < letters && !capitalAfterSmall;
    }
}
