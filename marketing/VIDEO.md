# Motion video from post frames

`make_video.swift` turns a sequence of PNG frames into an H.264 MP4 for
Instagram, Facebook and WhatsApp — a slow Ken Burns push on each still, with
cross-fades between them.

It exists because there is no ffmpeg on the machines that build this, and
installing one is not on the table. AVFoundation ships with macOS and encodes
H.264 natively, so the whole tool is one Swift script against `AVAssetWriter`
and CoreGraphics. No SwiftPM manifest, no Xcode project, no dependencies, no
network.

## Usage

```sh
swift marketing/make_video.swift --out <path.mp4> [options] <frame1.png> <frame2.png> ...
```

The frames come from `generate.py`, which renders one image per language into
`marketing/out/`. A carousel spec already produces the numbered slides this
wants, in order:

```sh
python3 marketing/generate.py marketing/content/customers-video-2026-09.json
swift marketing/make_video.swift --out marketing/out/customers-video-fa.mp4 \
    --size 1080x1920 \
    marketing/out/customers-video-2026-09_fa_0{1,2,3,4}_story.png
```

The slide files are zero-padded (`_01_`, `_02_`), so a shell glob and an upload
dialog both put them in swipe order. Pass them in the order they should play —
the tool does not sort.

`marketing/out/` is gitignored — the MP4 is build output, same as the PNGs.
The language rule in CLAUDE.md still applies: one video per language, Dari from
the light frames, Pashto from the dark ones. Never both colourways of the same
message in one language.

| option | default | notes |
| --- | --- | --- |
| `--out` | *required* | must end in `.mp4`; parent directories are created, an existing file is replaced |
| `--size` | `1080x1350` | Instagram feed. Stories are `1080x1920`. Must be even in both dimensions |
| `--fps` | `30` | |
| `--slide-seconds` | `2.6` | time each still is on screen, including its share of the fades |
| `--fade-seconds` | `0.6` | cross-fade overlap; `0` gives hard cuts |
| `--zoom` | `1.04` | Ken Burns end scale; `1.0` disables the motion |
| `--bitrate` | `9000000` scaled by width | average H.264 bitrate |

Duration is `slides × slide-seconds − (slides − 1) × fade-seconds`. Three slides
at the defaults is 6.60 s.

**Instagram's feed video minimum is 3 seconds**, so a one-slide video at the
default 2.6 s is rejected at upload. Use at least two slides, or raise
`--slide-seconds`.

## Design decisions

**Every frame must be exactly `--size`, and a mismatch is fatal.** Instagram
rejects an off-size upload without saying which file or which dimension was
wrong — the same reason `generate.py` asserts its sizes after rendering. The
error names the offending file and both sizes. The check runs before the writer
is created, so a rejected run leaves no partial MP4 behind. Nothing is ever
letterboxed or stretched to make a frame fit; the canvas and the frames share
one aspect ratio by construction.

**The timeline is computed in whole frames, not seconds.** `slideFrames` and
`fadeFrames` are rounded once, up front, and every later decision is integer
arithmetic on a frame index. Presentation times are `CMTime(value: i, timescale:
fps)`, so the duration is exact rather than accumulating float drift — 6.60 s
reads back as `3960/600`, not `6.5999999`.

**Ken Burns alternates direction.** Even slides zoom 1.00 → 1.04, odd slides
1.04 → 1.00, both eased with a smoothstep so nothing starts or stops with a
jerk. Alternating matters: every slide pushing the same way makes a carousel
pump rhythmically, which reads as a glitch. Since the scale never drops below
1.0 and the image is centred, the frame is always fully covered — verified at
both corners, at both ends of a slide.

**The cross-fade is plain source-over.** The outgoing slide is drawn at alpha 1,
the incoming one over it at the fade's alpha, which composites to exactly
`(1 − a)·previous + a·current`. Both are still running their own Ken Burns
during the overlap, so the motion does not stall at the seam.

**sRGB, explicitly — not `CGColorSpaceCreateDeviceRGB()`.** "Device RGB"
resolves to the host's display profile, and on a P3 Mac drawing an sRGB PNG
into it colour-converts: a flat `rgb(220,40,60)` test frame became
`rgb(229,64,76)` before the video encoder was even involved. That is the brand
rose visibly wrong, from a line that looks like a formality. The context is
built with `CGColorSpace(name: .sRGB)`.

**The track is tagged BT.709** (primaries, transfer and matrix), and the source
pixel buffers carry matching attachments. This does not change a single pixel —
measured identical against an untagged build — but it stops every player from
guessing, which is how a file ends up looking different in QuickTime and in the
Instagram player.

**32BGRA in, yuv420 out.** CoreGraphics draws straight into the adaptor's BGRA
buffers with no intermediate bitmap, and VideoToolbox does the chroma
conversion. H.264 High profile, autolevel, a keyframe every second, frame
reordering on. No audio track is ever added.

**Even dimensions are rejected early.** An odd width fails deep inside
`AVAssetWriter` with an unhelpful `-12902`; the guard says what is actually
wrong.

**The encode loop polls rather than using `requestMediaDataWhenReady`.** A
callback would need a run loop, and this is a script. Polling
`isReadyForMoreMediaData` keeps it straight-line code that exits with a real
status.

## Verified

Against three flat 1080×1350 PNGs at the defaults:

- `AVAsset` reports `6.6s (3960/600)`, `1080x1350`, `avc1`, 1 video track,
  **0 audio tracks**, nominal frame rate 30; `isPlayable` and `isReadable` both
  true. An `AVAssetReader` decodes exactly 198 frames.
- The mid-fade frame is a genuine blend, not either slide: at t=2.300 s the
  centre pixel is `(131, 57, 158)` between a red `(223, 44, 68)` and a blue
  `(48, 67, 223)`, ramping through `(206,48,85) → (70,65,207)` across the
  overlap.
- A 1080×1080 frame in a 1080×1350 run exits 1, names the file, and writes no
  MP4.
- On real content — the four `customers-video-2026-09` story frames, in Dari and
  in Pashto — it produced 8.60 s of 1080×1920 at 5.8 MB and 6.2 MB. Extracted
  frames show the Dari type sharp and a mid-fade frame with both slides visibly
  superimposed.

A convenient property of alternating the zoom: the outgoing slide is near 1.04
at the end of its push and the incoming one starts at 1.04, so the two images
are at almost the same scale exactly where they overlap.

## Limitations

- **Colour lifts slightly on dark mid-tones.** A source `rgb(139,58,71)` decodes
  and colour-manages back to `rgb(149,65,81)`; a bright `rgb(220,40,60)` comes
  back `rgb(223,44,68)`. This is the sRGB-versus-BT.709 transfer difference
  inherent to putting sRGB bytes into 8-bit 4:2:0 H.264 — ffmpeg's defaults do
  the same. Drawing in `CGColorSpace.itur_709` instead was tried and is *worse*
  (`(154,77,90)`, a double conversion). If a specific brand colour has to match
  exactly, check it in the rendered video rather than trusting the PNG.
- PNG only. Other formats are rejected by name rather than silently decoded.
- All frames are decoded up front, so memory scales with the slide count. Fine
  for a carousel; not a slideshow of hundreds.
- No audio, no text animation, no per-slide timing overrides. Add them only if a
  post actually needs them.
