// assets/consent.js against a small fake of the browser: what it loads, what
// it stores and what it deletes, for each answer. Node's own runner, no
// dependencies:
//
//     node --test "site/test/*.test.mjs"
//
// pages.yml runs it before it builds the site.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const SOURCE = readFileSync(new URL("../assets/consent.js", import.meta.url), "utf8");
const TAG = "https://mc.yandex.ru/metrika/tag.js?id=113266145";

class FakeElement {
  constructor(tag, document) {
    this.tagName = tag.toUpperCase();
    this.document = document;
    this.children = [];
    this.attributes = {};
    this.listeners = {};
    this.parentNode = null;
    this.textContent = "";
  }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  appendChild(child) { child.parentNode = this; this.children.push(child); return child; }
  insertBefore(child, before) {
    child.parentNode = this;
    const at = this.children.indexOf(before);
    this.children.splice(at < 0 ? this.children.length : at, 0, child);
    return child;
  }
  remove() {
    if (this.parentNode) {
      this.parentNode.children = this.parentNode.children.filter((c) => c !== this);
      this.parentNode = null;
    }
  }
  addEventListener(type, fn) { (this.listeners[type] ||= []).push(fn); }
  click() { (this.listeners.click || []).forEach((fn) => fn()); }
  *walk() { yield this; for (const c of this.children) if (c instanceof FakeElement) yield* c.walk(); }
}

/** A page with `stored` in localStorage and `cookies` set, after consent.js has run. */
function page({ stored = {}, cookies = [] } = {}) {
  const storage = new Map(Object.entries(stored));
  const jar = new Map(cookies.map((c) => c.split("=")));
  const deleted = [];
  const document = {
    readyState: "complete",
    listeners: {},
    addEventListener(type, fn) { (this.listeners[type] ||= []).push(fn); },
    dispatchEvent(event) { (this.listeners[event.type] || []).forEach((fn) => fn(event)); },
    createElement: (tag) => new FakeElement(tag, document),
    createTextNode: (text) => ({ text }),
    getElementsByTagName: (tag) => [...document.documentElement.walk()].filter((e) => e.tagName === tag.toUpperCase()),
    getElementById: (id) => [...document.documentElement.walk()].find((e) => e.id === id) || null,
    get scripts() { return document.getElementsByTagName("script"); },
    get cookie() { return [...jar].map(([k, v]) => `${k}=${v}`).join("; "); },
    set cookie(value) {
      const [pair, ...attributes] = value.split(";").map((s) => s.trim());
      const [name, v] = pair.split("=");
      if (attributes.some((a) => a.toLowerCase() === "max-age=0")) {
        jar.delete(name);
        deleted.push(value);
      } else {
        jar.set(name, v);
      }
    },
  };
  document.documentElement = new FakeElement("html", document);
  document.head = document.documentElement.appendChild(new FakeElement("head", document));
  document.body = document.documentElement.appendChild(new FakeElement("body", document));
  // The page's own script, which the snippet inserts its tag before.
  document.head.appendChild(new FakeElement("script", document));

  const window = {
    document,
    reloaded: 0,
    location: { hostname: "goldberry.dev", href: "https://goldberry.dev/", reload() { window.reloaded++; } },
    localStorage: {
      getItem: (k) => (storage.has(k) ? storage.get(k) : null),
      setItem: (k, v) => storage.set(k, String(v)),
      removeItem: (k) => storage.delete(k),
      key: (i) => [...storage.keys()][i] ?? null,
      get length() { return storage.size; },
    },
    CustomEvent: class { constructor(type, init) { this.type = type; this.detail = init?.detail; } },
  };
  window.window = window;
  const context = vm.createContext(window);
  context.location = window.location;
  vm.runInContext(SOURCE, context, { filename: "consent.js" });

  const tags = () => document.getElementsByTagName("script").filter((s) => s.src === TAG);
  const banner = () => document.getElementById("gb-consent");
  const buttonOf = (cls) => [...banner().walk()].find((e) => e.className === cls);
  return { window, document, storage, jar, deleted, tags, banner, buttonOf, api: window.GoldberryConsent };
}

const granted = { "gb-consent": JSON.stringify({ choice: "granted", version: 1, date: "2026-10-02" }) };
const denied = { "gb-consent": JSON.stringify({ choice: "denied", version: 1, date: "2026-10-02" }) };

test("a first visit asks, and loads nothing and sets no cookie until it is answered", () => {
  const p = page();
  assert.ok(p.banner(), "the banner is shown");
  assert.equal(p.banner().attributes["aria-label"], "Cookie consent");
  assert.equal(p.tags().length, 0, "Metrika is not loaded");
  assert.equal(p.window.ym, undefined, "ym is not defined");
  assert.equal(p.jar.size, 0);
  assert.equal(p.api.choice(), null);
});

test("Accept loads Metrika once, with the counter and its options, and remembers it", () => {
  const p = page();
  p.buttonOf("gb-consent-accept").click();
  assert.equal(p.banner(), null, "the banner is gone");
  assert.equal(p.tags().length, 1, "tag.js is inserted");
  assert.equal(p.tags()[0].async, 1);
  const init = p.window.ym.a.find((args) => args[1] === "init");
  assert.equal(init[0], 113266145);
  assert.equal(init[2].webvisor, true);
  assert.equal(JSON.parse(p.storage.get("gb-consent")).choice, "granted");
  assert.equal(p.api.choice(), "granted");
  p.api.grant();
  assert.equal(p.tags().length, 1, "accepting twice loads it once");
});

test("Decline loads nothing, remembers it, and does not reload a page that counted nothing", () => {
  const p = page();
  p.buttonOf("gb-consent-decline").click();
  assert.equal(p.banner(), null);
  assert.equal(p.tags().length, 0);
  assert.equal(p.api.choice(), "denied");
  assert.equal(p.window.reloaded, 0);
});

test("a visitor who accepted is counted on the next page without being asked", () => {
  const p = page({ stored: granted });
  assert.equal(p.banner(), null);
  assert.equal(p.tags().length, 1);
});

test("a visitor who declined is neither asked nor counted on the next page", () => {
  const p = page({ stored: denied });
  assert.equal(p.banner(), null);
  assert.equal(p.tags().length, 0);
});

test("taking consent back deletes Metrika's cookies and storage, keeps the rest, and reloads", () => {
  const p = page({
    stored: { ...granted, _ym_retryReqs: "{}", "gb-theme": "dark" },
    cookies: ["_ym_uid=1", "_ym_d=2", "_ym_isad=2", "other=keep"],
  });
  assert.equal(p.tags().length, 1);
  p.api.deny();
  assert.deepEqual([...p.jar.keys()], ["other"], "only the _ym cookies go");
  assert.ok(p.deleted.some((c) => c.includes("domain=.goldberry.dev")), "on the domain as well as the host");
  assert.equal(p.storage.has("_ym_retryReqs"), false);
  assert.equal(p.storage.get("gb-theme"), "dark");
  assert.equal(p.api.choice(), "denied");
  assert.equal(p.window.reloaded, 1, "the counter that was running stops");
});

test("a choice stored under another version asks again", () => {
  const p = page({ stored: { "gb-consent": JSON.stringify({ choice: "granted", version: 0 }) } });
  assert.ok(p.banner());
  assert.equal(p.tags().length, 0);
});

test("a stored choice that is not JSON asks rather than throwing", () => {
  const p = page({ stored: { "gb-consent": "yes" } });
  assert.ok(p.banner());
});

test("the banner links the privacy policy, and open() shows it again after a choice", () => {
  const p = page({ stored: denied });
  p.api.open();
  const link = [...p.banner().walk()].find((e) => e.tagName === "A");
  assert.equal(link.href, "/privacy.html");
  p.api.open();
  assert.equal(p.document.body.children.filter((c) => c.id === "gb-consent").length, 1, "one banner, not two");
});

test("each answer is announced, so the privacy page can show it", () => {
  const p = page();
  const heard = [];
  p.document.addEventListener("gb-consent", (e) => heard.push(e.detail));
  p.api.grant();
  p.api.deny();
  assert.deepEqual(heard, ["granted", "denied"]);
});
