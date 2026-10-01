/*
 * Turns content.js (and news.js) into HTML. Runs twice: in the browser
 * (site.js), and in CI (build.mjs), which writes the result into index.html
 * so crawlers and link previews see real content without JavaScript.
 */
(function (root) {
  "use strict";
  var esc = function (s) {
    return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  };
  var external = function (href) { return /^https?:/.test(href) ? ' rel="noopener"' : ""; };
  var icon = function (name, size) {
    var body = (root.GB_ICONS || {})[name];
    return body ? '<svg viewBox="0 0 24 24" width="' + (size || 22) + '" height="' + (size || 22) + '" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + body + "</svg>" : "";
  };
  var MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
  var longDate = function (iso) { var p = iso.split("-"); return MONTHS[+p[1] - 1] + " " + +p[2] + ", " + p[0]; };
  var news = function (S) { return root.NEWS || S.news || { items: [] }; };
  var newest = function (S) {
    return (news(S).items || []).slice().sort(function (a, b) { return a.date < b.date ? 1 : a.date > b.date ? -1 : 0; });
  };
  var fmtStars = function (n) { return n >= 1000 ? (n / 1000).toFixed(n >= 10000 ? 0 : 1).replace(/\.0$/, "") + "k" : String(n); };

  var R = root.GoldberryRender = {
    text: function (S, version) {
      return { tagline: S.tagline, lede: S.lede, versionNote: S.versionNote, version: version || S.version,
        dsTitle: (S.designSystem || {}).title, dsText: (S.designSystem || {}).text, perfStartText: ((S.perf || {}).start || {}).text, perfStartNote: ((S.perf || {}).start || {}).note };
    },
    heroActions: function (S, version, stars) {
      return ((S.links && S.links.hero) || []).map(function (b) {
        var inner = esc(b.label);
        if (b.github) inner = icon("github", 18) + "<span>" + esc(b.label) + "</span>" + (stars ? '<em class="stars" data-stars>' + icon("star", 13) + fmtStars(stars) + "</em>" : '<em class="stars" data-stars hidden></em>');
        return '<a class="btn' + (b.primary ? " primary" : "") + (b.github ? " gh" : "") + '" href="' + esc(b.href) + '"' + external(b.href) + ">" + inner + "</a>";
      }).join("");
    },
    announce: function (S) {
      var n = newest(S)[0];
      return n ? '<a class="announce" href="' + esc(n.href || "#news") + '">' + esc(n.pill || n.title) + " <b>→</b></a>" : "";
    },
    stats: function (S) {
      return (S.stats || []).map(function (st) {
        return "<li><strong>" + esc(st.value) + (st.unit ? " <small>" + esc(st.unit) + "</small>" : "") + "</strong><span>" + esc(st.text) + "</span></li>";
      }).join("");
    },
    builtOn: function (S) {
      var items = (S.builtOn || []).map(function (b) {
        return '<a href="' + esc(b.href) + '" rel="noopener"><strong>' + esc(b.name) + "</strong><span>" + esc(b.role) + "</span></a>";
      }).join("");
      return '<div class="marquee"><div class="track">' + items + items + "</div></div>";
    },
    shotTabs: function (S) {
      return (S.shots || []).map(function (sh, i) {
        return '<button role="tab" id="shot-tab-' + i + '" aria-controls="shot-' + i + '" aria-selected="' + (i === 0) + '"' + (i ? ' tabindex="-1"' : "") + "><strong>" + esc(sh.title) + "</strong><span>" + esc(sh.text) + "</span></button>";
      }).join("");
    },
    shotStage: function (S) {
      return (S.shots || []).map(function (sh, i) {
        var loupes = (sh.zooms || []).map(function (z, j) {
          return '<span class="loupe" style="--fx:' + (z[0] / 1200).toFixed(4) + ";--fy:" + (z[1] / 900).toFixed(4) + ';background-image:url(assets/shots/' + esc(sh.file) + ')" data-n="' + j + '"></span>';
        }).join("");
        return '<figure role="tabpanel" id="shot-' + i + '" aria-labelledby="shot-tab-' + i + '"' + (i ? " hidden" : "") + ' data-src="assets/shots/' + esc(sh.file) + '">' +
          '<img src="assets/shots/' + esc(sh.file) + '" width="1200" height="900" loading="' + (i ? "lazy" : "eager") + '" decoding="async" alt="Goldberry showcase: ' + esc(sh.title) + ". " + esc(sh.text) + '">' +
          loupes + '<span class="loupe free" style="background-image:url(assets/shots/' + esc(sh.file) + ')" hidden></span>' +
          "<figcaption>" + esc(sh.title) + ". " + esc(sh.text) + "</figcaption></figure>";
      }).join("");
    },
    highlightItems: function (S) {
      return (S.highlights || []).map(function (h, i) {
        var points = (h.points || []).map(function (p) { return "<li>" + esc(p) + "</li>"; }).join("");
        return '<article class="stack-item' + (i ? "" : " active") + '" data-stack="' + i + '"><p class="eyebrow">' + esc(h.eyebrow) + "</p><h3>" + esc(h.title) + "</h3><p>" + esc(h.text) + "</p>" +
          (points ? "<ul>" + points + "</ul>" : "") + (h.href ? '<a class="more" href="' + esc(h.href) + '">' + esc(h.hrefLabel || "Learn more") + " →</a>" : "") +
          '<figure class="stack-shot-inline"><img src="assets/shots/' + esc(h.shot) + '" width="1200" height="900" loading="lazy" decoding="async" alt="' + esc(h.shotAlt || h.title) + '"></figure></article>';
      }).join("");
    },
    highlightStage: function (S) {
      return (S.highlights || []).map(function (h, i) {
        return '<img class="' + (i ? "" : "active") + '" data-stack="' + i + '" src="assets/shots/' + esc(h.shot) + '" width="1200" height="900" loading="lazy" decoding="async" alt="">';
      }).join("");
    },
    features: function (S) {
      return (S.features || []).map(function (f) {
        var points = (f.points || []).map(function (p) { return "<li>" + esc(p) + "</li>"; }).join("");
        return '<article class="card"><div class="card-icon">' + icon(f.icon) + "</div><h3>" + esc(f.title) + "</h3><p>" + esc(f.text) + "</p>" + (points ? "<ul>" + points + "</ul>" : "") + "</article>";
      }).join("");
    },
    perfStart: function (S) {
      return ((((S.perf || {}).start) || {}).items || []).map(function (it, i) {
        return '<li class="' + (i ? "ours" : "jvm") + '"><strong>' + esc(it.value) + " <small>" + esc(it.unit) + "</small></strong><b>" + esc(it.label) + "</b><span>" + esc(it.note) + "</span></li>";
      }).join("");
    },
    perfRun: function (S) {
      return ((((S.perf || {}).run) || {}).items || []).map(function (it) {
        return "<li><strong>" + esc(it.value) + " <small>" + esc(it.unit) + "</small></strong><b>" + esc(it.label) + "</b><span>" + esc(it.note) + "</span></li>";
      }).join("");
    },
    perfCaveat: function (S) {
      var pf = S.perf || {};
      return esc(pf.caveat || "") + (pf.caveatHref ? ' <a href="' + esc(pf.caveatHref) + '">Details</a>' : "");
    },
    // A tabbed code block with line numbers. {{version}} in a sample becomes the real version.
    codeBlock: function (tabs, id, label, version) {
      var heads = tabs.map(function (t, i) {
        return '<button role="tab" id="' + id + '-tab-' + i + '" aria-controls="' + id + '-' + i + '" aria-selected="' + (i === 0) + '"' + (i ? ' tabindex="-1"' : "") + ">" + esc(t.label) + "</button>";
      }).join("");
      var panes = tabs.map(function (t, i) {
        var lines = t.code.replace(/\{\{version\}\}/g, version || "").split("\n").map(function (l) { return '<span class="ln">' + esc(l) + "</span>"; }).join("");
        return '<pre role="tabpanel" id="' + id + "-" + i + '" aria-labelledby="' + id + '-tab-' + i + '" tabindex="0" data-lang="' + esc(t.lang) + '"' + (i ? " hidden" : "") + ">" + lines + "</pre>";
      }).join("");
      return '<div class="code"><div class="tabs" role="tablist" aria-label="' + esc(label) + '">' + heads + '</div><div class="panes">' + panes + "</div></div>";
    },
    quickDeps: function (S, v) { return R.codeBlock(((S.quickstart || {}).tabs || []).filter(function (t) { return t.group === "deps"; }), "deps", "Build files", v || S.version); },
    quickJvm: function (S, v) { return R.codeBlock(((S.quickstart || {}).tabs || []).filter(function (t) { return t.group === "jvm"; }), "jvm", "Plain Java", v || S.version); },
    quickNative: function (S, v) { return R.codeBlock(((S.quickstart || {}).tabs || []).filter(function (t) { return t.group === "native"; }), "native", "GraalVM native image", v || S.version); },
    cssSamples: function (S) { return R.codeBlock(S.cssSamples || [], "css", "Styling", S.version); },
    markupNames: function (S) {
      var names = S.markupNames || [], rows = [[], [], []];
      names.forEach(function (n, i) { rows[i % 3].push(n); });
      return rows.map(function (r, i) {
        var chips = r.map(function (n) { return "<span>" + esc(n) + "</span>"; }).join("");
        return '<div class="marquee' + (i % 2 ? " rev" : "") + '"><div class="track">' + chips + chips + "</div></div>";
      }).join("");
    },
    markupCount: function (S) { return String((S.markupNames || []).length); },
    designSystem: function (S) {
      var d = S.designSystem || {};
      var sw = (d.swatches || []).map(function (c) { return '<i style="background:' + esc(c) + '" title="' + esc(c) + '"></i>'; }).join("");
      var sp = (d.spacing || []).map(function (n) { return '<i style="width:' + n + 'px;height:' + n + 'px"></i>'; }).join("");
      var facts = (d.facts || []).map(function (f) { return "<div><dt>" + esc(f[0]) + "</dt><dd>" + esc(f[1]) + "</dd></div>"; }).join("");
      return '<div class="ds-swatches">' + sw + sw + "</div>" +
        '<div class="ds-row"><div class="ds-type"><b style="font-weight:400">Aa</b><b style="font-weight:500">Aa</b><b style="font-weight:600">Aa</b><span>Inter · 400 500 600</span></div>' +
        '<div class="ds-type mono"><b>{ }</b><span>JetBrains Mono</span></div></div>' +
        '<div class="ds-row"><div class="ds-space">' + sp + '<span>4 px ramp</span></div>' +
        '<div class="ds-density"><i style="height:32px">Regular · 32</i><i style="height:28px">Compact · 28</i></div></div>' +
        '<dl class="ds-facts">' + facts + "</dl>" +
        (d.href ? '<a class="more" href="' + esc(d.href) + '">' + esc(d.hrefLabel || "Learn more") + " →</a>" : "");
    },
    compare: function (S) {
      var c = S.compare || {}, us = c.us || 1;
      var cell = function (v, i) {
        var o = typeof v === "string" ? { t: v } : v;
        return "<td" + (i < us ? ' class="us"' : "") + ">" + (o.tone ? '<i class="tone ' + esc(o.tone) + '"></i>' : "") + esc(o.t) + "</td>";
      };
      var head = '<tr><th scope="col"></th>' + (c.columns || []).map(function (h, i) { return '<th scope="col"' + (i < us ? ' class="us"' : "") + ">" + esc(h) + "</th>"; }).join("") + "</tr>";
      var body = (c.rows || []).map(function (r) { return '<tr><th scope="row">' + esc(r.label) + "</th>" + (r.cells || []).map(cell).join("") + "</tr>"; }).join("");
      return "<table><thead>" + head + "</thead><tbody>" + body + "</tbody></table>";
    },
    compareNote: function (S) {
      var c = S.compare || {};
      return c.noteHref ? '<a href="' + esc(c.noteHref) + '" rel="noopener">' + esc(c.note || "") + "</a>" : esc(c.note || "");
    },
    faq: function (S) {
      return (((S.seo || {}).faq) || []).map(function (f) {
        return "<details><summary>" + esc(f.q) + "</summary><p>" + esc(f.a) + "</p></details>";
      }).join("");
    },
    news: function (S) {
      var items = newest(S).slice(0, news(S).limit || 4);
      if (!items.length) return '<li class="news-empty">No news yet. Watch the repository on GitHub for updates.</li>';
      return items.map(function (n) {
        var title = n.href ? '<a href="' + esc(n.href) + '"' + external(n.href) + ">" + esc(n.title) + "</a>" : esc(n.title);
        return '<li><time datetime="' + esc(n.date) + '">' + longDate(n.date) + "</time><div><h3>" + title + "</h3>" + (n.text ? "<p>" + esc(n.text) + "</p>" : "") + "</div></li>";
      }).join("");
    },
    downloads: function (S) {
      var d = S.downloads || {}, gh = (S.links && S.links.github) || "";
      var buttons = (d.items || []).map(function (it) {
        var href = d.available ? gh + "/releases/latest/download/" + it.file : gh + "/releases";
        return '<a class="dl" href="' + esc(href) + '" rel="noopener"' + (d.available ? " download" : "") + ">" + icon("download", 20) + "<span><strong>" + esc(it.os) + "</strong><small>" + esc(it.arch) + "</small></span></a>";
      }).join("");
      return '<div class="dl-row">' + buttons + "</div><p>" + esc(d.note || "") + (d.available ? "" : " " + esc(d.pendingNote || "")) + "</p>";
    },
    cta: function (S) {
      var c = S.cta || {};
      return "<h2>" + esc(c.title || "") + "</h2><p>" + esc(c.text || "") + "</p>";
    },
    footer: function (S) {
      var f = S.footer || {};
      return (f.note ? esc(f.note) + " " : "") + 'Licensed under the <a href="' + esc(f.licenseHref || "#") + '" rel="noopener">' + esc(f.license || "") + "</a>.";
    },
    footerColumns: function (S) {
      return (((S.footer || {}).columns) || []).map(function (col) {
        return "<div><h4>" + esc(col.title) + "</h4><ul>" + (col.links || []).map(function (l) {
          return '<li><a href="' + esc(l[1]) + '"' + external(l[1]) + ">" + esc(l[0]) + "</a></li>";
        }).join("") + "</ul></div>";
      }).join("");
    },
    jsonLd: function (S, version) {
      var seo = S.seo || {};
      return JSON.stringify({
        "@context": "https://schema.org",
        "@graph": [
          { "@type": "WebSite", "@id": seo.url + "#site", url: seo.url, name: "Goldberry", description: S.tagline, inLanguage: "en" },
          {
            "@type": ["SoftwareApplication", "SoftwareSourceCode"], "@id": seo.url + "#software",
            name: "Goldberry", description: seo.description, url: seo.url,
            codeRepository: S.links && S.links.github, programmingLanguage: "Java",
            runtimePlatform: "Java 25+", softwareVersion: version || S.version, version: version || S.version,
            license: "https://www.apache.org/licenses/LICENSE-2.0", isAccessibleForFree: true,
            operatingSystem: "Linux, Windows, macOS", applicationCategory: "DeveloperApplication",
            offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
            keywords: (seo.keywords || []).join(", "), image: seo.url + "assets/og-card.jpg",
            softwareHelp: { "@type": "CreativeWork", url: seo.url + "docs/" }
          },
          {
            "@type": "FAQPage", "@id": seo.url + "#faq",
            mainEntity: (seo.faq || []).map(function (f) { return { "@type": "Question", name: f.q, acceptedAnswer: { "@type": "Answer", text: f.a } }; })
          }
        ]
      });
    },
    // llms.txt: the page as agents read it.
    llms: function (S, version) {
      var seo = S.seo || {}, v = version || S.version;
      var lines = ["# Goldberry", "", "> " + seo.description, "",
        "- Version: " + v + " (calendar versions, YEAR.RELEASE)", "- Licence: Apache License 2.0", "- Source: " + (S.links || {}).github,
        "- Documentation: " + seo.url + "docs/", "- Status (what works today): " + seo.url + "docs/status.html", "- Decision log (ADRs): " + seo.url + "docs/adr/",
        "- Java: 25 or newer; optional GraalVM native image", "- Platforms: Linux (Wayland, X11), Windows, macOS; headless backend for tests",
        "- Maven coordinates: io.github.digitalsmile:goldberry-bom:" + v + " (BOM), io.github.digitalsmile:goldberry, io.github.digitalsmile:goldberry-natives (classifiers linux-x64, linux-aarch64, macos-aarch64, windows-x64)", "",
        "## Facts", ""];
      (S.highlights || []).forEach(function (h) { lines.push("- " + h.title + " " + h.text); });
      (S.features || []).forEach(function (f) { lines.push("- " + f.title + ": " + f.text + " " + (f.points || []).join("; ") + "."); });
      lines.push("", "## Performance", "");
      ((S.perf || {}).start || {}).items && lines.push("- Start-up on a plain JDK (README trace): " + S.perf.start.items.map(function (i) { return i.value + " " + i.unit + " " + i.label; }).join(", ") + ". " + (S.perf.start.note || ""));
      ((S.perf || {}).run || {}).items && S.perf.run.items.forEach(function (i) { lines.push("- " + i.value + " " + i.unit + " " + i.label + ": " + i.note); });
      lines.push("- " + ((S.perf || {}).caveat || ""), "", "## Built on", "");
      (S.builtOn || []).forEach(function (b) { lines.push("- " + b.name + " (" + b.role + "): " + b.href); });
      lines.push("", "## FAQ", "");
      (seo.faq || []).forEach(function (f) { lines.push("### " + f.q, "", f.a, ""); });
      lines.push("## Recent news", "");
      newest(S).slice(0, 6).forEach(function (n) { lines.push("- " + n.date + ": " + n.title + (n.text ? " " + n.text : "") + (n.href ? " (" + (/^https?:/.test(n.href) ? n.href : seo.url + n.href.replace(/^#/, "")) + ")" : "")); });
      return lines.join("\n") + "\n";
    }
  };
})(typeof window !== "undefined" ? window : globalThis);
