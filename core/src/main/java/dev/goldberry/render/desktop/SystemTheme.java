package dev.goldberry.render.desktop;

/// What the desktop is set to — light or dark.
///
/// ```java
/// boolean dark = host.systemTheme().orElse(SystemTheme.LIGHT) == SystemTheme.DARK;
/// ```
///
/// The toolkit's own word for it, above the backend SPI, so an application reads
/// a setting rather than an SDL enum.
///
/// There is no `UNKNOWN` here, on purpose. A desktop that has no such setting
/// says nothing, and an application needs to tell **"the desktop says light"**
/// from **"the desktop does not say"** — the first is a theme and the second is a
/// default, and a third enum constant makes that distinction easy to forget in a
/// `switch`. [dev.goldberry.Host#systemTheme()] answers with an empty `Optional`
/// instead, which no caller can ignore by accident.
///
/// It is a *setting*, not a stylesheet. Goldberry ships `nord-light` and
/// `nord-dark` and does not choose between them: which theme an application uses
/// is the application's decision, and this is the one input to it that an
/// application cannot get for itself. An application that wants to follow the
/// desktop resolves this into its own theme choice; one that ships a single theme
/// ignores it and nothing changes.
///
/// Read more: [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html#the-desktops-theme).
public enum SystemTheme {

    /// Dark text on a light background.
    LIGHT,

    /// Light text on a dark background.
    DARK
}
