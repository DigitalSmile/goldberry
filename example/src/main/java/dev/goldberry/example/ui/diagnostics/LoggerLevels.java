package dev.goldberry.example.ui.diagnostics;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.badge.Badge;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.Spacer;
import dev.goldberry.widgets.text.Text;

/// Logger names, each with the most detailed level this run's configuration
/// lets through.
///
/// Asked of SLF4J, so it answers for whatever provider is bound and says `off`
/// when none is.
///
/// Read more: [A logback configuration](https://goldberry.dev/docs/guide/logging.html#a-logback-configuration).
///
/// @param id    the list's id
/// @param names the loggers to ask about
public record LoggerLevels(String id, List<String> names) implements Widget.Stateless {

    public LoggerLevels {
        names = List.copyOf(names);
    }

    /// The most detailed level `logger` lets through, or `off`.
    static String levelOf(Logger logger) {
        if (logger.isTraceEnabled()) {
            return "trace";
        }
        if (logger.isDebugEnabled()) {
            return "debug";
        }
        if (logger.isInfoEnabled()) {
            return "info";
        }
        if (logger.isWarnEnabled()) {
            return "warn";
        }
        return logger.isErrorEnabled() ? "error" : "off";
    }

    @Override
    public Widget build(BuildContext context) {
        var rows = new ArrayList<Widget>(names.size());
        for (var name : names) {
            var level = levelOf(LoggerFactory.getLogger(name));
            rows.add(new Row(
                    List.of(
                            new Text(name, Attributes.NONE.classes("mono")),
                            new Spacer(),
                            new Badge(level, null, Attributes.NONE.classes(level.equals("off") ? "warning" : "info"))),
                    Attributes.NONE.classes("diagnostics-line")));
        }
        return new Column(rows, Attributes.NONE.id(id).classes("diagnostics-list"));
    }
}
