package dev.goldberry.content.select;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.text.Paragraph;
import dev.goldberry.text.flow.Hyphens;
import dev.goldberry.text.hyphen.Hyphenator;

/// Discretionary break positions for one joined document paragraph. Adjacent
/// spans in the same language share their words even when their faces differ.
final class DocumentHyphens {
    record Run(int start, int end, Hyphens mode, @Nullable Locale language) {}

    private @Nullable Paragraph source;
    private List<Run> runs = List.of();
    private @Nullable Paragraph result;

    static void append(List<Run> runs, int start, int end, Hyphens mode, @Nullable Locale language) {
        if (!runs.isEmpty()) {
            var last = runs.getLast();
            if (last.end == start && last.mode == mode && Objects.equals(last.language, language)) {
                runs.set(runs.size() - 1, new Run(last.start, end, mode, language));
                return;
            }
        }
        runs.add(new Run(start, end, mode, language));
    }

    Paragraph apply(Paragraph paragraph, List<Run> requested) {
        if (source == paragraph && result != null && runs.equals(requested)) {
            return result;
        }
        var text = paragraph.text();
        Paragraph resolved;
        if (requested.isEmpty() && text.indexOf('\u00ad') < 0) {
            resolved = paragraph;
        } else {
            resolved = paragraph.withHyphenation(offsets(text, requested));
        }
        runs = List.copyOf(requested);
        source = paragraph;
        result = resolved;
        return resolved;
    }

    static BitSet offsets(String text, List<Run> runs) {
        var offsets = new BitSet();
        var dictionaries = new HashMap<Locale, BitSet>();
        for (var run : runs) {
            if (run.mode == Hyphens.NONE) {
                continue;
            }
            if (run.mode == Hyphens.AUTO && run.language != null) {
                var locale = Hyphenator.dictionaryLocale(run.language);
                if (locale != null) {
                    dictionaries.computeIfAbsent(locale, _ -> new BitSet()).set(run.start, run.end);
                }
            }
            for (int at = text.indexOf('\u00ad', run.start, run.end);
                    at >= 0;
                    at = text.indexOf('\u00ad', at + 1, run.end)) {
                offsets.set(at + 1);
            }
        }
        // A span can end inside a word. Match the entire word, then keep
        // positions where both adjoining characters allow the dictionary.
        for (var entry : dictionaries.entrySet()) {
            var allowed = entry.getValue();
            for (var at : Hyphenator.breaks(text, entry.getKey())) {
                if (allowed.get(at - 1) && allowed.get(at)) {
                    offsets.set(at);
                }
            }
        }
        return offsets;
    }
}
