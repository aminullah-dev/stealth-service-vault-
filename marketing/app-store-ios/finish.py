#!/usr/bin/env python3
"""Turn raw `xcrun simctl io <udid> screenshot` captures into App Store files.

    python3 finish.py RAW.png [RAW.png ...] --out DIR

Two changes, each checked rather than assumed:

- The iOS 26.5 simulator draws a one-point black square at the top-left corner
  of every capture. It is not the app: a capture of the Settings app has the
  same square. Its 4x4-pixel block is filled with the colour just inside it,
  and the script asserts that no other pixel changed.
- The alpha channel is dropped. It is fully opaque (asserted), and App Store
  screenshots should not carry one.

Only the 6.9" portrait sizes are accepted, so a capture from the wrong
simulator fails here rather than at upload.
"""
import os
import sys

from PIL import Image, ImageChops

SIZES = {(1320, 2868), (1290, 2796)}
BLOCK = 4   # the 3x3 square plus its one-pixel antialiased edge


def finish(src, out_dir):
    raw = Image.open(src)
    if raw.size not in SIZES:
        sys.exit(f"{src}: {raw.size[0]}x{raw.size[1]} is not a 6.9-inch size {sorted(SIZES)}")
    if raw.mode == "RGBA" and raw.getchannel("A").getextrema() != (255, 255):
        sys.exit(f"{src}: has real transparency; refusing to flatten it")
    img = raw.convert("RGB")

    corner = [img.getpixel((x, y)) for y in range(3) for x in range(3)]
    if any(max(p) > 16 for p in corner):
        sys.exit(f"{src}: top-left corner is not the simulator's black square; look at it")
    fill = img.getpixel((BLOCK + 1, BLOCK + 1))
    fixed = img.copy()
    for y in range(BLOCK):
        for x in range(BLOCK):
            fixed.putpixel((x, y), fill)

    bbox = ImageChops.difference(img, fixed).getbbox()
    assert bbox is None or (bbox[2] <= BLOCK and bbox[3] <= BLOCK), f"{src}: changed {bbox}"

    dst = os.path.join(out_dir, os.path.basename(src))
    fixed.save(dst, optimize=True)
    check = Image.open(dst)
    assert check.size == raw.size and check.mode == "RGB"
    print(f"{dst}  {check.size[0]}x{check.size[1]} RGB")


if __name__ == "__main__":
    args = sys.argv[1:]
    if "--out" not in args or args.index("--out") == len(args) - 1:
        sys.exit(__doc__)
    i = args.index("--out")
    out_dir = args[i + 1]
    os.makedirs(out_dir, exist_ok=True)
    for path in args[:i] + args[i + 2:]:
        finish(path, out_dir)
