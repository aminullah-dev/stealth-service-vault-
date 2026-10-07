#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Copy the rendered set out of marketing/out/ into the folder the owner posts
from, named for people rather than for the generator, and write the captions.

    python3 marketing/generate.py --all
    python3 marketing/generate.py --as story <single-post specs…>
    python3 marketing/export.py ~/Desktop/1/SafeBeauty-Marketing

Only files this script names are written. Anything else in the destination —
the raw app captures, the App Store screenshots, the screen recordings — is
left alone, because those are not rendered here and cannot be rebuilt from
marketing/out/.

The captions are generated from the same specs the images are, so the words
under a post are the words on it. What a caption adds is the part an image
cannot carry: where to get the app, and the hashtags.
"""

import io
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENT = os.path.join(ROOT, "marketing", "content")
OUT = os.path.join(ROOT, "marketing", "out")

LANGS = {"fa": "dari", "ps": "pashto", "en": "english"}

# Carousels: spec slug -> the folder name the owner knows them by.
CAROUSELS = {
    "salons-join-2026-09": "carousel-salons",
    "customers-book-2026-09": "carousel-customers",
    "real-app-2026-09": "carousel-real-app",
    "launch-iphone-2026-09": "carousel-launch-iphone",
}

# WhatsApp channel: one image a message, per language. A channel post is read
# as a single picture in a notification, so no carousels here.
WHATSAPP = [
    ("01-iphone-launch", "a-now-on-iphone"),
    ("02-salons", "s-iphone-too"),
    ("03-customers", "c-how-to-book"),
]

VIDEOS = [
    ("launch-iphone", "launch-iphone"),
    ("customers", "customers"),
]

STORE_LINE = {
    "fa": "📲 رایگان در App Store و Google Play",
    "ps": "📲 وړیا په App Store او Google Play کې",
    "en": "Free on the App Store and Google Play",
}
TAGS = {
    "fa": "#SafeBeauty #سیف_بیوتی #کابل #سالن_زیبایی #رزرو_آنلاین",
    "ps": "#SafeBeauty #سیف_بیوټي #کابل #ښکلا_سالون #آنلاین_بکینګ",
    "en": "#SafeBeauty #Kabul #Afghanistan",
}


def folder_for(spec):
    slug = spec["slug"]
    if slug.startswith("s-"):
        return "posts-salons"
    if slug.startswith("c-"):
        return "posts-customers"
    return "posts-brand"


def copy(src, dst):
    if not os.path.exists(src):
        sys.exit("not rendered: %s — run generate.py first" % os.path.relpath(src, ROOT))
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(src, dst)


def strip_tags(s):
    return s.replace("<bdi>", "").replace("</bdi>", "")


def caption(text, lang):
    """The words on the image, then the store line, the link and the tags."""
    lines = []
    for field in ("headline", "quote"):
        if text.get(field):
            lines.append(text[field])
    if text.get("stat"):
        lines.append("%s — %s" % (text["stat"], text.get("label", "")))
    body = text.get("body", "")
    if body and body not in lines:
        lines += ["", body]
    if text.get("steps"):
        lines.append("")
        lines += ["%d. %s" % (i, s) for i, s in enumerate(text["steps"], 1)]
    link = text.get("cta") or "safebeauty.web.app/get"
    if link.startswith("safebeauty"):
        lines += ["", STORE_LINE[lang], "👉 " + link]
    else:
        lines += ["", link]
    lines += ["", TAGS[lang]]
    return "\n".join("> " + strip_tags(l) if l else ">" for l in lines)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__.strip().split("\n\n")[1])
    dest = os.path.expanduser(sys.argv[1])
    specs = []
    for f in sorted(os.listdir(CONTENT)):
        if f.endswith(".json") and f != "TEMPLATE.json" and not f.startswith("_"):
            with io.open(os.path.join(CONTENT, f), encoding="utf-8") as fh:
                specs.append(json.load(fh))

    n = 0
    doc = {"posts-salons": [], "posts-customers": [], "posts-brand": [], "carousels": []}
    for spec in specs:
        slug = spec["slug"]
        if spec.get("slides"):
            if slug not in CAROUSELS:
                continue      # frames for a video, not a post
            name = CAROUSELS[slug]
            langs = list(spec["slides"][0]["text"])
            for lang in langs:
                for i in range(1, len(spec["slides"]) + 1):
                    src = os.path.join(OUT, "%s_%s_%02d_post.png" % (slug, lang, i))
                    for net in ("instagram", "facebook"):
                        copy(src, os.path.join(dest, net, "%s-%s" % (name, LANGS[lang]),
                                               "%02d.png" % i))
                        n += 1
                    story = os.path.join(OUT, "%s_%s_%02d_story.png" % (slug, lang, i))
                    if os.path.exists(story):
                        copy(story, os.path.join(dest, "stories", "%s-%s" % (name, LANGS[lang]),
                                                 "%02d.png" % i))
                        n += 1
            # A carousel's caption is its first slide's and its last slide's
            # words: the hook, and the call to action.
            for lang in langs:
                first = spec["slides"][0]["text"][lang]
                last = spec["slides"][-1]["text"][lang]
                merged = dict(first)
                merged["body"] = first.get("body") or last.get("headline", "")
                merged["cta"] = last.get("cta", "")
                doc["carousels"].append((name, lang, caption(merged, lang)))
            continue
        folder = folder_for(spec)
        for lang in spec["text"]:
            copy(os.path.join(OUT, "%s_%s_post.png" % (slug, lang)),
                 os.path.join(dest, folder, "%s_%s.png" % (slug, LANGS[lang])))
            n += 1
            story = os.path.join(OUT, "%s_%s_story.png" % (slug, lang))
            if os.path.exists(story):
                copy(story, os.path.join(dest, "stories", folder.replace("posts-", ""),
                                         "%s_%s.png" % (slug, LANGS[lang])))
                n += 1
            doc[folder].append((slug, lang, caption(spec["text"][lang], lang)))

    for name, slug in WHATSAPP:
        for lang in ("fa", "ps"):
            copy(os.path.join(OUT, "%s_%s_post.png" % (slug, lang)),
                 os.path.join(dest, "whatsapp", LANGS[lang], name + ".png"))
            n += 1

    for name, base in VIDEOS:
        for lang in ("fa", "ps"):
            src = os.path.join(OUT, "video", "%s-%s.mp4" % (base, lang))
            if os.path.exists(src):
                copy(src, os.path.join(dest, "video", "%s-%s.mp4" % (name, LANGS[lang])))
                n += 1

    out = [
        "# Captions — every post",
        "",
        "Generated by `marketing/export.py` from the same specs as the images, so the",
        "words here are the words on the picture. Dari and Pashto are two posts, not",
        "one: the images are different colourways.",
        "",
    ]
    titles = [("carousels", "Carousels"), ("posts-salons", "Salon owners"),
              ("posts-customers", "Customers"), ("posts-brand", "Brand and news")]
    for key, title in titles:
        out += ["", "## " + title, ""]
        for slug, lang, text in doc[key]:
            out += ["**`%s` · %s**" % (slug, LANGS[lang]), "", text, ""]
    path = os.path.join(dest, "captions", "CAPTIONS-ALL-POSTS.md")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")

    print("%d files into %s" % (n, dest))
    print("captions: %s" % path)


if __name__ == "__main__":
    main()
