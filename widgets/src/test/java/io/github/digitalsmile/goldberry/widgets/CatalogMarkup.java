package io.github.digitalsmile.goldberry.widgets;

import java.util.List;

import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Widget;

/// Every widget the build registered, and the smallest markup that builds one.
///
/// ## Why the sweeps share this
///
/// `ChainingTest`, `ImmutabilityTest` and `WidgetParityTest` each ask a question
/// of *the catalog* — is every widget chainable, is every widget a value, is
/// every widget a record and a KDL node and a CSS type — and each of them used to
/// answer it against `Primitives.builtInTypes() + Controls.controlTypes()`, two
/// lists kept by hand. That was **24 names of the 72 the build registers**: every
/// widget under `form`, `panel`, `menu`, `nav`, `overlay` and `data` was outside
/// all three sweeps, and a control added there was swept by nothing.
///
/// The list is the **inflater's** now, which is the generated one — a widget
/// cannot be added to the catalog and left out of the sweeps, because there is no
/// second list to forget. `SemanticsSweepTest` and `AnimationSweepTest` already
/// read the catalog rather than a list; this is the same move for the other
/// three.
///
/// ## What is still written by hand, and why that is different
///
/// The **markup**, below. A dozen widgets refuse to exist without an argument —
/// §13's rule that a control nobody can read out is a failure rather than a blank
/// — so a bare node will not build them. That is a table of *arguments*, not a
/// table of names: leaving a widget out of it does not hide the widget from the
/// sweeps, it fails them with "a crumb needs a label", which is a message naming
/// exactly what to add.
public final class CatalogMarkup {

    private CatalogMarkup() {}

    /// Every node name the build registered, sorted.
    public static List<String> types() {
        return Widgets.inflater().registered().stream().sorted().toList();
    }

    /// A node of `type` carrying `attributes`, plus whatever it refuses to exist
    /// without.
    ///
    /// @param type       the registered node name
    /// @param attributes extra KDL properties, starting with a space, or empty
    public static String markup(String type, String attributes) {
        return switch (type) {
            // §13: a control with nothing to read out is a failure rather than a
            // blank, so these are handed a word.
            case "button", "chip", "crumb", "entry", "item", "link", "page", "step", "text" ->
                type + attributes + " \"Word\"";
            // The three that report by value: their group matches on it.
            case "option", "radio", "tab" -> type + attributes + " value=\"x\" \"X\"";
            case "image" -> type + attributes + " src=\"x.png\" alt=\"x\"";
            case "statistic" -> type + attributes + " label=\"Disk\" value=\"72\"";
            // A marker is the thing drawn on an axis, and holds exactly one.
            case "marker" -> type + attributes + " {\n    text \"x\"\n}";
            // Exactly two children: a three-way split is two split panes.
            case "split-pane" -> type + attributes + " {\n    panel\n    panel\n}";
            default -> type + attributes;
        };
    }

    /// The widget `type` inflates to, built the way markup builds it.
    public static Widget inflate(String type, String attributes) {
        return Widgets.inflater()
                .inflate(KdlParser.parse(markup(type, attributes)).getFirst());
    }
}
