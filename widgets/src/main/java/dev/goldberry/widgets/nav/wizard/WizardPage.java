package dev.goldberry.widgets.nav.wizard;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// One page of a [Wizard]: what the indicator calls it, and what it shows.
///
/// ```kdl
/// page "Account" description="Who you are" {
///     field label="Name" { text-input bind="signup.name" }
/// }
/// page error=#true "Payment" { … }
/// ```
///
/// A description rather than a widget that draws itself —
/// [dev.goldberry.widgets.panel.tabs.Tab]'s arrangement, and
/// for the same reason: the wizard builds the indicator from every page and the
/// content from one, so a page cannot decide where it goes. `label`,
/// `description`, `error` and `reachable` are handed to the
/// [dev.goldberry.widgets.nav.steps.Step] the wizard makes
/// of it; the children are shown when the page is current and are not built at
/// all otherwise.
///
/// @param label       what the step is called
/// @param description an optional second line under it
/// @param error       whether the application says this page failed
/// @param reachable   whether the application lets a press on the indicator
///                    come here, when the wizard is `clickable`
/// @param children    the page's content
/// @param attributes  `id` and `class`, which land on the content area while
///                    this page is shown
@Markup("page")
public record WizardPage(
        String label,
        @Nullable String description,
        boolean error,
        boolean reachable,
        List<Widget> children,
        Attributes attributes)
        implements Widget.Leaf, Attributed<WizardPage> {

    /// Written out so that the parameters taking null for a default can say so (ADR-0497).
    public WizardPage(
            String label,
            @Nullable String description,
            boolean error,
            boolean reachable,
            @Nullable List<Widget> children,
            @Nullable Attributes attributes) {
        Objects.requireNonNull(label, "label");
        if (label.isEmpty()) {
            throw new IllegalArgumentException("a page needs a label: it is what the indicator calls it (§13)");
        }
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.label = label;
        this.description = description;
        this.error = error;
        this.reachable = reachable;
        this.children = children;
        this.attributes = attributes;
    }

    /// A page with a name and its content.
    public WizardPage(String label, Widget... children) {
        this(label, null, false, false, List.of(children), Attributes.NONE);
    }

    /// This page with a second line under its name.
    public WizardPage describe(@Nullable String value) {
        return new WizardPage(label, value, error, reachable, children, attributes);
    }

    /// This page, failed.
    public WizardPage error(boolean value) {
        return new WizardPage(label, description, value, reachable, children, attributes);
    }

    /// This page, one a press on the indicator may reach.
    public WizardPage reachable(boolean value) {
        return new WizardPage(label, description, error, value, children, attributes);
    }

    @Override
    public WizardPage withAttributes(Attributes value) {
        return new WizardPage(label, description, error, reachable, children, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// Builds a `page` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new WizardPage(
                Wiring.label(node),
                node.stringProperty("description"),
                node.booleanProperty("error"),
                node.booleanProperty("reachable"),
                children,
                Attributes.of(node));
    }
}
