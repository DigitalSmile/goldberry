package dev.goldberry.example.ui.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.Spacer;
import dev.goldberry.widgets.text.Text;

/// A short table of the system properties that change what the toolkit does,
/// each with what it does and what this run set it to.
///
/// Read more:
/// [Properties](https://goldberry.dev/docs/guide/logging.html#properties-an-application-can-set).
///
/// @param properties the rows
/// @param lookup     reads a property; `System::getProperty` in the window, a
///                   map in a test
public record RunProperties(List<Property> properties, UnaryOperator<@Nullable String> lookup)
        implements Widget.Stateless {

    /// One row: a property and what it does.
    ///
    /// @param name the property, without `-D`
    /// @param does what setting it does, in a phrase
    public record Property(String name, String does) {}

    /// The properties the card lists, from the chapter's table.
    public static final List<Property> LISTED = List.of(
            new Property("goldberry.backend.videoDriver", "the SDL video driver: x11, wayland, dummy"),
            new Property("goldberry.backend.vsync", "false turns pacing to the display off"),
            new Property("goldberry.frame.rate", "frames a second; 0 is unthrottled"),
            new Property("goldberry.gpu", "off or auto: whether any GPU is used"),
            new Property("goldberry.gpu.composite", "never, auto or always present through the GPU"),
            new Property("goldberry.input.primary", "ctrl or meta: what Primary means"),
            new Property("goldberry.motion.reduced", "reduce or full, instead of asking the desktop"),
            new Property("goldberry.trace.frames", "true or all: what each frame did to the tree"),
            new Property("goldberry.trace.input", "true: every pseudo-class input sets"),
            new Property("goldberry.css.lint", "true: lint the application's stylesheets"));

    public RunProperties {
        properties = List.copyOf(properties);
    }

    /// The listed properties, as this process has them.
    public static RunProperties ofThisRun() {
        return new RunProperties(LISTED, System::getProperty);
    }

    /// What `name` is set to in this run, or `not set`.
    String valueOf(String name) {
        var value = lookup.apply(name);
        return value == null ? "not set" : value;
    }

    @Override
    public Widget build(BuildContext context) {
        var rows = new ArrayList<Widget>(properties.size());
        for (var property : properties) {
            rows.add(new Column(
                    List.of(
                            new Row(
                                    List.of(
                                            new Text(property.name(), Attributes.NONE.classes("mono")),
                                            new Spacer(),
                                            new Text(
                                                    valueOf(property.name()),
                                                    Attributes.NONE
                                                            .id("property-" + property.name())
                                                            .classes("property-value"))),
                                    Attributes.NONE.classes("diagnostics-line")),
                            new Text(property.does(), Attributes.NONE.classes("caption"))),
                    Attributes.NONE.classes("property-row")));
        }
        return new Column(rows, Attributes.NONE.id("run-properties").classes("diagnostics-list"));
    }
}
