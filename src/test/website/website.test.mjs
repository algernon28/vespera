// The website's search, held to docs/adr/0333 sections 3 and 9: it runs in the browser over titles,
// labels, the synthesis writing and entries' names, reads what search-index.js set and fetches nothing,
// and writes what it finds as text, never as markup.
//
//   node --test src/test/website/website.test.mjs
//
// The Java build has no JavaScript engine and gains none (docs/adr/0297 section 1, docs/adr/0046), so the
// script is held here, as the guard is, and .github/workflows/build.yml runs this file in both jobs.
//
// What is held is the script the website module ships, at the place the jar carries it from:
// src/main/resources/io/algernon/vespera/website/website.js, which website.TheWebsiteIsSelfContainedTest holds
// the drawn website.js equal to, byte for byte. It is run in a context of its own with no network and a
// small document of the test's, which holds the two elements every page of the website holds for its search,
// the query, id "search", and the place what it finds is written, id "search-results" (the Java test holds
// every page to carry both). The script is told a query by setting the query's value and sending it an
// "input" event, as a reader typing does.
//
// The index is set as search-index.js sets it (website.TheSearchIndexHoldsNoExtractedTextTest holds that
// shape): one global, VESPERA_SEARCH_INDEX, an array of a cluster an element, each of partition, title,
// label, writing, link and entries, each entry of name and link, every link relative to the website's root.
// A cluster with no writing has no title and no writing.
//
// What a search shows beyond a title and a link is not decided (docs/adr/0333, What this does not decide), so
// each case claims only that what it finds is in the results' text, linked by an anchor whose href ends with
// the cluster's or an entry's link, and that what it does not find is not there.

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const repo = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..", "..");
const WEBSITE_JS = resolve(repo, "src/main/resources/io/algernon/vespera/website/website.js");

const THE_INDEX = [
  {
    partition: "Fire Safety",
    title: "Fire doors and their inspection",
    label: "Fire doors",
    writing: "Doors close by themselves [1], and they are inspected twice a year [2].",
    link: "1-fire-safety/1-fire-doors/index.html",
    entries: [
      { name: "reports/door survey.docx", link: "1-fire-safety/1-fire-doors/index.html#document-1" },
      { name: "reports/inspection-log.docx", link: "1-fire-safety/1-fire-doors/index.html#document-2" },
    ],
  },
  {
    partition: "Fire Safety",
    label: "Smoke dampers",
    link: "1-fire-safety/2-smoke-dampers/index.html",
    entries: [{ name: "scans/damper-calibration.png", link: "1-fire-safety/2-smoke-dampers/index.html#document-1" }],
  },
  {
    partition: "Plant Rooms",
    title: "Boilers and their servicing",
    label: "Boilers",
    writing: "The boilers are serviced each autumn [1].",
    link: "2-plant-rooms/1-boilers/index.html",
    entries: [{ name: "reports/boiler-log.xlsx", link: "2-plant-rooms/1-boilers/index.html#document-1" }],
  },
];

const HOSTILE_TITLE = '<img src=x onerror="globalThis.pwned = 1">Hostile hinges';
const HOSTILE_LABEL = "<b>Hinges</b>";

/* ---------- a small document, which records every write of markup ---------- */

class Text {
  constructor(text) {
    this.nodeType = 3;
    this.data = String(text);
    this.parentNode = null;
    this.childNodes = [];
  }
  get textContent() {
    return this.data;
  }
  set textContent(text) {
    this.data = String(text);
  }
  get nodeValue() {
    return this.data;
  }
}

class Element {
  constructor(document, tagName) {
    this.ownerDocument = document;
    this.nodeType = 1;
    this.tagName = String(tagName).toUpperCase();
    this.nodeName = this.tagName;
    this.childNodes = [];
    this.parentNode = null;
    this.attributes = new Map();
    this.listeners = new Map();
    this.style = {};
    this.dataset = {};
    this.hidden = false;
    this.value = "";
    const classes = new Set();
    this.classList = {
      add: (...names) => names.forEach((name) => classes.add(name)),
      remove: (...names) => names.forEach((name) => classes.delete(name)),
      toggle: (name, on) => ((on ?? !classes.has(name)) ? classes.add(name) : classes.delete(name), classes.has(name)),
      contains: (name) => classes.has(name),
    };
    document.created.push(this);
  }
  get children() {
    return this.childNodes.filter((node) => node.nodeType === 1);
  }
  get firstChild() {
    return this.childNodes[0] ?? null;
  }
  get lastChild() {
    return this.childNodes[this.childNodes.length - 1] ?? null;
  }
  get firstElementChild() {
    return this.children[0] ?? null;
  }
  appendChild(node) {
    if (node.tagName === "#FRAGMENT") {
      [...node.childNodes].forEach((child) => this.appendChild(child));
      return node;
    }
    if (node.parentNode) node.parentNode.removeChild(node);
    node.parentNode = this;
    this.childNodes.push(node);
    return node;
  }
  append(...nodes) {
    nodes.forEach((node) => this.appendChild(typeof node === "string" ? new Text(node) : node));
  }
  prepend(...nodes) {
    const after = this.childNodes;
    this.childNodes = [];
    this.append(...nodes);
    after.forEach((node) => this.childNodes.push(node));
  }
  insertBefore(node, before) {
    if (before === null || before === undefined) return this.appendChild(node);
    if (node.parentNode) node.parentNode.removeChild(node);
    node.parentNode = this;
    this.childNodes.splice(this.childNodes.indexOf(before), 0, node);
    return node;
  }
  replaceChildren(...nodes) {
    this.childNodes.forEach((node) => (node.parentNode = null));
    this.childNodes = [];
    this.append(...nodes);
  }
  removeChild(node) {
    this.childNodes = this.childNodes.filter((child) => child !== node);
    node.parentNode = null;
    return node;
  }
  remove() {
    if (this.parentNode) this.parentNode.removeChild(this);
  }
  get textContent() {
    return this.childNodes.map((node) => node.textContent).join("");
  }
  set textContent(text) {
    this.replaceChildren();
    if (String(text) !== "") this.appendChild(new Text(text));
  }
  get innerText() {
    return this.textContent;
  }
  set innerText(text) {
    this.textContent = text;
  }
  get innerHTML() {
    return this.textContent.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }
  set innerHTML(markup) {
    if (String(markup) !== "") this.ownerDocument.markupWritten.push(String(markup));
    this.replaceChildren();
  }
  set outerHTML(markup) {
    this.ownerDocument.markupWritten.push(String(markup));
  }
  insertAdjacentHTML(position, markup) {
    this.ownerDocument.markupWritten.push(String(markup));
  }
  setAttribute(name, value) {
    const attribute = String(name).toLowerCase();
    if (attribute.startsWith("on")) this.ownerDocument.markupWritten.push(`${attribute}=${value}`);
    this.attributes.set(attribute, String(value));
  }
  getAttribute(name) {
    return this.attributes.get(String(name).toLowerCase()) ?? null;
  }
  hasAttribute(name) {
    return this.attributes.has(String(name).toLowerCase());
  }
  removeAttribute(name) {
    this.attributes.delete(String(name).toLowerCase());
  }
  get href() {
    return this.getAttribute("href") ?? "";
  }
  set href(value) {
    this.setAttribute("href", value);
  }
  get id() {
    return this.getAttribute("id") ?? "";
  }
  set id(value) {
    this.setAttribute("id", value);
  }
  get className() {
    return this.getAttribute("class") ?? "";
  }
  set className(value) {
    this.setAttribute("class", value);
  }
  addEventListener(type, listener) {
    if (!this.listeners.has(type)) this.listeners.set(type, []);
    this.listeners.get(type).push(listener);
  }
  removeEventListener(type, listener) {
    this.listeners.set(type, (this.listeners.get(type) ?? []).filter((each) => each !== listener));
  }
  dispatchEvent(event) {
    (this.listeners.get(event.type) ?? []).forEach((listener) =>
      typeof listener === "function" ? listener.call(this, event) : listener.handleEvent(event));
    const handler = this[`on${event.type}`];
    if (typeof handler === "function") handler.call(this, event);
    return true;
  }
  querySelectorAll(selector) {
    const found = [];
    const walk = (node) =>
      node.children.forEach((child) => {
        if (matches(child, selector)) found.push(child);
        walk(child);
      });
    walk(this);
    return found;
  }
  querySelector(selector) {
    return this.querySelectorAll(selector)[0] ?? null;
  }
  closest() {
    return null;
  }
  focus() {}
  blur() {}
}

function matches(element, selector) {
  const s = String(selector).trim();
  if (s.startsWith("#")) return element.id === s.slice(1);
  if (s.startsWith(".")) return element.classList.contains(s.slice(1));
  return element.tagName === s.toUpperCase();
}

function aDocument() {
  const document = {
    created: [],
    markupWritten: [],
    listeners: new Map(),
    readyState: "loading",
  };
  const element = (tag) => new Element(document, tag);
  document.documentElement = element("html");
  document.documentElement.setAttribute("lang", "en");
  document.head = element("head");
  document.body = element("body");
  document.documentElement.appendChild(document.head);
  document.documentElement.appendChild(document.body);
  const query = element("input");
  query.setAttribute("type", "search");
  query.id = "search";
  const results = element("ol");
  results.id = "search-results";
  document.body.appendChild(query);
  document.body.appendChild(results);
  document.query = query;
  document.results = results;
  document.createElement = (tag) => element(tag);
  document.createTextNode = (text) => new Text(text);
  document.createDocumentFragment = () => element("#fragment");
  document.getElementById = (id) => document.documentElement.querySelector(`#${id}`);
  document.querySelector = (selector) => document.documentElement.querySelector(selector);
  document.querySelectorAll = (selector) => document.documentElement.querySelectorAll(selector);
  document.addEventListener = (type, listener) => {
    if (!document.listeners.has(type)) document.listeners.set(type, []);
    document.listeners.get(type).push(listener);
  };
  document.removeEventListener = () => {};
  document.write = (markup) => document.markupWritten.push(String(markup));
  document.writeln = document.write;
  document.currentScript = { src: "file:///website/website.js", getAttribute: () => null, dataset: {} };
  return document;
}

/* ---------- the script, run once per case ---------- */

function theWebsite(index) {
  const document = aDocument();
  const reached = [];
  const context = {
    document,
    location: { href: "file:///website/index.html", protocol: "file:", pathname: "/website/index.html" },
    navigator: { userAgent: "node" },
    console,
    URL,
    URLSearchParams,
    setTimeout: (callback) => (callback(), 0),
    clearTimeout: () => {},
    requestAnimationFrame: (callback) => (callback(0), 0),
    cancelAnimationFrame: () => {},
    fetch: (...args) => (reached.push(["fetch", ...args]), Promise.reject(new Error("no network"))),
    XMLHttpRequest: function XMLHttpRequest() {
      reached.push(["XMLHttpRequest"]);
      throw new Error("no network");
    },
    WebSocket: function WebSocket(...args) {
      reached.push(["WebSocket", ...args]);
      throw new Error("no network");
    },
    VESPERA_SEARCH_INDEX: index,
  };
  context.window = context;
  context.self = context;
  context.addEventListener = (type, listener) => document.addEventListener(`window:${type}`, listener);
  context.removeEventListener = () => {};
  vm.createContext(context);
  vm.runInContext(readFileSync(WEBSITE_JS, "utf8"), context, { filename: "website.js" });
  document.readyState = "complete";
  for (const type of ["DOMContentLoaded", "window:DOMContentLoaded", "window:load"]) {
    (document.listeners.get(type) ?? []).forEach((listener) => listener.call(document, { type: type.replace("window:", "") }));
  }
  return {
    context,
    document,
    reached,
    search(words) {
      document.query.value = words;
      document.query.dispatchEvent({
        type: "input",
        target: document.query,
        currentTarget: document.query,
        preventDefault() {},
        stopPropagation() {},
      });
      return document.results;
    },
  };
}

/** Every element beneath {@code node}, itself left out. */
function under(node) {
  return node.children.flatMap((child) => [child, ...under(child)]);
}

/** The hrefs of the anchors beneath {@code node}. */
function linksUnder(node) {
  return under(node)
    .filter((element) => element.tagName === "A")
    .map((anchor) => anchor.getAttribute("href") ?? "");
}

/** Whether one of the anchors beneath {@code results} lands on {@code cluster}'s page or one of its entries. */
function linksTo(results, cluster) {
  const targets = [cluster.link, ...cluster.entries.map((entry) => entry.link)];
  return linksUnder(results).some((href) => targets.some((target) => href.endsWith(target)));
}

/* ---------- the cases ---------- */

test("website.js is there to be held, where the website module ships it", () => {
  assert.ok(readFileSync(WEBSITE_JS, "utf8").length > 0, `${WEBSITE_JS} holds the script`);
});

test("a word of a title finds its group, whatever its case, linked to its page, and not the others", () => {
  const website = theWebsite(THE_INDEX);
  const results = website.search("INSPECTION");

  assert.ok(results.textContent.includes(THE_INDEX[0].title), "the doors' title is among what was found");
  assert.ok(linksTo(results, THE_INDEX[0]), "an anchor found lands on the doors' page or an entry of it");
  assert.ok(!results.textContent.includes(THE_INDEX[2].title), "the boilers' title is not among what was found");
});

test("a group with no writing is found by its label, and shown by it", () => {
  const results = theWebsite(THE_INDEX).search("dampers");

  assert.ok(results.textContent.includes(THE_INDEX[1].label), "the dampers' label is among what was found");
  assert.ok(linksTo(results, THE_INDEX[1]), "an anchor found lands on the dampers' page or its entry");
});

test("a sentence of the writing finds its group", () => {
  const results = theWebsite(THE_INDEX).search("serviced each autumn");

  assert.ok(results.textContent.includes(THE_INDEX[2].title), "the boilers' title is among what was found");
  assert.ok(linksTo(results, THE_INDEX[2]), "an anchor found lands on the boilers' page or its entry");
  assert.ok(!results.textContent.includes(THE_INDEX[0].title), "the doors are not found by the boilers' writing");
});

test("an entry's name finds its group", () => {
  const results = theWebsite(THE_INDEX).search("damper-calibration");

  assert.ok(linksTo(results, THE_INDEX[1]), "an anchor found lands on the dampers' page or its entry");
  assert.ok(!results.textContent.includes(THE_INDEX[0].title), "the doors are not found by the dampers' entry");
});

test("a seed partition's name finds its groups", () => {
  const results = theWebsite(THE_INDEX).search("Plant Rooms");

  assert.ok(results.textContent.includes(THE_INDEX[2].title), "the boilers, under Plant Rooms, are found");
  assert.ok(!results.textContent.includes(THE_INDEX[0].title), "the doors, under Fire Safety, are not");
});

test("what a search finds is written as text, never as markup", () => {
  const index = [
    {
      partition: "Fire Safety",
      title: HOSTILE_TITLE,
      label: HOSTILE_LABEL,
      writing: "Hinges <script>globalThis.pwned = 2</script> are oiled [1].",
      link: "1-fire-safety/3-hinges/index.html",
      entries: [{ name: "reports/<i>hinge</i>.docx", link: "1-fire-safety/3-hinges/index.html#document-1" }],
    },
  ];
  const website = theWebsite(index);
  const results = website.search("hostile");

  assert.ok(results.textContent.includes(HOSTILE_TITLE), "the title is shown as the characters it holds");
  assert.deepEqual(website.document.markupWritten, [], "nothing was written as markup, nor as a handler");
  assert.deepEqual(
    under(results).map((element) => element.tagName).filter((tag) => ["IMG", "B", "I", "SCRIPT"].includes(tag)),
    [],
    "no element the title, the label, the writing or a name spells was made",
  );
  assert.equal(website.context.pwned, undefined, "and nothing the title or the writing carries ran");
});

test("a search reaches no network: the index is what search-index.js set", () => {
  const website = theWebsite(THE_INDEX);
  website.search("doors");
  website.search("boilers");

  assert.deepEqual(website.reached, [], "no fetch, no request and no socket was made");
});

test("the script names nothing that writes markup or reaches the network", () => {
  const script = readFileSync(WEBSITE_JS, "utf8");
  for (const forbidden of ["insertAdjacentHTML", "outerHTML", "document.write", "eval(", "new Function", "fetch(",
    "XMLHttpRequest", "WebSocket", "EventSource", "sendBeacon", "importScripts", "import(", "http:", "https:"]) {
    assert.ok(!script.includes(forbidden), `website.js names ${forbidden}`);
  }
});
