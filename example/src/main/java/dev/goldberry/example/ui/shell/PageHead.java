package dev.goldberry.example.ui.shell;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.home.HomeScreen;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.Spacer;
import dev.goldberry.widgets.text.Text;

/// The slim row above a screen: Back and Forward, and where the screen is.
///
/// ```text
/// ←  →   Home › Controls › Buttons                 Previous · Next
/// ```
///
/// The trail's first step goes Home; the section is a label, because a section
/// is a heading in the sidebar rather than a page. Previous and Next walk the
/// sidebar's order, as `Ctrl+PgUp` and `Ctrl+PgDn` do.
///
/// Read more: [Navigation](https://goldberry.dev/docs/components/navigation.html#breadcrumbs).
///
/// @param screen   the screen showing
/// @param back     whether Back would go anywhere
/// @param forward  whether Forward would go anywhere
/// @param actions  what the row's buttons ask for
public record PageHead(String screen, boolean back, boolean forward, Moves actions) implements Widget.Stateless {

    /// The row's id.
    public static final String ID = "page-head";

    /// What the row's buttons ask for.
    ///
    /// @param back     the screen before
    /// @param forward  the screen Back left
    /// @param previous the screen above in the sidebar
    /// @param next     the screen below in the sidebar
    /// @param home     Home
    public record Moves(Runnable back, Runnable forward, Runnable previous, Runnable next, Runnable home) {}

    @Override
    public Widget build(BuildContext context) {
        var children = new ArrayList<Widget>();
        children.add(TopBar.iconButton("back", "arrow-left", "Back (Alt+Left)", actions.back())
                .disabled(!back));
        children.add(TopBar.iconButton("forward", "arrow-right", "Forward (Alt+Right)", actions.forward())
                .disabled(!forward));
        children.addAll(trail());
        children.add(new Spacer());
        if (Gallery.has(screen)) {
            children.add(step("previous-screen", "Previous", "Previous screen (Ctrl+PgUp)", actions.previous()));
            children.add(step("next-screen", "Next", "Next screen (Ctrl+PgDn)", actions.next()));
        }
        return new Row(children, Attributes.NONE.id(ID));
    }

    private List<Widget> trail() {
        if (!Gallery.has(screen)) {
            return List.of(new Text(HomeScreen.TITLE, Attributes.NONE.classes("crumb", "current")));
        }
        var tab = Gallery.tab(screen);
        return List.of(
                new Pressable(
                        "Home",
                        actions.home(),
                        false,
                        List.of(new Text(HomeScreen.TITLE)),
                        Attributes.NONE.classes("crumb", "link")),
                new Text("›", Attributes.NONE.classes("crumb-separator")),
                new Text(tab.section().title(), Attributes.NONE.classes("crumb")),
                new Text("›", Attributes.NONE.classes("crumb-separator")),
                new Text(tab.title(), Attributes.NONE.classes("crumb", "current")));
    }

    private static Widget step(String id, String label, String name, Runnable onPress) {
        return new Pressable(
                name,
                onPress,
                false,
                List.of(new Text(label)),
                Attributes.NONE.id(id).classes("page-step").tooltip(name));
    }
}
