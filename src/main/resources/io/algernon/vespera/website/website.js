// The website's search (ADR-333 section 3 and 9). It runs in the browser over what search-index.js set:
// the global VESPERA_SEARCH_INDEX, one element for each cluster, with its seed partition's name, its title,
// its label, the text of its writing and the name and link of each of its entries. It reads nothing else,
// asks nothing of any server, and writes what it finds as text and never as markup, so no title, label,
// writing or name can add an element or run a script.
//
// A page holds two elements for it: the query, id "search", and the place what it finds is written, id
// "search-results". The query carries, as attributes, the way up to the website's root ("data-root"), which
// the links of the index are relative to, and the words a page says when nothing was found ("data-none").
(function () {
  "use strict";

  var MOST_CLUSTERS_SHOWN = 50;
  var MOST_ENTRIES_SHOWN = 5;

  function textOf(value) {
    return typeof value === "string" ? value : "";
  }

  /** The words typed, lower case, split at white space. */
  function termsOf(typed) {
    return textOf(typed).toLowerCase().split(/\s+/).filter(function (term) {
      return term.length > 0;
    });
  }

  /** Everything of a cluster that a search reads, lower case, one string. */
  function haystackOf(cluster) {
    var parts = [textOf(cluster.partition), textOf(cluster.title), textOf(cluster.label), textOf(cluster.writing)];
    (Array.isArray(cluster.entries) ? cluster.entries : []).forEach(function (entry) {
      parts.push(textOf(entry.name));
    });
    return parts.join("\n").toLowerCase();
  }

  function anchor(document, href, text) {
    var link = document.createElement("a");
    link.setAttribute("href", href);
    link.appendChild(document.createTextNode(text));
    return link;
  }

  function bind() {
    var query = document.getElementById("search");
    var results = document.getElementById("search-results");
    if (!query || !results) {
      return;
    }
    var clusters = Array.isArray(globalThis.VESPERA_SEARCH_INDEX) ? globalThis.VESPERA_SEARCH_INDEX : [];
    var haystacks = clusters.map(haystackOf);
    var root = query.getAttribute("data-root") || "";
    var nothing = query.getAttribute("data-none") || "";

    function show() {
      var terms = termsOf(query.value);
      results.textContent = "";
      if (terms.length === 0) {
        return;
      }
      var shown = 0;
      clusters.forEach(function (cluster, at) {
        if (shown >= MOST_CLUSTERS_SHOWN) {
          return;
        }
        var haystack = haystacks[at];
        if (!terms.every(function (term) { return haystack.indexOf(term) >= 0; })) {
          return;
        }
        shown += 1;
        var item = document.createElement("li");
        var heading = textOf(cluster.title) || textOf(cluster.label);
        item.appendChild(anchor(document, root + textOf(cluster.link), heading));
        if (textOf(cluster.partition)) {
          item.appendChild(document.createTextNode(" - " + textOf(cluster.partition)));
        }
        var matching = (Array.isArray(cluster.entries) ? cluster.entries : []).filter(function (entry) {
          var name = textOf(entry.name).toLowerCase();
          return terms.some(function (term) { return name.indexOf(term) >= 0; });
        }).slice(0, MOST_ENTRIES_SHOWN);
        if (matching.length > 0) {
          var list = document.createElement("ul");
          matching.forEach(function (entry) {
            var row = document.createElement("li");
            row.appendChild(anchor(document, root + textOf(entry.link), textOf(entry.name)));
            list.appendChild(row);
          });
          item.appendChild(list);
        }
        results.appendChild(item);
      });
      if (shown === 0 && nothing) {
        var none = document.createElement("li");
        none.appendChild(document.createTextNode(nothing));
        results.appendChild(none);
      }
    }

    query.addEventListener("input", show);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", bind);
  } else {
    bind();
  }
})();
