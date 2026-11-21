package dev.goldberry.example.ui.richinput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkRun;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.text.RichText;
import dev.goldberry.widgets.text.Run;

/// Marked text shown read-only: a `rich-text` with a run per stretch of one set
/// of marks, classed by the kinds over it, so `rich-text > run.bold` styles it
/// as `rich-input > mark.bold` styles the field.
///
/// A spoiler run is covered until it is pressed. A link, a mention or a custom
/// kind is pressable, and a press reports the kind it carries.
public final class MarkedRuns {

    private MarkedRuns() {}

    /// `text` as runs, with no press handlers.
    public static List<Run> of(MarkedText text) {
        return of(text, null);
    }

    /// `text` as runs, each run that carries a link, a mention or a custom kind
    /// telling `onPress` which; null for no handlers.
    public static List<Run> of(MarkedText text, @Nullable Consumer<MarkKind> onPress) {
        Objects.requireNonNull(text, "text");
        var runs = new ArrayList<Run>();
        for (var run : MarkRun.of(text.length(), text.marks())) {
            Set<String> classes = run.kinds().stream().map(MarkKind::className).collect(Collectors.toSet());
            var shown = new Run(text.text().substring(run.start(), run.end()), classes)
                    .spoiler(run.kinds().contains(MarkKind.Style.SPOILER));
            var pressed = pressable(run);
            if (onPress != null && pressed.isPresent()) {
                var kind = pressed.get();
                shown = shown.onPress(() -> onPress.accept(kind));
            }
            runs.add(shown);
        }
        return runs;
    }

    /// `text` as a `rich-text` with `attributes`.
    public static RichText richText(MarkedText text, @Nullable Consumer<MarkKind> onPress, Attributes attributes) {
        return new RichText(of(text, onPress), attributes);
    }

    /// The kind a press on `run` reports: a link, a mention, or a custom kind,
    /// whichever comes first; nothing for a run of styles alone.
    static Optional<MarkKind> pressable(MarkRun run) {
        return run.kinds().stream()
                .filter(kind -> !(kind instanceof MarkKind.Style))
                .sorted(MarkKind.ORDER)
                .findFirst();
    }
}
