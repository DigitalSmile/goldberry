/*
 * Everything you are likely to change on the landing page lives here (news
 * lives in news.js). Edit, commit, push to master; the Pages workflow does
 * the rest.
 *
 * version: CI takes `goldberryVersion` from gradle.properties and writes it
 *          into assets/version.js; the value here is the local-preview
 *          fallback. Code samples use {{version}} and get the real one.
 */
window.SITE = {
  version: "2026.2",
  versionNote: "calendar versions: YEAR.RELEASE",

  tagline: "Build fast, lightweight desktop apps in no time.",
  lede:
    "Goldberry is an open-source, declarative UI toolkit for Java 25+. A window " +
    "is three lines, a screen is a KDL file that reloads while it runs, and the " +
    "same code runs as a plain jar on any JDK or compiles with GraalVM into one " +
    "native binary. No JNI, no bundled web engine. Apache 2.0.",

  // The strip under the hero copy.
  stats: [
    { value: "Java 25+", unit: "", text: "a plain jar on any JDK; GraalVM native image optional" },
    { value: "Apache 2.0", unit: "", text: "open source, no CLA, no dual licence" },
    { value: "0", unit: "JNI", text: "FFM bindings and no third-party Java deps" },
    { value: "3", unit: "platforms", text: "Linux, Windows, macOS" }
  ],

  // Search engines, link previews, and the llms.txt that agents read.
  seo: {
    url: "https://goldberry.dev/",
    title: "Goldberry: open-source Java UI toolkit for fast, lightweight desktop apps",
    description:
      "Goldberry is an open-source (Apache 2.0) declarative desktop UI toolkit for Java 25+. " +
      "Real CSS and flexbox, KDL markup with hot reload, pure Java over native libraries via the FFM API, " +
      "optional GraalVM native image. Linux, Windows, macOS.",
    keywords: [
      "Java UI toolkit", "Java GUI library", "Java desktop UI", "declarative UI Java", "open source Java UI",
      "JavaFX alternative", "Swing alternative", "Compose for Desktop alternative", "FFM API", "Project Panama",
      "GraalVM native image GUI", "Blend2D", "Yoga flexbox", "KDL", "CSS", "Nord theme", "Apache 2.0"
    ],
    // One-line answers for the FAQ section, the FAQPage schema and llms.txt.
    faq: [
      { q: "What is Goldberry?", a: "An open-source, declarative desktop UI toolkit for Java 25+. Widgets are immutable Java records or KDL markup, styled with a real CSS subset and laid out with Yoga flexbox, drawn by its own rendering pipeline. Licensed under Apache 2.0." },
      { q: "Which Java version does it need?", a: "Java 25 or newer. It uses the Foreign Function & Memory API, so there is no JNI and no third-party Java dependency." },
      { q: "Do I need GraalVM?", a: "No. Goldberry runs as a plain jar on any JDK 25+. GraalVM native image is an optional, supported target that produces one self-contained executable, with the native library inside it." },
      { q: "Which platforms are supported?", a: "Linux (Wayland and X11), Windows and macOS, as peer platforms behind one SDL3 backend. There is also a headless backend for tests and servers." },
      { q: "Does it need a GPU?", a: "No. Frames are rasterized on the CPU with Blend2D across worker threads. The goldberry-gpu module adds GPU composition, a canvas3d layer and GPU video presentation when you want them." },
      { q: "How is it licensed?", a: "Apache License 2.0, with no contributor licence agreement and no commercial edition. The source is on GitHub at DigitalSmile/goldberry." },
      { q: "How do I add it to a project?", a: "Add the goldberry-bom to Gradle or Maven, then the goldberry artifact plus the goldberry-natives classifier jars for the platforms you run on. Coordinates are under dev.goldberry." }
    ]
  },

  links: {
    docs: "docs/",
    github: "https://github.com/DigitalSmile/goldberry",
    license: "https://github.com/DigitalSmile/goldberry/blob/master/LICENSE",
    // Hero buttons, in order. `primary: true` is the dark button, `github: true` adds the logo and the star count.
    hero: [
      { label: "Get started", href: "#quickstart", primary: true },
      { label: "Star on GitHub", href: "https://github.com/DigitalSmile/goldberry", github: true },
      { label: "Read the docs", href: "docs/getting-started/requirements.html" }
    ]
  },

  // Every name a KDL file can use today, from the @Markup annotations in the repository.
  markupNames: "action affix area-chart audio-player badge bar-chart breadcrumbs button canvas canvas3d card carousel checkbox chip code-input collapse color-picker column crumb date-picker dialog donut-chart entry field form group-box html-view hud image item knob line-chart link list markdown-view marker masonry media-controls media-player menu menubar message option page panel point popover progress qr-code radio radio-group row scroll segmented select separator series skeleton slider spacer sparkline spinner split-pane stack statistic step steps tab table tabs text text-area text-input time-picker timeline toggle tree video-view wizard".split(" "),

  // Showcase screenshots (assets/shots/, the toolkit's own golden images). `zooms` are
  // looking-glass insets: the centre of the region in 1200x900 picture coordinates.
  shots: [
    { file: "basic.webp", title: "Controls", text: "Buttons, toggles, radios, sliders, badges and chips, drawn to the design system's metrics.", zooms: [[720, 232], [200, 700]] },
    { file: "forms-light.webp", title: "Forms, light theme", text: "Fields that validate, date and colour pickers, a one-time-code input. One switch restyles every control.", zooms: [[640, 585], [500, 525]] },
    { file: "charts.webp", title: "Charts", text: "Line, bar, area and donut charts, first-party and drawn on the same canvas primitive as everything else.", zooms: [[1070, 318], [960, 560]] },
    { file: "collections.webp", title: "Lists, tables, trees", text: "A virtualized list of ten thousand rows, a sortable table and a tree with tri-state checkboxes.", zooms: [[720, 330], [170, 700]] },
    { file: "markdown.webp", title: "Markdown", text: "A live editor beside its preview. The rendered side is ordinary widgets, with no browser engine underneath.", zooms: [[300, 430], [960, 470]] },
    { file: "overlays.webp", title: "Menus and dialogs", text: "Menus that open their own platform window, a dialog that traps focus, context menus and banners.", zooms: [[1000, 270], [1000, 520]] }
  ],

  // The three big feature rows, each with a showcase screenshot.
  highlights: [
    {
      eyebrow: "Declarative",
      title: "Describe the screen. The toolkit keeps it true.",
      text: "A widget is an immutable Java record with a pure build(), or the same tree as a KDL node. The element tree behind it persists, so state and :hover survive a parent re-describing its child.",
      points: ["Every widget is a record, a node and a CSS type", "Markup names an action and a value to bind to; it never contains code", "A typo fails at inflation, not silently at a click"],
      shot: "basic.webp", shotAlt: "The Goldberry showcase: buttons, toggles, radios, sliders, badges and chips", href: "docs/components/index.html", hrefLabel: "Read about widgets"
    },
    {
      eyebrow: "Real CSS, real flexbox",
      title: "Style it like the web. Reload it while it runs.",
      text: "A genuine CSS subset with variables, cascade and transitions, laid out by Yoga. Every colour is a --gb-* token, so a theme switch restyles controls whose rules never mention one. Stylesheets and markup hot-reload.",
      points: ["Nord light and dark out of the box", "Regular and compact density that no widget mentions", "Transitions on a frame clock, deterministic in tests"],
      shot: "forms-light.webp", shotAlt: "The showcase's forms screen in the light theme", href: "docs/guide/styling.html", hrefLabel: "Read about styling"
    },
    {
      eyebrow: "Light to ship",
      title: "A jar on any JDK 25+, or one native binary with GraalVM.",
      text: "The same application runs as a plain jar, with the native library in a classifier jar, or compiles with GraalVM native image into one 41 MiB executable with that library inside it. Frames are rasterized on the CPU, so plain UI needs no GPU context, and only the damaged region is repainted.",
      points: ["No JNI and no third-party Java dependencies", "Linux, Windows and macOS behind one SDL3 backend", "A GPU lane, video and audio, and a web view as optional modules"],
      shot: "charts.webp", shotAlt: "The showcase's charts screen: line, bar, area and donut charts", href: "docs/native.html", hrefLabel: "Read about native image"
    }
  ],

  // Feature cards. `icon` is a name from assets/icons.js (Lucide).
  features: [
    { icon: "cpu", title: "Pure Java over FFM", text: "Hand-written Foreign Function & Memory bindings on Java 25+.", points: ["No JNI and no third-party Java dependencies", "One statically linked native library", "JPMS keeps raw memory inside the natives module"] },
    { icon: "monitor", title: "Three platforms, one SPI", text: "Linux, Windows and macOS are peer platforms from the first commit.", points: ["Wayland and X11, through a single SDL3 backend", "Fractional DPI correct by construction", "A headless backend for CI and servers"] },
    { icon: "type", title: "Text, icons and emoji", text: "Typography is part of the toolkit, not something the platform decides.", points: ["HarfBuzz shaping with Inter and JetBrains Mono embedded", "1544 Lucide icons bundled", "Noto Color Emoji as an opt-in module, drawn from its paint graphs"] },
    { icon: "play", title: "Video and audio", text: "goldberry-media drives FFmpeg from Java; audio leaves through SDL, video is a widget.", points: ["Hardware decode, track switching, subtitles, network streams", "Royalty-free codecs built in; bring your own through a decoder SPI", "4K60 with every picture shown when the GPU module is present"] },
    { icon: "box", title: "A GPU when you want one", text: "The CPU path never needs a GPU context. goldberry-gpu adds one, bound to SDL_GPU.", points: ["Windows composite through the GPU by default, and fall back to the CPU", "canvas3d: a GPU layer your own renderer draws into", "Shaders in HLSL, compiled and committed"] },
    { icon: "globe", title: "A web page, when the platform has one", text: "web-view drives the desktop's own engine through a separate optional library.", points: ["WebKitGTK, WebView2 and WKWebView", "Never a load-time dependency of the toolkit", "Ask Goldberry.capabilities() for WEB_VIEW before offering one"] },
    { icon: "blocks", title: "Plain-Java models", text: "@Model, @Bind and @Action make ordinary field assignments observable.", points: ["A jar binds at run time through the annotations", "A build-time weaver prepares the same model for native image", "No annotation processor, no code generation step"] },
    { icon: "flask-conical", title: "Content widgets, tested in pixels", text: "Markdown, HTML and charts render as ordinary widgets, with no browser engine underneath.", points: ["markdown-view and html-view, first-party charts", "Golden images per platform", "A virtual frame clock makes animation deterministic"] }
  ],

  // "Starts fast, runs fast". Plain numbers from the README and the Status page.
  perf: {
    start: {
      title: "Starts fast",
      text: "The README's own start-up trace, on a plain JDK. The first line is the JVM; the last three are the toolkit.",
      items: [
        { value: "534", unit: "ms", label: "the JVM starting", note: "before any Goldberry code runs" },
        { value: "26", unit: "ms", label: "native library mapped", note: "one static library, bound through FFM" },
        { value: "163", unit: "ms", label: "SDL video subsystem up", note: "window, input and display ready" },
        { value: "117", unit: "ms", label: "first frame presented", note: "about 330 ms of toolkit work in total" }
      ],
      note: "With GraalVM native image the first line is the one that changes: there is no JVM to start and no classes to load, so the executable reaches its first frame after the toolkit's own work."
    },
    run: {
      title: "Runs fast",
      items: [
        { value: "3.1", unit: "ms", label: "median frame", note: "960×640 with a wrapped paragraph, paced to the display; 4.3 ms at the 95th percentile" },
        { value: "2.3", unit: "ms", label: "a full 4K raster", note: "3840×2160, Blend2D across four paint workers" },
        { value: "117", unit: "µs", label: "repainting one change", note: "only the damaged region is drawn again" },
        { value: "9", unit: "µs", label: "a frame where nothing changed", note: "layout and tree walk on the retained element tree" }
      ]
    },
    caveat: "Measured on one Linux machine. The three-platform run of the same benchmarks is in progress.",
    caveatHref: "docs/status.html"
  },

  // Quick start. Group "deps" is the build-file card, "jvm" the plain-Java card, "native" the GraalVM card.
  quickstart: {
    tabs: [
      { group: "deps", label: "Gradle", lang: "groovy", code:
"repositories {\n    mavenCentral()\n}\ndependencies {\n    implementation platform('dev.goldberry:goldberry-bom:{{version}}')\n    // common, natives, core and widgets in one\n    implementation 'dev.goldberry:goldberry'\n    // optional: Markdown and HTML, video and audio, the GPU lane\n    implementation 'dev.goldberry:goldberry-html'\n    // the native library, one classifier per platform you run on\n    runtimeOnly 'dev.goldberry:goldberry-natives::linux-x64'\n    runtimeOnly 'dev.goldberry:goldberry-natives::linux-aarch64'\n    runtimeOnly 'dev.goldberry:goldberry-natives::macos-aarch64'\n    runtimeOnly 'dev.goldberry:goldberry-natives::windows-x64'\n}" },
      { group: "deps", label: "Maven", lang: "xml", code:
"<dependencyManagement>\n  <dependencies>\n    <dependency>\n      <groupId>dev.goldberry</groupId>\n      <artifactId>goldberry-bom</artifactId>\n      <version>{{version}}</version>\n      <type>pom</type>\n      <scope>import</scope>\n    </dependency>\n  </dependencies>\n</dependencyManagement>\n<dependencies>\n  <dependency>\n    <groupId>dev.goldberry</groupId>\n    <artifactId>goldberry</artifactId>\n  </dependency>\n  <dependency>\n    <groupId>dev.goldberry</groupId>\n    <artifactId>goldberry-natives</artifactId>\n    <classifier>linux-x64</classifier>\n    <scope>runtime</scope>\n  </dependency>\n</dependencies>" },
      { group: "jvm", label: "Java", lang: "java", code:
"var window = Window.open(\"Hello\", 960, 640);\nwindow.onPaint(frame -> frame.fill(0xFF2E3440));\nGoldberry.run();\n\n// Work that is not instant goes off the UI thread\n// and comes back on it:\nGoldberry.async(() -> loadTheThing())\n         .thenAccept(thing -> window.title(thing.name()));" },
      { group: "jvm", label: "Run", lang: "sh", code:
"# Any JDK 25 or newer. Nothing to install beyond the jars.\n./gradlew run\n\n# macOS: AppKit wants the process's first thread, exactly as for\n# LWJGL or SWT. `gradlew run` passes the flag; a plain launch needs it:\njava -XstartOnFirstThread -jar app.jar" },
      { group: "native", label: "Build", lang: "sh", code:
"# Optional. Needs a GraalVM for Java 25+; the result needs nothing.\n./gradlew :example:nativeImage -Pgraalvm.home=/path/to/graalvm\n\n# One self-contained file, the native library inside it.\n./example/build/native/goldberry-showcase-linux-x64" },
      { group: "native", label: "What the weaver does", lang: "java", code:
"// @Model fields become observable at build time, so native image\n// needs no reflection and no reachability metadata from you.\n@Model\nclass Form {\n    String name;      // bound from markup: text-input bind=\"name\"\n    @Action void save() { /* named from markup: button press=\"save\" */ }\n}" }
    ]
  },

  // The design-system card: what the Goldberry Design System fixes, from docs/goldberry-design-system.md.
  designSystem: {
    title: "A design system, not a theme file",
    text: "Nord's sixteen colours alias into --gb-* tokens; everything else is metrics the widgets obey: a 4 px ramp, a type scale, two densities, three materials and a motion scale. Switch the theme and every control restyles, though no widget's rule names a colour.",
    swatches: ["#2E3440", "#3B4252", "#434C5E", "#4C566A", "#D8DEE9", "#E5E9F0", "#ECEFF4", "#8FBCBB", "#88C0D0", "#81A1C1", "#5E81AC", "#BF616A", "#D08770", "#EBCB8B", "#A3BE8C", "#B48EAD"],
    spacing: [4, 8, 12, 16, 24, 32, 48],
    facts: [
      ["Themes", "Nord light and Nord dark, switchable at run time"],
      ["Type", "Inter and JetBrains Mono embedded; body 13/18, a fixed scale above it"],
      ["Density", "regular (32 px controls) and compact (28 px), set once per window"],
      ["Materials", "opaque, frost and veil, with an automatic opaque fallback"],
      ["Motion", "100 / 160 / 240 ms durations; enters ease out, exits are faster than enters"],
      ["Icons", "1544 Lucide icons and Noto Color Emoji, both as widgets"],
      ["Scrollbars", "one spec for the whole scroll family, overlay and classic"]
    ],
    href: "docs/guide/design-system.html", hrefLabel: "The design system"
  },

  // The CSS card: a stylesheet as the showcase writes one.
  cssSamples: [
    { label: "theme.css", lang: "css", code: "/* Alias tokens: the only place a colour is named. */\n:root {\n  --gb-accent: var(--nord10);\n  --gb-surface: var(--nord1);\n  --gb-text: var(--nord4);\n  --gb-button-bg: var(--nord3);\n  --gb-button-danger-bg: var(--nord11);\n}" },
    { label: "controls.css", lang: "css", code: "button {\n  background: var(--gb-button-bg);\n  color: var(--gb-text);\n  border-radius: var(--gb-radius-md);\n  transition: background var(--gb-motion-fast);\n}\n\nbutton:hover { background: var(--gb-button-bg-hover); }\nbutton:focus-visible { outline: 2px solid var(--gb-accent); }\n\nbutton.danger {\n  background: var(--gb-button-danger-bg);\n  color: var(--gb-button-danger-text);\n}" },
    { label: "screen.kdl", lang: "kdl", code: "// The class is the whole contract between markup and stylesheet.\nrow {\n    spacer\n    button press=\"dismiss\" \"Cancel\"\n    button class=\"danger\" press=\"delete\" \"Delete\"\n}" }
  ],

  // "How it compares". The first `us` columns are Goldberry; a cell is a string or
  // { t: "text", tone: "good" | "mid" | "no" } to add a coloured mark.
  compare: {
    columns: ["Goldberry on a JDK", "Goldberry, GraalVM native", "JavaFX", "Swing", "Compose for Desktop", "SWT"],
    us: 2,
    rows: [
      { label: "Language", cells: ["Java 25+", "Java 25+", "Java", "Java", "Kotlin", "Java"] },
      { label: "Licence", cells: [
        { t: "Apache 2.0", tone: "good" }, { t: "Apache 2.0", tone: "good" }, "GPL v2 + Classpath exception", "GPL v2 + Classpath exception", "Apache 2.0", "EPL 2.0"] },
      { label: "UI model", cells: [
        { t: "Declarative: immutable records with a pure build(), or KDL markup", tone: "good" }, { t: "Same", tone: "good" },
        "Scene graph; imperative or FXML", "Imperative component tree", { t: "Declarative @Composable functions", tone: "good" }, "Imperative, native widgets"] },
      { label: "Markup", cells: [
        { t: "KDL, hot-reloaded while running", tone: "good" }, { t: "KDL, hot-reloaded while running", tone: "good" }, { t: "FXML", tone: "mid" }, { t: "None", tone: "no" }, { t: "None, code only", tone: "no" }, { t: "None", tone: "no" }] },
      { label: "Styling and layout", cells: [
        { t: "A real CSS subset and Yoga flexbox", tone: "good" }, { t: "Same", tone: "good" }, "JavaFX CSS dialect (-fx-*), layout panes", "Look and Feel, layout managers", "Kotlin modifiers", "Platform look, layout managers"] },
      { label: "Rendering", cells: [
        "Own pipeline: Blend2D on the CPU; GPU composition and canvas3d optional", "Same", "Prism, GPU with software fallback", "Java2D", "Skia (Skiko), GPU", "The platform toolkit: GTK, Win32, Cocoa"] },
      { label: "Text", cells: [
        "HarfBuzz shaping; Inter and JetBrains Mono embedded; colour emoji", "Same", "Platform shaping through Prism", "Java2D", "Skia", "Platform"] },
      { label: "What you ship", cells: [
        "Your jars plus the goldberry-natives classifier jar; any JDK 25+", { t: "One executable, no JDK, about 41 MiB for the whole showcase", tone: "good" }, "jlink runtime or jpackage with a JDK inside", "jpackage with a JDK inside", "jpackage with a JDK inside", "jpackage with a JDK inside"] },
      { label: "Start-up", cells: [
        { t: "JVM start, then about 330 ms of toolkit work to the first frame", tone: "mid" }, { t: "The toolkit's work only; no JVM to start", tone: "good" }, "JVM start, then the FX runtime", "JVM start, then AWT", "JVM start, then Skiko", "JVM start, then the platform toolkit"] },
      { label: "GraalVM native image", cells: [
        { t: "Optional", tone: "mid" }, { t: "First-class: the binding schema and the weaver are designed for it", tone: "good" }, { t: "Through GluonFX", tone: "mid" }, { t: "Partial AWT support, not on every platform", tone: "mid" }, { t: "Community experiments only", tone: "no" }, { t: "Community experiments only", tone: "no" }] },
      { label: "Native code", cells: [
        { t: "One static library over FFM, no JNI", tone: "good" }, { t: "Same, linked into the executable", tone: "good" }, "Bundled natives over JNI", "Inside the JDK", "Skiko over JNI", "JNI into the platform toolkit"] },
      { label: "Third-party Java dependencies", cells: [
        { t: "None", tone: "good" }, { t: "None", tone: "good" }, "None beyond the FX modules", "None", "Kotlin runtime, Skiko, Compose runtime", "None"] },
      { label: "Platforms", cells: [
        "Linux (Wayland, X11), Windows, macOS", "Same", "Linux, Windows, macOS, mobile through Gluon", "Linux, Windows, macOS", "Linux, Windows, macOS", "Linux, Windows, macOS"] },
      { label: "Headless testing", cells: [
        { t: "Headless backend, golden images per platform, virtual frame clock", tone: "good" }, { t: "Same", tone: "good" }, { t: "Monocle, TestFX", tone: "mid" }, { t: "Headless AWT, no pixel goldens built in", tone: "mid" }, { t: "Compose UI testing", tone: "mid" }, { t: "SWTBot", tone: "mid" }] },
      { label: "Accessibility", cells: [
        { t: "Keyboard, focus ring, WCAG AA contrast and roles; no screen-reader bridge yet", tone: "no" }, { t: "Same", tone: "no" }, { t: "Yes", tone: "good" }, { t: "Yes", tone: "good" }, { t: "Yes, through AWT", tone: "good" }, { t: "Yes, native", tone: "good" }] }
    ],
    note: "This is how we read each project in September 2026. It will drift; corrections are welcome as an issue.",
    noteHref: "https://github.com/DigitalSmile/goldberry/issues"
  },

  // Showcase downloads. While `available` is false every button opens the releases page;
  // set it to true once a release tag with these assets exists.
  downloads: {
    available: false,
    note: "One self-contained GraalVM native binary per platform. No JDK needed.",
    pendingNote: "Binaries are attached to each release tag; the buttons open the releases page.",
    items: [
      { os: "Linux", arch: "x64", file: "goldberry-showcase-native-linux-x64.tar.gz" },
      { os: "macOS", arch: "Apple silicon", file: "goldberry-showcase-native-macos-aarch64.tar.gz" },
      { os: "Windows", arch: "x64", file: "goldberry-showcase-native-windows-x64.exe" }
    ]
  },

  // The closing call to action.
  cta: {
    title: "Open source, Apache 2.0, yours to build on.",
    text: "Add the BOM, open a window, and describe the rest in KDL and CSS. No CLA, no commercial edition, no telemetry."
  },

  builtOn: [
    { name: "Blend2D", role: "2D rasterizer", href: "https://blend2d.com/" },
    { name: "Yoga", role: "flexbox layout", href: "https://www.yogalayout.dev/" },
    { name: "HarfBuzz", role: "text shaping", href: "https://harfbuzz.github.io/" },
    { name: "SDL3", role: "windows and input", href: "https://www.libsdl.org/" },
    { name: "md4c", role: "Markdown parser", href: "https://github.com/mity/md4c" },
    { name: "libwebp", role: "WebP images", href: "https://developers.google.com/speed/webp" },
    { name: "FFmpeg", role: "media decoding", href: "https://ffmpeg.org/" },
    { name: "KDL", role: "markup language", href: "https://kdl.dev/" },
    { name: "Inter", role: "UI typeface", href: "https://rsms.me/inter/" },
    { name: "JetBrains Mono", role: "code typeface", href: "https://www.jetbrains.com/lp/mono/" },
    { name: "Lucide", role: "icons", href: "https://lucide.dev/" },
    { name: "Noto Color Emoji", role: "emoji face", href: "https://fonts.google.com/noto/specimen/Noto+Color+Emoji" }
  ],

  footer: {
    license: "Apache License 2.0",
    licenseHref: "https://github.com/DigitalSmile/goldberry/blob/master/LICENSE",
    note: "Open source.",
    columns: [
      { title: "Docs", links: [["Introduction", "docs/"], ["Getting started", "docs/getting-started/requirements.html"], ["Components", "docs/components/index.html"], ["Status", "docs/status.html"], ["Decision log", "https://github.com/DigitalSmile/goldberry/tree/master/book/src/adr"], ["Native image", "docs/native.html"]] },
      { title: "Project", links: [["GitHub", "https://github.com/DigitalSmile/goldberry"], ["Issues", "https://github.com/DigitalSmile/goldberry/issues"], ["Releases", "https://github.com/DigitalSmile/goldberry/releases"], ["Licence", "https://github.com/DigitalSmile/goldberry/blob/master/LICENSE"]] },
      { title: "Page", links: [["Features", "#features"], ["Quick start", "#quickstart"], ["Performance", "#performance"], ["Compare", "#compare"], ["FAQ", "#faq"], ["llms.txt", "llms.txt"]] }
    ]
  }
};
