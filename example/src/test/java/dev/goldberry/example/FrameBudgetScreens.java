package dev.goldberry.example;

/// The screens `FrameBudgetBenchmark` measures, kept where a test under `check`
/// can resolve them.
///
/// The benchmark runs in the benchmark lane and never under `check`, so a name
/// that stopped resolving would be found by the nightly at best. It has been,
/// twice: once when the gallery was reorganised into questions and `"controls"`
/// stopped being a screen, and again when it became a screen per chapter of the
/// guide and `"basic"` did. `FrameBudgetScreensTest` resolves these on every
/// build instead, the way `ShowcaseActionsTest` resolves the names
/// `BindingBenchmark` asks for.
public final class FrameBudgetScreens {

    /// A wall of cards, which is what most of this application is: the screen the
    /// stage budgets are measured against.
    public static final String WALL = "buttons";

    /// A **document**, one `text` widget per word: a different shape of tree, and
    /// the one that found the paragraph cache smaller than one frame.
    public static final String DOCUMENT = "markdown";

    /// The sheet of **1544 icons**, the biggest *model* in the application and,
    /// since a grid became a list of rows, no longer the biggest tree.
    public static final String SHEET = "icons";

    private FrameBudgetScreens() {}
}
