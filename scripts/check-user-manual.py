#!/usr/bin/env python3
"""Check the locally rendered handbook, anchors, includes and repository links.

Usage: python3 scripts/check-user-manual.py docs/biblios/build/docs-site
This checks local targets; it does not fetch external websites.
"""

import sys
import re
from collections import Counter
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit


class HandbookParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = []
        self.chapters = []
        self.tutorial_titles = []
        self.heading_text = None
        self.links = []
        self.assets = []
        self.text = []
        self.code_depth = 0
        self.emphasis_in_code = False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if attrs.get("id"):
            self.ids.append(attrs["id"])
        if tag == "h2":
            self.chapters.append(attrs.get("id"))
        if tag == "h3":
            self.heading_text = []
        if tag == "a" and attrs.get("href"):
            self.links.append(attrs["href"])
        if tag in ("link", "script", "img"):
            target = attrs.get("href") if tag == "link" else attrs.get("src")
            if target:
                self.assets.append(target)
        if tag == "code":
            self.code_depth += 1
        if tag in ("em", "strong") and self.code_depth:
            self.emphasis_in_code = True

    def handle_endtag(self, tag):
        if tag == "h3":
            title = "".join(self.heading_text or [])
            match = re.search(r"Beispiel ([A-I]):", title)
            if match:
                self.tutorial_titles.append(match.group(1))
            self.heading_text = None
        if tag == "code":
            self.code_depth -= 1

    def handle_data(self, data):
        self.text.append(data)
        if self.heading_text is not None:
            self.heading_text.append(data)


def require(condition, message):
    if not condition:
        raise SystemExit(message)


if len(sys.argv) != 2:
    raise SystemExit(__doc__)

repo = Path(__file__).resolve().parents[1]
site = Path(sys.argv[1]).resolve()
page = site / "benutzerhandbuch/main/index.html"
require((site / "index.html").is_file() and page.is_file(), "rendered handbook missing")
parser = HandbookParser()
parser.feed(page.read_text(encoding="utf-8"))
expected_chapters = [
    "_einführung", "_konzepte_was_wird_zu_was", "_installation", "erste-pipeline",
    "xtf-lesen", "xtf-schreiben", "erweiterte-transforms", "_beispiele",
    "_troubleshooting", "_glossar",
]
require(parser.chapters == expected_chapters, f"handbook chapters: {parser.chapters}")
duplicates = [key for key, count in Counter(parser.ids).items() if count > 1]
require(not duplicates, f"duplicate anchors: {duplicates}")
ids = set(parser.ids)
require(parser.tutorial_titles == list("ABCDEFGHI"), f"tutorials: {parser.tutorial_titles}")
require(all(f"beispiel-{letter}" in ids for letter in "ghi"), "new tutorial anchor missing")
require(not parser.emphasis_in_code, "underscores/emphasis damaged an inline code literal")
text = "".join(parser.text)
require("Unresolved directive" not in text and "include::" not in text, "unresolved AsciiDoc include")

fragment_links = set()
pipelines = set()
repo_prefix = "/edigonzales/hop-interlis-plugin/blob/main/"
for target in parser.links + parser.assets:
    url = urlsplit(target)
    if not url.scheme and not url.netloc:
        local = (page.parent / unquote(url.path)).resolve() if url.path else page
        if local.is_dir():
            local = local / "index.html"
        require(local.is_file(), f"missing local link/asset: {target}")
        if url.fragment and local == page:
            fragment = unquote(url.fragment)
            require(fragment in ids, f"missing fragment target: {target}")
            fragment_links.add(fragment)
    elif url.netloc == "github.com" and url.path.startswith(repo_prefix):
        relative = unquote(url.path[len(repo_prefix):])
        require((repo / relative).is_file(), f"missing repository example/document: {relative}")
        if relative.endswith(".hpl"):
            pipelines.add(relative)

require(all(chapter in fragment_links for chapter in expected_chapters), "chapter TOC link missing")
require(len(pipelines) == 12, f"expected twelve executable pipeline links, got {len(pipelines)}")
print("Handbook HTML OK: 10 chapters, 9 tutorials, anchors, includes, literals, assets and 12 pipeline links")
