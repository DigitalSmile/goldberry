// Prerenders the landing page for crawlers and link previews, and writes
// sitemap.xml and robots.txt. Plain Node, no dependencies. CI runs it as
//
//     node site/build.mjs _site <version> [stars]
//
// after `site/` has been copied to `_site/` and the book built into `_site/docs/`.
// A local preview does not need it: site.js renders the same HTML in the browser.
import { readFileSync, writeFileSync, readdirSync, statSync } from "node:fs";
import { join, relative, sep } from "node:path";
import vm from "node:vm";

const [out = "_site", version = "", starsArg = ""] = process.argv.slice(2);
const stars = parseInt(starsArg, 10) || 0;
const here = new URL(".", import.meta.url).pathname;

const ctx = { window: {} };
ctx.globalThis = ctx.window;
vm.createContext(ctx);
for (const f of ["content.js", "news.js", "assets/icons.js", "assets/render.js"]) {
  vm.runInContext(readFileSync(join(here, f), "utf8"), ctx, { filename: f });
}
const S = ctx.window.SITE, R = ctx.window.GoldberryRender;
const seo = S.seo, v = version || S.version;
const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/"/g, "&quot;");

let html = readFileSync(join(out, "index.html"), "utf8");

// <head>: title, description, canonical, Open Graph, Twitter card, JSON-LD.
const head = `<title>${esc(seo.title)}</title>
  <meta name="description" content="${esc(seo.description)}">
  <meta name="keywords" content="${esc(seo.keywords.join(", "))}">
  <link rel="canonical" href="${esc(seo.url)}">
  <meta property="og:type" content="website">
  <meta property="og:site_name" content="Goldberry">
  <meta property="og:title" content="${esc(seo.title)}">
  <meta property="og:description" content="${esc(seo.description)}">
  <meta property="og:url" content="${esc(seo.url)}">
  <meta property="og:image" content="${esc(seo.url)}assets/og-card.jpg">
  <meta property="og:image:width" content="1200">
  <meta property="og:image:height" content="630">
  <meta property="og:image:alt" content="Goldberry, a modern Java UI toolkit">
  <meta name="twitter:card" content="summary_large_image">
  <script type="application/ld+json">${R.jsonLd(S, v).replace(/</g, "\\u003c")}</script>`;
html = html.replace(/<!-- seo:start -->[\s\S]*?<!-- seo:end -->/, head);

// <body>: the same slots site.js fills.
const text = R.text(S, v);
html = html.replace(/(<[a-z0-9]+\b[^>]*\bdata-text="([a-zA-Z]+)"[^>]*>)([^<]*)(<\/)/g,
  (m, open, key, _old, close) => (text[key] ? open + esc(text[key]) + close : m));
html = html.replace(/(<[a-z0-9]+\b[^>]*\bdata-slot="([a-zA-Z]+)"[^>]*>)(<\/)/g,
  (m, open, key, close) => (R[key] ? open + R[key](S, v, stars) + close : m));
// the GitHub mark in the header button, and the star count the build was given
html = html.replace(/(<svg[^>]*data-icon="github"[^>]*>)(<\/svg>)/, (m, open, close) => open + (ctx.window.GB_ICONS.github || "") + close);
if (stars) {
  const txt = stars >= 1000 ? (stars / 1000).toFixed(stars >= 10000 ? 0 : 1).replace(/\.0$/, "") + "k" : String(stars);
  html = html.replace(/<em class="stars" data-stars hidden><\/em>/g, '<em class="stars" data-stars><svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (ctx.window.GB_ICONS.star || "") + "</svg>" + txt + "</em>");
}
html = html.replace(/(<a\b[^>]*?)href="[^"]*"([^>]*\bdata-link="([a-z]+)")/g,
  (m, a, b, key) => (S.links[key] ? `${a}href="${esc(S.links[key])}"${b}` : m));
writeFileSync(join(out, "index.html"), html);
writeFileSync(join(out, "assets/version.js"), `window.GOLDBERRY_VERSION = ${JSON.stringify(v)};\nwindow.GOLDBERRY_STARS = ${stars};\n`);
writeFileSync(join(out, "llms.txt"), R.llms(S, v));

// sitemap.xml: the landing page and every page of the book.
const pages = [];
const walk = (dir) => {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p);
    else if (name.endsWith(".html") && !["404.html", "print.html", "toc.html"].includes(name)) pages.push(p);
  }
};
walk(out);
const today = new Date().toISOString().slice(0, 10);
const urls = pages
  .map((p) => relative(out, p).split(sep).join("/").replace(/(^|\/)index\.html$/, "$1"))
  .sort((a, b) => a.length - b.length || a.localeCompare(b))
  .map((u) => `  <url><loc>${esc(seo.url + u)}</loc><lastmod>${today}</lastmod></url>`);
writeFileSync(join(out, "sitemap.xml"),
  `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls.join("\n")}\n</urlset>\n`);
writeFileSync(join(out, "robots.txt"), `User-agent: *\nAllow: /\n\n# Agents: a plain-text summary of this site is at ${seo.url}llms.txt\nSitemap: ${seo.url}sitemap.xml\n`);

console.log(`Prerendered index.html for v${v}${stars ? ` with ${stars} stars` : ""}; sitemap.xml lists ${urls.length} pages; llms.txt written.`);
