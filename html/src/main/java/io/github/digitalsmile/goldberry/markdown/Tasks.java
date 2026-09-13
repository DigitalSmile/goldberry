package io.github.digitalsmile.goldberry.markdown;

import java.util.regex.Pattern;

/// Finding a task marker in the **source**, for
/// [Markdown#toggleTask(String, int)].
///
/// Package-private, and a scan rather than a parse. The obvious implementation is
/// `parse`, edit the model, write it back out — and it is wrong for this job in a way
/// worth writing down: a re-serialisation returns *a* document with the same meaning
/// rather than **the author's file**, so it would silently reflow their tables,
/// renumber their lists and normalise their line endings, all because somebody ticked
/// a box (ADR-0300).
///
/// So this finds one character and changes it. Everything else in the file comes back
/// byte for byte.
final class Tasks {

    /// A list item with a task box: an optional indent, a bullet or a number, at
    /// least one space, and `[ ]`, `[x]` or `[X]`.
    ///
    /// GitHub's rule, which md4c implements and which this has to agree with — the
    /// index the renderer hands over is counted by *that* parser, and a scanner that
    /// counted a different set of lines would tick the wrong box. The two are held
    /// together by `TasksTest`, which walks a document's model and this scanner over
    /// the same text and asserts they find the same tasks in the same order.
    private static final Pattern MARKER = Pattern.compile("^\\s{0,3}(?:[-*+]|\\d{1,9}[.)])\\s+\\[([ xX])]");

    /// A fence, which turns everything up to the matching one into a program.
    private static final Pattern FENCE = Pattern.compile("^\\s{0,3}(`{3,}|~{3,})");

    private Tasks() {}

    /// What a scan reports: one task box, and where its state character is.
    @FunctionalInterface
    private interface Visitor {

        /// @return true to stop the scan
        boolean task(int line, int at);
    }

    /// Walks `lines`, reporting every task box outside a fence, in order.
    private static void scan(String[] lines, Visitor visitor) {
        var fence = "";
        for (var i = 0; i < lines.length; i++) {
            var line = lines[i];
            var fenced = FENCE.matcher(line);
            if (fenced.find()) {
                var marker = fenced.group(1);
                if (fence.isEmpty()) {
                    // Opened. The closing fence has to be at least as long and of the
                    // same character, which is CommonMark's rule and the reason this
                    // keeps the opener rather than a boolean.
                    fence = marker;
                } else if (marker.charAt(0) == fence.charAt(0) && marker.length() >= fence.length()) {
                    fence = "";
                }
                continue;
            }
            if (!fence.isEmpty()) {
                // Inside a program that happens to contain `- [ ]`.
                continue;
            }
            var matcher = MARKER.matcher(line);
            if (matcher.find() && visitor.task(i, matcher.start(1))) {
                return;
            }
        }
    }

    /// `markdown` with the `index`th task box flipped, or unchanged when there is no
    /// such task.
    static String toggle(String markdown, int index) {
        var lines = markdown.split("\n", -1);
        var seen = new int[] {0};
        var edited = new boolean[] {false};
        scan(lines, (line, at) -> {
            if (seen[0]++ != index) {
                return false;
            }
            var text = lines[line];
            var flipped = text.charAt(at) == ' ' ? 'x' : ' ';
            lines[line] = text.substring(0, at) + flipped + text.substring(at + 1);
            edited[0] = true;
            return true;
        });
        return edited[0] ? String.join("\n", lines) : markdown;
    }

    /// How many task boxes `markdown` has, by this scanner's counting.
    ///
    /// Only a test asks — but what it asks is the load-bearing question: that this
    /// agrees with md4c about what a task is.
    static int count(String markdown) {
        var seen = new int[] {0};
        scan(markdown.split("\n", -1), (line, at) -> {
            seen[0]++;
            return false;
        });
        return seen[0];
    }
}
