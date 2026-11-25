package dev.goldberry.content.select;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// Adjacent words in one style are shaped together. Each word keeps its
/// document position for copy and double-click selection.
public record StyledWords(List<Word> words, String text) implements Widget.Leaf, Styled, Paints {

    static Widget of(List<Word> group) {
        if (group.size() == 1) {
            return group.getFirst();
        }
        var text = new StringBuilder();
        for (var word : group) {
            if (!text.isEmpty() && word.entry().spaceBefore()) {
                text.append(' ');
            }
            text.append(word.text());
        }
        return new StyledWords(List.copyOf(group), text.toString());
    }

    public static List<Word> ofWidget(Widget widget) {
        return switch (widget) {
            case Word word -> List.of(word);
            case StyledWords run -> run.words();
            default -> List.of();
        };
    }

    @Override
    public String cssType() {
        return "word";
    }

    @Override
    public Set<String> classes() {
        return words.getFirst().classes();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.text(context.paragraph(style, text), style.color()).style(style);
    }
}
