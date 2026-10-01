(function () {
  "use strict";
  var S = window.SITE || {};
  var R = window.GoldberryRender;
  var $ = function (id) { return document.getElementById(id); };

  /* ---- content (CI has already written the same HTML into the page; this
          keeps a local preview, which has no build step, identical) ---- */
  var text = R.text(S, window.GOLDBERRY_VERSION);
  document.querySelectorAll("[data-text]").forEach(function (n) {
    var v = text[n.getAttribute("data-text")];
    if (v) n.textContent = v; else n.hidden = true;
  });
  document.querySelectorAll("[data-slot]").forEach(function (n) {
    var fn = R[n.getAttribute("data-slot")];
    if (fn) n.innerHTML = fn(S, window.GOLDBERRY_VERSION, window.GOLDBERRY_STARS);
  });
  document.querySelectorAll("[data-link]").forEach(function (a) {
    var href = S.links && S.links[a.getAttribute("data-link")];
    if (href) a.href = href;
  });

  /* ---- theme ---- */
  $("theme").addEventListener("click", function () {
    var root = document.documentElement;
    var dark = root.getAttribute("data-theme")
      ? root.getAttribute("data-theme") === "dark"
      : matchMedia("(prefers-color-scheme: dark)").matches;
    var next = dark ? "light" : "dark";
    root.setAttribute("data-theme", next);
    try { localStorage.setItem("gb-theme", next); } catch (e) {}
  });

  /* ---- top bar ---- */
  var bar = document.querySelector(".bar");
  var onScroll = function () {
    bar.classList.toggle("scrolled", scrollY > 8);
    var current = "top";
    document.querySelectorAll("[data-nav]").forEach(function (a) {
      var id = a.getAttribute("data-nav"), sec = id !== "top" && $(id);
      if (sec && sec.getBoundingClientRect().top < innerHeight * .4) current = id;
    });
    document.querySelectorAll("[data-nav]").forEach(function (a) {
      if (a.getAttribute("data-nav") === current) a.setAttribute("aria-current", "true"); else a.removeAttribute("aria-current");
    });
  };
  addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  /* ---- code tabs + a very small highlighter ---- */
  var esc = function (s) { return s.replace(/&/g, "&amp;").replace(/</g, "&lt;"); };
  var rules = {
    java: /("(?:[^"\\]|\\.)*")|\b(var|new|this)\b|\b([A-Z][A-Za-z0-9]+)\b|()()(\/\/.*$)/gm,
    groovy: /('(?:[^'\\]|\\.)*')|\b(implementation|runtimeOnly|platform|repositories|dependencies|mavenCentral)\b|()()(\/\/.*$)/gm,
    xml: /()()()()(&lt;!--[\s\S]*?--&gt;)|(&lt;\/?[a-zA-Z]+&gt;)/g,
    sh: /("(?:[^"\\]|\\.)*")|\b(gradlew)\b|()()(#.*$)/gm,
    kdl: /("(?:[^"\\]|\\.)*")|\b(column|row|text|spacer|button)\b|()([a-z-]+)(?==)/g,
    css: /(var\([^)]*\))|([a-z-]+)(?=\s*:\s)|()()(\/\*[\s\S]*?\*\/)|(^[^\s{\/][^{]*)(?=\s*\{)/gm
  };
  var highlight = function (text, re) {
    return esc(text).replace(re, function (m, s, k, t, p, sel, tag) {
      var cls = s ? "tk-s" : k ? "tk-k" : t ? "tk-t" : p ? "tk-p" : sel ? "tk-c" : tag ? "tk-t" : "";
      return cls ? '<span class="' + cls + '">' + m + "</span>" : m;
    });
  };
  document.querySelectorAll(".code pre").forEach(function (pre) {
    var re = rules[pre.getAttribute("data-lang")];
    if (!re) return;
    var lines = pre.querySelectorAll(".ln");
    if (lines.length) lines.forEach(function (ln) { ln.innerHTML = highlight(ln.textContent, re); });
    else pre.innerHTML = highlight(pre.textContent, re);
  });

  // Every tablist on the page: the code sample and the showcase screens.
  document.querySelectorAll('[role="tablist"]').forEach(function (list) {
    var tabs = Array.prototype.slice.call(list.querySelectorAll('[role="tab"]'));
    var select = function (tab, focus) {
      tabs.forEach(function (t) {
        var on = t === tab;
        t.setAttribute("aria-selected", on);
        t.tabIndex = on ? 0 : -1;
        $(t.getAttribute("aria-controls")).hidden = !on;
      });
      if (focus) tab.focus();
      if (tab.scrollIntoView && list.scrollWidth > list.clientWidth) tab.scrollIntoView({ block: "nearest", inline: "center", behavior: "smooth" });
    };
    tabs.forEach(function (tab, i) {
      tab.addEventListener("click", function () { select(tab); });
      tab.addEventListener("keydown", function (e) {
        var d = /^Arrow(Right|Down)$/.test(e.key) ? 1 : /^Arrow(Left|Up)$/.test(e.key) ? -1 : 0;
        if (d) { e.preventDefault(); select(tabs[(i + d + tabs.length) % tabs.length], true); }
      });
    });
  });

  /* ---- feature stack: the sticky picture follows the active item ---- */
  var items = Array.prototype.slice.call(document.querySelectorAll(".stack-item"));
  var stageImgs = Array.prototype.slice.call(document.querySelectorAll(".stage-imgs img"));
  var activate = function (i) {
    items.forEach(function (it, j) { it.classList.toggle("active", i === j); });
    stageImgs.forEach(function (im, j) { im.classList.toggle("active", i === j); });
  };
  items.forEach(function (it, i) { it.addEventListener("click", function () { activate(i); }); });
  if (items.length && window.IntersectionObserver && !matchMedia("(max-width: 860px)").matches) {
    new IntersectionObserver(function (entries) {
      entries.forEach(function (e) { if (e.isIntersecting) activate(+e.target.getAttribute("data-stack")); });
    }, { rootMargin: "-45% 0px -45% 0px" }).observe && items.forEach(function (it) {
      new IntersectionObserver(function (entries) {
        if (entries[0].isIntersecting) activate(+it.getAttribute("data-stack"));
      }, { rootMargin: "-40% 0px -40% 0px" }).observe(it);
    });
  }

  /* ---- the engine drawing: reduced motion strips the SMIL leaves; off screen pauses it;
          small screens zoom the viewBox onto the core ---- */
  var engine = $("engine");
  if (engine) {
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) {
      engine.querySelectorAll("animateMotion").forEach(function (a) { a.parentNode.remove(); });
    } else if (window.IntersectionObserver) {
      new IntersectionObserver(function (entries) {
        if (entries[0].isIntersecting) { engine.classList.remove("paused"); if (engine.unpauseAnimations) engine.unpauseAnimations(); }
        else { engine.classList.add("paused"); if (engine.pauseAnimations) engine.pauseAnimations(); }
      }).observe(engine);
    }
    var zoom = function () { engine.setAttribute("viewBox", matchMedia("(max-width: 700px)").matches ? "380 110 440 400" : "0 0 1200 560"); };
    zoom(); matchMedia("(max-width: 700px)").addEventListener("change", zoom);
  }

  /* ---- the illustration behind the hero, moving slower than the page ---- */
  var bg = $("hero-bg");
  if (bg && !matchMedia("(prefers-reduced-motion: reduce)").matches) {
    var bgImg = bg.querySelector("img"), ticking = false;
    var parallax = function () {
      ticking = false;
      var y = Math.min(scrollY, 1200);
      bgImg.style.transform = "translate3d(0," + (y * .35).toFixed(1) + "px,0)";
    };
    addEventListener("scroll", function () { if (!ticking) { ticking = true; requestAnimationFrame(parallax); } }, { passive: true });
    parallax();
  }

  /* ---- the looking glass over the showcase: fixed insets, and one that follows the pointer ---- */
  var stage = $("show-stage");
  if (stage) {
    var setWidth = function () { stage.style.setProperty("--fw", stage.clientWidth + "px"); };
    setWidth();
    if (window.ResizeObserver) new ResizeObserver(setWidth).observe(stage);
    if (matchMedia("(hover: hover)").matches) {
      stage.addEventListener("mousemove", function (e) {
        var fig = e.target.closest("figure"); if (!fig) return;
        var r = fig.querySelector("img").getBoundingClientRect(), free = fig.querySelector(".loupe.free");
        var fx = (e.clientX - r.left) / r.width, fy = (e.clientY - r.top) / r.height;
        if (fx < 0 || fx > 1 || fy < 0 || fy > 1) return;
        fig.classList.add("hovering"); free.hidden = false;
        free.style.setProperty("--fx", fx.toFixed(4)); free.style.setProperty("--fy", fy.toFixed(4));
      });
      stage.addEventListener("mouseleave", function () {
        stage.querySelectorAll("figure").forEach(function (f) { f.classList.remove("hovering"); var fr = f.querySelector(".loupe.free"); if (fr) fr.hidden = true; });
      });
    }
  }

  /* ---- GitHub stars: the build stamps a count; the browser refreshes it once an hour ---- */
  var ghIcon = document.querySelector('[data-icon="github"]');
  if (ghIcon && window.GB_ICONS && window.GB_ICONS.github) ghIcon.innerHTML = window.GB_ICONS.github;
  var showStars = function (n) {
    if (!n) return;
    var txt = n >= 1000 ? (n / 1000).toFixed(n >= 10000 ? 0 : 1).replace(/\.0$/, "") + "k" : String(n);
    document.querySelectorAll("[data-stars]").forEach(function (el) {
      el.innerHTML = '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (window.GB_ICONS.star || "") + "</svg>" + txt;
      el.hidden = false;
    });
  };
  showStars(window.GOLDBERRY_STARS);
  try {
    var cached = JSON.parse(localStorage.getItem("gb-stars") || "null");
    if (cached && Date.now() - cached.at < 36e5) showStars(cached.n);
    else if (window.fetch && S.links && S.links.github) {
      var repo = S.links.github.replace(/^https:\/\/github\.com\//, "").replace(/\/$/, "");
      fetch("https://api.github.com/repos/" + repo, { headers: { Accept: "application/vnd.github+json" } })
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (j) { if (j && typeof j.stargazers_count === "number") { showStars(j.stargazers_count); try { localStorage.setItem("gb-stars", JSON.stringify({ n: j.stargazers_count, at: Date.now() })); } catch (e) {} } })
        .catch(function () {});
    }
  } catch (e) {}
})();
