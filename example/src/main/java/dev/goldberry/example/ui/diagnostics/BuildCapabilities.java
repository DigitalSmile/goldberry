package dev.goldberry.example.ui.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.goldberry.Goldberry;
import dev.goldberry.platform.Capability;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.badge.Badge;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.Spacer;
import dev.goldberry.widgets.text.Text;

/// Every [Capability] the toolkit knows, and whether this build has it.
///
/// Read more: [What this build can do](https://goldberry.dev/docs/guide/logging.html#what-this-build-can-do).
///
/// @param present what `Goldberry.capabilities()` answered
public record BuildCapabilities(Set<Capability> present) implements Widget.Stateless {

    public BuildCapabilities {
        present = Set.copyOf(present);
    }

    /// What the loaded native library says it was built with.
    public static BuildCapabilities ofThisBuild() {
        return new BuildCapabilities(Goldberry.capabilities());
    }

    /// A capability as a reader would name it: `SYSTEM_THEME` is `system theme`.
    static String label(Capability capability) {
        return capability.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    @Override
    public Widget build(BuildContext context) {
        var rows = new ArrayList<Widget>(Capability.values().length);
        for (var capability : Capability.values()) {
            var has = present.contains(capability);
            rows.add(new Row(
                    List.of(
                            new Text(label(capability)),
                            new Spacer(),
                            new Badge(
                                    has ? "yes" : "no",
                                    null,
                                    Attributes.NONE
                                            .id("capability-"
                                                    + capability.name().toLowerCase(Locale.ROOT))
                                            .classes(has ? "success" : "warning"))),
                    Attributes.NONE.classes("diagnostics-line")));
        }
        return new Column(rows, Attributes.NONE.id("capabilities").classes("diagnostics-list"));
    }
}
