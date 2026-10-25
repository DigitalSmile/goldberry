package dev.goldberry.example.model.navigation;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Action;
import dev.goldberry.bind.Bind;
import dev.goldberry.bind.Model;

/// Where the Panels and Navigation screens say a reader is: the chapters open
/// in the tab strip and which of them is showing, and the breadcrumbs' path.
///
/// **Assignment is what is observed**, which is why every list here gets
/// replaced rather than edited. A list mutated in place changes nothing anybody
/// can see — the same rule the weaver enforces by refusing to bind an array at
/// all.
///
/// Read more: [Values](https://goldberry.dev/docs/applications.html#values).
@Model
// Every field below is read, and none of them is read *here*: a `@Bind` field is
// reached by path through the generated registry, or through a woven call site
// in a native image. The reader is generated.
@SuppressWarnings("UnusedVariable")
public final class NavigationModel {

    /// The places a new chapter is named after, in order. Ten of them, which is
    /// more than anybody will open — and a name that repeats would be two tabs
    /// with one identity, which a strip keyed by name cannot tell apart.
    private static final List<String> STAGES = List.of(
            "Bree",
            "Weathertop",
            "Lothlórien",
            "Anduin",
            "Amon Hen",
            "Fangorn",
            "Edoras",
            "Helm's Deep",
            "Osgiliath",
            "Cirith Ungol");

    /// The places a deeper path goes, in order — the trail's equivalent of
    /// [#STAGES], and long enough to push the row past its overflow twice over.
    static final List<String> DEEPER = List.of(
            "Library",
            "Reference",
            "Middle-earth",
            "Shire",
            "Hobbiton",
            "Bag End",
            "The Red Book",
            "Chapter One",
            "A Long-expected Party");

    // --- the chapters --------------------------------------------------------

    /// The chapters open in the strip, and which of them is showing. The strip
    /// reports what the user asked for and changes nothing itself — closing a
    /// chapter removes it from here or it does not close.
    @Bind("app.tabs")
    @SuppressWarnings("unused")
    private List<String> tabs = List.of("Rivendell", "Moria");

    @Bind("app.tab")
    @SuppressWarnings("unused")
    private @Nullable String tab = "Rivendell";

    /// How many chapters have been opened, so a new one gets a name nobody has
    /// used.
    ///
    /// Bound, and declared `repaint = false`: it is genuinely part of what this
    /// model knows, and nothing on screen shows it, so a change to it has no
    /// frame to ask for. It is also why [Actions] can be a record —
    /// this counter is state, and it lives with the rest of the state rather than
    /// in the thing that increments it.
    @Bind(value = "app.tabs-added", repaint = false)
    @SuppressWarnings("unused")
    private int added;

    // --- the trail -----------------------------------------------------------

    /// Where the breadcrumbs say you are.
    ///
    /// The whole path, root first, and the **application** owns it: the trail
    /// draws what is here and a crumb asks for a prefix of it, which is the same
    /// loop every other control in this window runs.
    @Bind("app.path")
    @SuppressWarnings("unused")
    private List<String> path = List.of("Home", "Library", "Reference");

    /// The path the breadcrumbs draw, root first.
    public List<String> path() {
        return path;
    }

    /// Everything that *happens* to the chapters and the trail.
    ///
    /// The strip and the trail are built in Java and hold these handlers
    /// directly; only Go deeper is also an `@Action`, so a document may press it.
    ///
    /// Read more: [Actions](https://goldberry.dev/docs/applications.html#actions).
    @dev.goldberry.bind.runtime.Actions
    public record Actions(NavigationModel values) {

        // --- the chapters ----------------------------------------------------

        /// Shows a chapter. What the strip asks for and this decides, like every
        /// other value here.
        public void pickTab(String value) {
            values.tab = value;
        }

        /// Closes one — and picks a neighbour when it was the one being shown, because
        /// a strip whose selection has been removed shows nothing at all.
        public void closeTab(String value) {
            var current = new ArrayList<>(values.tabs);
            var index = current.indexOf(value);
            if (index < 0) {
                return;
            }
            current.remove(index);
            // A new list rather than a mutation: a subscriber is subscribed to the
            // *value*, and a list changed in place is the same value.
            values.tabs = List.copyOf(current);
            if (value.equals(values.tab)) {
                values.tab = current.isEmpty() ? null : current.get(Math.min(index, current.size() - 1));
            }
        }

        /// Moves a chapter to a new place in the strip — what a drag asks for.
        /// The index is among the other chapters, so it is where the
        /// moved one lands once it has been taken out.
        public void moveTab(String value, int index) {
            var current = new ArrayList<>(values.tabs);
            if (!current.remove(value)) {
                return;
            }
            current.add(Math.clamp(index, 0, current.size()), value);
            values.tabs = List.copyOf(current);
        }

        /// Opens one, and shows it — which is what every editor does with a new
        /// tab. The name is the next place on the road, so a strip of chapters
        /// reads as a journey rather than as `Untitled 3`.
        public void newTab() {
            var name = STAGES.get(values.added++ % STAGES.size());
            var current = new ArrayList<>(values.tabs);
            // A repeat would be two tabs with one identity, and a strip keyed by
            // name cannot tell those apart -- so the road is walked twice rather
            // than the same stage being opened twice.
            while (current.contains(name)) {
                name = name + " again";
            }
            current.add(name);
            values.tabs = List.copyOf(current);
            values.tab = name;
        }

        // --- the trail -------------------------------------------------------

        /// Walks back up the path to `depth` steps.
        ///
        /// What a crumb asks for. The trail draws whatever this leaves behind,
        /// and a crumb that asked for a depth the path no longer has is clamped
        /// rather than refused — the path is the application's and may have
        /// changed since the frame the click was aimed at.
        public void goUp(int depth) {
            var wanted = Math.clamp(depth, 1, Math.max(1, values.path.size()));
            values.path = List.copyOf(values.path.subList(0, wanted));
        }

        /// Goes deeper, so the trail can be pushed past its overflow.
        @Action("app.go-deeper")
        @SuppressWarnings("unused")
        public void goDeeper() {
            if (values.path.size() >= DEEPER.size() + 1) {
                return;
            }
            var next = new ArrayList<>(values.path);
            next.add(DEEPER.get(values.path.size() - 1));
            values.path = List.copyOf(next);
        }
    }
}
