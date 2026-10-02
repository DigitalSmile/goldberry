/*
 * Consent for counting visits, shared by the landing page and the book.
 *
 * Nothing is counted and no cookie is set until the visitor accepts. Until a
 * choice is made a banner asks; the choice is kept in localStorage under
 * `gb-consent`, which is the same store for `/` and `/docs/`, so one answer
 * covers the whole site. Accepting loads Yandex Metrika; declining, or taking
 * the consent back later on privacy.html, deletes Metrika's `_ym*` cookies
 * and storage and reloads, so the counter that was running stops.
 *
 * `VERSION` is part of the stored choice: raise it when what is counted
 * changes, and everybody is asked again.
 *
 * The page talks to it through `window.GoldberryConsent`:
 * `choice()` is "granted", "denied" or null, `grant()` and `deny()` record one,
 * and `open()` shows the banner again.
 */
(function (w, d) {
  "use strict";
  var KEY = "gb-consent";
  var VERSION = 1;
  var COUNTER = 113266145;
  var PRIVACY = "/privacy.html";
  var BANNER = "gb-consent";

  var loaded = false;
  var banner = null;

  function choice() {
    try {
      var stored = JSON.parse(w.localStorage.getItem(KEY) || "null");
      if (stored && stored.version === VERSION && (stored.choice === "granted" || stored.choice === "denied")) {
        return stored.choice;
      }
    } catch (e) {}
    return null;
  }

  function remember(value) {
    try {
      w.localStorage.setItem(KEY, JSON.stringify({
        choice: value, version: VERSION, date: new Date().toISOString().slice(0, 10)
      }));
    } catch (e) {}
  }

  /* Yandex Metrika's snippet, as Yandex gives it, run only once consent is given. */
  function metrika() {
    if (loaded) {
      return;
    }
    loaded = true;
    (function(m,e,t,r,i,k,a){
        m[i]=m[i]||function(){(m[i].a=m[i].a||[]).push(arguments)};
        m[i].l=1*new Date();
        for (var j = 0; j < document.scripts.length; j++) {if (document.scripts[j].src === r) { return; }}
        k=e.createElement(t),a=e.getElementsByTagName(t)[0],k.async=1,k.src=r,a.parentNode.insertBefore(k,a)
    })(window, document,'script','https://mc.yandex.ru/metrika/tag.js?id=' + COUNTER, 'ym');

    w.ym(COUNTER, 'init', {ssr:true, webvisor:true, clickmap:true, ecommerce:"dataLayer", referrer: document.referrer, url: location.href, accurateTrackBounce:true, trackLinks:true});
  }

  /* Deletes what Metrika keeps on this site: its `_ym` cookies, on this host and
     the domain above it, and its `_ym` keys in localStorage. */
  function forget() {
    var host = w.location.hostname;
    var domains = ["", host, "." + host];
    var parent = host.split(".").slice(-2).join(".");
    if (parent !== host) {
      domains.push("." + parent);
    }
    (d.cookie || "").split(";").forEach(function (cookie) {
      var name = cookie.split("=")[0].trim();
      if (name.indexOf("_ym") === 0) {
        domains.forEach(function (domain) {
          d.cookie = name + "=; Max-Age=0; path=/" + (domain ? "; domain=" + domain : "");
        });
      }
    });
    try {
      var keys = [];
      for (var i = 0; i < w.localStorage.length; i++) {
        keys.push(w.localStorage.key(i));
      }
      keys.filter(function (key) { return key && key.indexOf("_ym") === 0; })
        .forEach(function (key) { w.localStorage.removeItem(key); });
    } catch (e) {}
  }

  function choose(value) {
    var wasCounting = loaded;
    remember(value);
    close();
    if (value === "granted") {
      metrika();
    } else {
      forget();
      if (wasCounting) {
        w.location.reload();
      }
    }
    d.dispatchEvent(new w.CustomEvent("gb-consent", { detail: value }));
  }

  var STYLE =
    "#gb-consent{position:fixed;z-index:2147483000;left:16px;right:16px;bottom:16px;max-width:42rem;margin:0 auto;" +
    "padding:16px 18px;border-radius:14px;border:1px solid #4C566A;background:#2E3440;color:#ECEFF4;" +
    "font:400 15px/1.5 system-ui,-apple-system,'Segoe UI',sans-serif;box-shadow:0 12px 40px rgba(0,0,0,.35)}" +
    "#gb-consent p{margin:0 0 12px}#gb-consent a{color:#88C0D0}" +
    "#gb-consent .gb-consent-actions{display:flex;gap:10px;justify-content:flex-end;flex-wrap:wrap}" +
    "#gb-consent button{font:inherit;font-size:14px;font-weight:600;line-height:1;padding:10px 18px;border-radius:999px;cursor:pointer;" +
    "border:1px solid #4C566A;background:transparent;color:#ECEFF4}" +
    "#gb-consent button.gb-consent-accept{background:#88C0D0;border-color:#88C0D0;color:#2E3440}" +
    "#gb-consent button:focus-visible{outline:2px solid #EBCB8B;outline-offset:2px}";

  function button(label, className, value) {
    var b = d.createElement("button");
    b.type = "button";
    b.className = className;
    b.textContent = label;
    b.addEventListener("click", function () { choose(value); });
    return b;
  }

  function open() {
    if (banner || !d.body) {
      return;
    }
    if (!d.getElementById(BANNER + "-style")) {
      var style = d.createElement("style");
      style.id = BANNER + "-style";
      style.textContent = STYLE;
      d.head.appendChild(style);
    }
    banner = d.createElement("section");
    banner.id = BANNER;
    banner.setAttribute("role", "dialog");
    banner.setAttribute("aria-modal", "false");
    banner.setAttribute("aria-label", "Cookie consent");

    var text = d.createElement("p");
    text.appendChild(d.createTextNode(
      "May goldberry.dev count your visit? With your consent, Yandex Metrika sets cookies and records " +
      "how pages are used, including a replay of the session, so we can see what readers look for. " +
      "Nothing is counted if you decline. "));
    var more = d.createElement("a");
    more.href = PRIVACY;
    more.textContent = "Privacy policy";
    text.appendChild(more);

    var actions = d.createElement("div");
    actions.className = "gb-consent-actions";
    actions.appendChild(button("Decline", "gb-consent-decline", "denied"));
    actions.appendChild(button("Accept", "gb-consent-accept", "granted"));

    banner.appendChild(text);
    banner.appendChild(actions);
    d.body.appendChild(banner);
  }

  function close() {
    if (banner) {
      banner.remove();
      banner = null;
    }
  }

  w.GoldberryConsent = {
    choice: choice,
    grant: function () { choose("granted"); },
    deny: function () { choose("denied"); },
    open: open
  };

  var current = choice();
  if (current === "granted") {
    metrika();
  } else if (current === null) {
    if (d.readyState === "loading") {
      d.addEventListener("DOMContentLoaded", open);
    } else {
      open();
    }
  }
})(window, document);
