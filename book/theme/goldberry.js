/*
 * The landing page's header, built into mdBook's menu bar once the page has
 * loaded (docs/book.md). mdBook's own buttons stay: the sidebar toggle, the
 * theme menu and search on the left, print and the GitHub links on the right.
 * What this adds between them is the brand and the six parts of the guide,
 * which is the navigation the landing page has for its own sections.
 *
 * `path_to_root` is the global mdBook writes into every page, so a link works
 * from any depth and under any site-url. The brand goes one level above the
 * book, which is the landing page at goldberry.dev and the parent directory
 * under `mdbook serve`.
 *
 * The second half turns every `<div class="gb-tabs">` of fenced samples into
 * tabs, one per language, and remembers the reader's choice for the whole book
 * (ADR-0514). The pictures need no script: `goldberry.css` shows the light or
 * the dark one by the theme's class on <html> (ADR-0513).
 */
(function () {
    var bar = document.getElementById("mdbook-menu-bar");
    var title = bar && bar.querySelector(".menu-title");
    if (!bar || !title) {
        return;
    }
    var root = typeof path_to_root === "string" ? path_to_root : "";

    var parts = [
        ["Overview", "overview/concept.html", "overview/"],
        ["Getting started", "getting-started/requirements.html", "getting-started/"],
        ["Layout", "layout/index.html", "layout/"],
        ["Components", "components/index.html", "components/"],
        ["Performance", "performance/index.html", "performance/"],
        ["Guide", "applications.html", "guide/"]
    ];
    // Which part the current page belongs to, by its path under the root.
    var here = location.pathname;
    var developerGuide = /\/(applications|weaving|native)\.html$|\/(guide|contributing)\//;

    var brand = document.createElement("a");
    brand.className = "gb-brand";
    brand.href = root + "../";
    brand.setAttribute("aria-label", "goldberry.dev");
    brand.innerHTML =
        '<svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">' +
        '<path d="M2.5 21.5C3.6 12.6 10.4 4.9 21.5 2.5C19.9 13 12.9 20.1 2.5 21.5Z" fill="currentColor"/>' +
        '<path d="M3.2 20.8C7.5 15.8 13.4 9.7 20.6 3.4" stroke="var(--bg)" stroke-width="1.1" stroke-linecap="round" fill="none" opacity=".6"/>' +
        "</svg><span>Goldberry</span><em>docs</em>";

    var nav = document.createElement("nav");
    nav.className = "gb-nav";
    nav.setAttribute("aria-label", "Parts of the guide");
    parts.forEach(function (part) {
        var link = document.createElement("a");
        link.textContent = part[0];
        link.href = root + part[1];
        var current = part[2] === "guide/" ? developerGuide.test(here) : here.indexOf("/" + part[2]) >= 0;
        if (current) {
            link.setAttribute("aria-current", "true");
        }
        nav.appendChild(link);
    });

    var github = document.createElement("a");
    github.className = "gb-github";
    github.href = "https://github.com/DigitalSmile/goldberry";
    github.rel = "noopener";
    github.textContent = "GitHub";

    title.replaceWith(brand, nav, github);
    bar.classList.add("gb-bar");
})();

(function () {
    var LABELS = {
        kdl: "KDL",
        java: "Java",
        css: "CSS",
        groovy: "Gradle",
        kotlin: "Kotlin",
        xml: "XML",
        bash: "Shell",
        sh: "Shell",
        json: "JSON",
        toml: "TOML",
        html: "HTML",
        text: "Text"
    };
    var KEY = "gb-tab";
    var groups = Array.prototype.slice.call(document.querySelectorAll(".gb-tabs"));
    if (!groups.length) {
        return;
    }

    function languageOf(pane) {
        var code = pane.querySelector("code");
        var match = code && /language-([a-z0-9]+)/.exec(code.className);
        return match ? match[1] : "text";
    }
    function remembered() {
        try {
            return localStorage.getItem(KEY);
        } catch (e) {
            return null;
        }
    }
    function remember(language) {
        try {
            localStorage.setItem(KEY, language);
        } catch (e) {
            // A reader with storage turned off still gets tabs, just not a memory.
        }
    }

    var all = [];
    groups.forEach(function (group) {
        var panes = Array.prototype.filter.call(group.children, function (child) {
            return child.tagName === "PRE";
        });
        if (panes.length < 2) {
            return;
        }
        var list = document.createElement("div");
        list.className = "gb-tablist";
        list.setAttribute("role", "tablist");
        var tabs = panes.map(function (pane, index) {
            var language = languageOf(pane);
            var tab = document.createElement("button");
            tab.type = "button";
            tab.setAttribute("role", "tab");
            tab.setAttribute("aria-selected", "false");
            tab.textContent = LABELS[language] || language.toUpperCase();
            tab.dataset.language = language;
            tab.addEventListener("click", function () {
                remember(language);
                // Every group on the page follows, so a reader who prefers Java
                // reads Java all the way down.
                all.forEach(function (other) {
                    other.select(language, false);
                });
            });
            tab.addEventListener("keydown", function (event) {
                var step = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
                if (!step) {
                    return;
                }
                event.preventDefault();
                var next = tabs[(index + step + tabs.length) % tabs.length];
                next.focus();
                next.click();
            });
            list.appendChild(tab);
            return tab;
        });

        var entry = {
            select: function (language, fallback) {
                var at = -1;
                tabs.forEach(function (tab, index) {
                    if (tab.dataset.language === language) {
                        at = index;
                    }
                });
                if (at < 0) {
                    if (!fallback) {
                        return;
                    }
                    at = 0;
                }
                tabs.forEach(function (tab, index) {
                    var on = index === at;
                    tab.setAttribute("aria-selected", on ? "true" : "false");
                    tab.tabIndex = on ? 0 : -1;
                    panes[index].classList.toggle("gb-active", on);
                });
            }
        };
        group.insertBefore(list, group.firstChild);
        group.classList.add("gb-ready");
        all.push(entry);
        entry.select(remembered() || languageOf(panes[0]), true);
    });
})();
