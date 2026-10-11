// book/theme/lightbox.js against a small fake of the browser: which pictures
// it makes openable, what it puts over the page, and how it goes away. Node's
// own runner, no dependencies:
//
//     node --test "book/test/*.test.mjs"
//
// pages.yml runs it with the landing page's tests before it builds the site.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const SOURCE = readFileSync(new URL("../theme/lightbox.js", import.meta.url), "utf8");

class FakeClassList {
  constructor() { this.names = new Set(); }
  add(name) { this.names.add(name); }
  remove(name) { this.names.delete(name); }
  contains(name) { return this.names.has(name); }
}

class FakeElement {
  constructor(tag, document) {
    this.tagName = tag.toUpperCase();
    this.document = document;
    this.children = [];
    this.attributes = {};
    this.listeners = {};
    this.parentNode = null;
    this.classList = new FakeClassList();
    this.textContent = "";
    this.className = "";
  }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  getAttribute(name) { return name in this.attributes ? this.attributes[name] : null; }
  appendChild(child) { child.parentNode = this; this.children.push(child); return child; }
  remove() {
    if (this.parentNode) {
      this.parentNode.children = this.parentNode.children.filter((c) => c !== this);
      this.parentNode = null;
    }
  }
  addEventListener(type, fn) { (this.listeners[type] ||= []).push(fn); }
  removeEventListener(type, fn) { this.listeners[type] = (this.listeners[type] || []).filter((f) => f !== fn); }
  dispatch(type, event = {}) {
    const full = { key: undefined, preventDefault() { this.defaultPrevented = true; }, ...event };
    (this.listeners[type] || []).slice().forEach((fn) => fn(full));
    return full;
  }
  focus() { this.document.activeElement = this; }
  matches(selector) { return selector.startsWith(".") ? this.classList.contains(selector.slice(1)) : this.tagName === selector.toUpperCase(); }
  descendants() { return this.children.flatMap((c) => [c, ...c.descendants()]); }
  querySelectorAll(selector) { return this.descendants().filter((e) => e.matches(selector)); }
  querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
}

class FakeDocument extends FakeElement {
  constructor() {
    super("#document", null);
    this.document = this;
    this.documentElement = new FakeElement("html", this);
    this.body = new FakeElement("body", this);
    this.documentElement.appendChild(this.body);
    this.activeElement = this.body;
  }
  createElement(tag) { return new FakeElement(tag, this); }
  querySelectorAll(selector) { return this.documentElement.querySelectorAll(selector); }
}

/** A page with one shot: a light and a dark picture and a caption, and runs the script over it. */
function page({ caption = "Five variants.", pictures = true } = {}) {
  const document = new FakeDocument();
  const shot = document.createElement("div");
  shot.classList.add("gb-shot");
  const light = document.createElement("img");
  light.src = "../images/button-light.webp";
  light.setAttribute("alt", "Five buttons");
  const dark = document.createElement("img");
  dark.src = "../images/button-dark.webp";
  dark.setAttribute("alt", "Five buttons");
  if (pictures) {
    shot.appendChild(light);
    shot.appendChild(dark);
  }
  if (caption) {
    const p = document.createElement("p");
    p.textContent = caption;
    shot.appendChild(p);
  }
  document.body.appendChild(shot);
  vm.runInNewContext(SOURCE, { document });
  return { document, shot, light, dark };
}

const overlayOf = (document) => document.body.children.find((c) => c.className === "gb-lightbox") || null;

test("makes every picture of a shot a button that says it enlarges", () => {
  const { shot, light, dark } = page();
  assert.ok(shot.classList.contains("gb-zoomable"));
  for (const picture of [light, dark]) {
    assert.equal(picture.getAttribute("role"), "button");
    assert.equal(picture.getAttribute("tabindex"), "0");
    assert.equal(picture.getAttribute("title"), "Click to enlarge");
  }
});

test("opens the picture that was clicked, at full size, with its caption", () => {
  const { document, dark } = page();
  dark.dispatch("click");
  const overlay = overlayOf(document);
  assert.ok(overlay, "an overlay is on the page");
  assert.equal(overlay.getAttribute("role"), "dialog");
  assert.equal(overlay.getAttribute("aria-modal"), "true");
  const [figure] = overlay.children;
  const [full, caption] = figure.children;
  assert.equal(full.src, "../images/button-dark.webp");
  assert.equal(full.alt, "Five buttons");
  assert.equal(caption.textContent, "Five variants.");
  assert.ok(document.documentElement.classList.contains("gb-lightbox-open"));
  assert.equal(document.activeElement.getAttribute("aria-label"), "Close");
});

test("opens from the keyboard with Enter or Space", () => {
  for (const key of ["Enter", " "]) {
    const { document, light } = page();
    const event = light.dispatch("keydown", { key });
    assert.ok(event.defaultPrevented, key + " does not scroll the page");
    assert.ok(overlayOf(document), key + " opens it");
  }
});

test("closes on Escape and gives focus back to the picture", () => {
  const { document, light } = page();
  light.dispatch("click");
  document.dispatch("keydown", { key: "Escape" });
  assert.equal(overlayOf(document), null);
  assert.ok(!document.documentElement.classList.contains("gb-lightbox-open"));
  assert.equal(document.activeElement, light);
  assert.deepEqual(document.listeners.keydown, [], "no key listener is left behind");
});

test("closes on a click anywhere over the page", () => {
  const { document, light } = page();
  light.dispatch("click");
  overlayOf(document).dispatch("click");
  assert.equal(overlayOf(document), null);
});

test("opens one view at a time", () => {
  const { document, light, dark } = page();
  light.dispatch("click");
  dark.dispatch("click");
  assert.equal(document.body.children.filter((c) => c.className === "gb-lightbox").length, 1);
});

test("leaves a shot with no picture alone, and a shot with no caption has none", () => {
  assert.ok(!page({ pictures: false }).shot.classList.contains("gb-zoomable"));
  const { document, light } = page({ caption: "" });
  light.dispatch("click");
  assert.equal(overlayOf(document).children[0].children.length, 1);
});
