#!/usr/bin/env swift
//
//  make_video.swift — turn a sequence of PNG frames into an H.264 MP4.
//
//      swift marketing/make_video.swift --out marketing/out/launch-fa.mp4 \
//          --size 1080x1350 --fps 30 --slide-seconds 2.6 --fade-seconds 0.6 \
//          marketing/out/launch-fa-1.png marketing/out/launch-fa-2.png
//
//  There is no ffmpeg on the machines that build this, and installing one is
//  not on the table, so the encoder is AVFoundation — AVAssetWriter with a
//  pixel-buffer adaptor — which ships with macOS. Frames are composited by
//  hand in CoreGraphics: a slow Ken Burns push on each still, alternating
//  direction so a carousel does not pulse, and a cross-fade between slides.
//
//  See marketing/VIDEO.md for the why behind the defaults.
//

import AVFoundation
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

// MARK: - Failure

let toolName = "make_video.swift"

func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data("\(toolName): error: \(message)\n".utf8))
    exit(1)
}

// MARK: - Options

struct Options {
    var outputPath: String = ""
    var width: Int = 1080
    var height: Int = 1350
    var fps: Int32 = 30
    var slideSeconds: Double = 2.6
    var fadeSeconds: Double = 0.6
    var zoom: Double = 1.04
    var bitrate: Int = 0          // 0 = derive from width
    var frames: [String] = []
}

let usage = """
usage: swift marketing/make_video.swift --out <path.mp4> [options] <frame1.png> <frame2.png> ...

  --out <path>            output .mp4 (required)
  --size <WxH>            expected frame size, default 1080x1350 (Instagram post)
  --fps <n>               frames per second, default 30
  --slide-seconds <s>     seconds each still is on screen, default 2.6
  --fade-seconds <s>      cross-fade overlap between slides, default 0.6 (0 = hard cut)
  --zoom <f>              Ken Burns end scale, default 1.04 (1.0 = no motion)
  --bitrate <bits>        average H.264 bitrate, default 9000000 scaled by width
  -h, --help              this text

Every input PNG must be exactly the requested size; Instagram rejects off-size
video and reports neither which file nor which dimension was wrong.
"""

func parseOptions(_ argv: [String]) -> Options {
    var o = Options()
    var sawOut = false
    var i = 0

    func value(_ flag: String) -> String {
        i += 1
        guard i < argv.count else { fail("\(flag) needs a value\n\n\(usage)") }
        return argv[i]
    }

    func positiveDouble(_ flag: String, _ raw: String, allowZero: Bool = false) -> Double {
        guard let d = Double(raw), d.isFinite, allowZero ? d >= 0 : d > 0 else {
            fail("\(flag) expects a \(allowZero ? "non-negative" : "positive") number, got \(raw.isEmpty ? "\"\"" : raw)")
        }
        return d
    }

    while i < argv.count {
        let arg = argv[i]
        switch arg {
        case "-h", "--help":
            print(usage)
            exit(0)
        case "--out":
            o.outputPath = value(arg)
            sawOut = true
        case "--size":
            let raw = value(arg)
            let parts = raw.lowercased().split(separator: "x", omittingEmptySubsequences: false)
            guard parts.count == 2, let w = Int(parts[0]), let h = Int(parts[1]), w > 0, h > 0 else {
                fail("--size expects WxH, e.g. 1080x1350, got \(raw)")
            }
            o.width = w
            o.height = h
        case "--fps":
            let raw = value(arg)
            guard let f = Int32(raw), f > 0, f <= 120 else { fail("--fps expects 1...120, got \(raw)") }
            o.fps = f
        case "--slide-seconds":
            o.slideSeconds = positiveDouble(arg, value(arg))
        case "--fade-seconds":
            o.fadeSeconds = positiveDouble(arg, value(arg), allowZero: true)
        case "--zoom":
            let z = positiveDouble(arg, value(arg))
            guard z >= 1.0, z <= 2.0 else { fail("--zoom expects 1.0...2.0, got \(z)") }
            o.zoom = z
        case "--bitrate":
            let raw = value(arg)
            guard let b = Int(raw), b >= 100_000 else { fail("--bitrate expects at least 100000, got \(raw)") }
            o.bitrate = b
        default:
            if arg.hasPrefix("--") { fail("unknown option \(arg)\n\n\(usage)") }
            o.frames.append(arg)
        }
        i += 1
    }

    guard sawOut, !o.outputPath.isEmpty else { fail("--out is required\n\n\(usage)") }
    guard !o.frames.isEmpty else { fail("no input PNGs given\n\n\(usage)") }
    guard o.outputPath.lowercased().hasSuffix(".mp4") else {
        fail("--out must end in .mp4, got \(o.outputPath)")
    }
    // H.264 macroblocks are 16x16 and the encoder only accepts even dimensions;
    // an odd one fails deep inside AVAssetWriter with an unhelpful -12902.
    guard o.width % 2 == 0, o.height % 2 == 0 else {
        fail("--size must be even in both dimensions for H.264, got \(o.width)x\(o.height)")
    }
    return o
}

let options = parseOptions(Array(CommandLine.arguments.dropFirst()))

// MARK: - Timeline (computed in whole frames so the duration is exact)

let fps = options.fps
let slideFrames = Int((options.slideSeconds * Double(fps)).rounded())
var fadeFrames = Int((options.fadeSeconds * Double(fps)).rounded())
let slideCount = options.frames.count

guard slideFrames >= 1 else {
    fail("--slide-seconds \(options.slideSeconds) at --fps \(fps) rounds to zero frames")
}
if fadeFrames >= slideFrames {
    fail("--fade-seconds (\(options.fadeSeconds)) must be shorter than --slide-seconds (\(options.slideSeconds))")
}
if slideCount == 1 { fadeFrames = 0 }

// Slide k occupies [k * stride, k * stride + slideFrames); consecutive slides
// overlap by exactly fadeFrames, which is where the cross-fade lives.
let stride = slideFrames - fadeFrames
let totalFrames = slideCount * slideFrames - (slideCount - 1) * fadeFrames
let duration = Double(totalFrames) / Double(fps)

// MARK: - Input PNGs

func loadFrame(_ path: String, index: Int) -> CGImage {
    let url = URL(fileURLWithPath: path)
    guard FileManager.default.fileExists(atPath: url.path) else {
        fail("input frame \(index + 1) does not exist: \(path)")
    }
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else {
        fail("could not read \(path) — is it an image?")
    }
    let type = CGImageSourceGetType(source) as String?
    guard type == UTType.png.identifier else {
        fail("\(path) is not a PNG (it is \(type ?? "an unrecognised format"))")
    }
    guard let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
          let w = properties[kCGImagePropertyPixelWidth] as? Int,
          let h = properties[kCGImagePropertyPixelHeight] as? Int else {
        fail("could not read the pixel dimensions of \(path)")
    }
    guard w == options.width, h == options.height else {
        fail("""
             \(path) is \(w)x\(h), but --size is \(options.width)x\(options.height).
             Every frame must be exactly the output size — the video is never letterboxed \
             or stretched to fit, and Instagram rejects an off-size upload without saying which \
             file was wrong.
             """)
    }
    guard let image = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCache: true] as CFDictionary) else {
        fail("could not decode \(path)")
    }
    return image
}

let images = options.frames.enumerated().map { loadFrame($1, index: $0) }

// MARK: - Writer

let outputURL = URL(fileURLWithPath: options.outputPath)
let outputDirectory = outputURL.deletingLastPathComponent()
if !FileManager.default.fileExists(atPath: outputDirectory.path) {
    do {
        try FileManager.default.createDirectory(at: outputDirectory, withIntermediateDirectories: true)
    } catch {
        fail("could not create output directory \(outputDirectory.path): \(error.localizedDescription)")
    }
}
if FileManager.default.fileExists(atPath: outputURL.path) {
    do {
        try FileManager.default.removeItem(at: outputURL)
    } catch {
        fail("could not overwrite \(outputURL.path): \(error.localizedDescription)")
    }
}

// ~9 Mbps at 1080 wide: comfortably above what Instagram re-encodes to, so the
// gradients in the brand background do not band, and small enough to send over
// WhatsApp.
let bitrate = options.bitrate > 0
    ? options.bitrate
    : max(2_000_000, Int(9_000_000.0 * Double(options.width) / 1080.0))

let writer: AVAssetWriter
do {
    writer = try AVAssetWriter(outputURL: outputURL, fileType: .mp4)
} catch {
    fail("could not create the MP4 writer: \(error.localizedDescription)")
}

let videoSettings: [String: Any] = [
    AVVideoCodecKey: AVVideoCodecType.h264,
    AVVideoWidthKey: options.width,
    AVVideoHeightKey: options.height,
    // Tag the track BT.709. Untagged, the encoder converts BGRA to YCbCr with
    // one matrix and every player guesses another: measured on a flat
    // rgb(220,40,60) test frame, the round trip came back (231,73,86) — the
    // brand rose visibly off. Tagged, it survives.
    AVVideoColorPropertiesKey: [
        AVVideoColorPrimariesKey: AVVideoColorPrimaries_ITU_R_709_2,
        AVVideoTransferFunctionKey: AVVideoTransferFunction_ITU_R_709_2,
        AVVideoYCbCrMatrixKey: AVVideoYCbCrMatrix_ITU_R_709_2,
    ],
    AVVideoCompressionPropertiesKey: [
        AVVideoAverageBitRateKey: bitrate,
        AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
        AVVideoAllowFrameReorderingKey: true,
        // A keyframe every second keeps scrubbing responsive in the Instagram
        // and WhatsApp players without costing much size on near-still footage.
        AVVideoMaxKeyFrameIntervalKey: Int(fps),
        AVVideoExpectedSourceFrameRateKey: Int(fps),
    ],
]

guard writer.canApply(outputSettings: videoSettings, forMediaType: .video) else {
    fail("this machine's H.264 encoder rejected \(options.width)x\(options.height) at \(bitrate) bps")
}

let input = AVAssetWriterInput(mediaType: .video, outputSettings: videoSettings)
input.expectsMediaDataInRealTime = false

let adaptor = AVAssetWriterInputPixelBufferAdaptor(
    assetWriterInput: input,
    sourcePixelBufferAttributes: [
        // BGRA in, yuv420 out: the encoder does the conversion, and CoreGraphics
        // can draw straight into a BGRA buffer with no intermediate copy.
        kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA),
        kCVPixelBufferWidthKey as String: options.width,
        kCVPixelBufferHeightKey as String: options.height,
        kCVPixelBufferCGImageCompatibilityKey as String: true,
        kCVPixelBufferCGBitmapContextCompatibilityKey as String: true,
    ]
)

guard writer.canAdd(input) else { fail("the writer refused the video input") }
writer.add(input)

guard writer.startWriting() else {
    fail("could not start writing: \(writer.error?.localizedDescription ?? "unknown error")")
}
writer.startSession(atSourceTime: .zero)

// MARK: - Compositing

// sRGB explicitly, NOT CGColorSpaceCreateDeviceRGB(): "device RGB" resolves to
// the host's display profile, and on a P3 Mac drawing an sRGB PNG into it
// colour-converts. Measured: a flat rgb(220,40,60) frame came out rgb(229,64,76)
// before the video encoder was even involved — the brand rose, visibly wrong.
let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
let bitmapInfo = CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue

/// Ease in/out so neither the zoom nor the fade starts or stops with a jerk.
func smoothstep(_ t: Double) -> Double {
    let x = min(max(t, 0), 1)
    return x * x * (3 - 2 * x)
}

/// Scale for slide `index` at progress `p` through its own time on screen.
/// Odd slides zoom out instead of in, so a carousel breathes rather than pumps.
func kenBurnsScale(index: Int, progress: Double) -> Double {
    let eased = smoothstep(progress)
    let zoom = options.zoom
    return index % 2 == 0 ? 1.0 + (zoom - 1.0) * eased : zoom - (zoom - 1.0) * eased
}

var currentFrame = 0

func draw(slide index: Int, alpha: Double, into context: CGContext) {
    let first = index * stride
    // Progress across the slide's own window, clamped for the tail slide.
    let denominator = Double(max(slideFrames - 1, 1))
    let progress = Double(currentFrame - first) / denominator
    let scale = kenBurnsScale(index: index, progress: progress)

    let w = Double(options.width) * scale
    let h = Double(options.height) * scale
    // Uniform scale of an image already at the canvas aspect ratio, centred:
    // the frame is always fully covered and nothing is stretched.
    let rect = CGRect(
        x: (Double(options.width) - w) / 2.0,
        y: (Double(options.height) - h) / 2.0,
        width: w,
        height: h
    )
    context.saveGState()
    context.setAlpha(CGFloat(alpha))
    context.interpolationQuality = .high
    context.draw(images[index], in: rect)
    context.restoreGState()
}

func renderFrame(into buffer: CVPixelBuffer) {
    CVPixelBufferLockBaseAddress(buffer, [])
    defer { CVPixelBufferUnlockBaseAddress(buffer, []) }

    guard let base = CVPixelBufferGetBaseAddress(buffer),
          let context = CGContext(
            data: base,
            width: options.width,
            height: options.height,
            bitsPerComponent: 8,
            bytesPerRow: CVPixelBufferGetBytesPerRow(buffer),
            space: colorSpace,
            bitmapInfo: bitmapInfo
          ) else {
        fail("could not wrap the pixel buffer in a CoreGraphics context")
    }

    // Recycled pool buffers keep the previous frame; start from opaque black so
    // a partly transparent PNG never smears the slide before it.
    context.setFillColor(CGColor(red: 0, green: 0, blue: 0, alpha: 1))
    context.fill(CGRect(x: 0, y: 0, width: options.width, height: options.height))

    // The slide whose window contains this frame, latest first.
    let index = min(max(currentFrame / max(stride, 1), 0), slideCount - 1)
    let previous = index - 1

    // Inside the overlap the outgoing slide is still on screen: paint it first,
    // then the incoming one at the fade's alpha. Source-over gives exactly
    // (1 - a) * previous + a * current, which is the cross-fade.
    if fadeFrames > 0, previous >= 0, currentFrame < previous * stride + slideFrames {
        let into = currentFrame - index * stride            // 0 ..< fadeFrames
        let alpha = smoothstep(Double(into + 1) / Double(fadeFrames + 1))
        draw(slide: previous, alpha: 1.0, into: context)
        draw(slide: index, alpha: alpha, into: context)
    } else {
        draw(slide: index, alpha: 1.0, into: context)
    }

    context.flush()
}

// MARK: - Encode

while currentFrame < totalFrames {
    guard writer.status == .writing else {
        fail("the writer stopped at frame \(currentFrame): \(writer.error?.localizedDescription ?? "unknown error")")
    }
    // The input drains on the writer's own queue; poll rather than hand it a
    // callback, so this stays a straight-line script with no run loop.
    if !input.isReadyForMoreMediaData {
        usleep(2000)
        continue
    }

    guard let pool = adaptor.pixelBufferPool else {
        fail("the pixel buffer pool was not available")
    }
    var maybeBuffer: CVPixelBuffer?
    let status = CVPixelBufferPoolCreatePixelBuffer(kCFAllocatorDefault, pool, &maybeBuffer)
    guard status == kCVReturnSuccess, let buffer = maybeBuffer else {
        fail("could not allocate a pixel buffer for frame \(currentFrame) (CVReturn \(status))")
    }

    renderFrame(into: buffer)

    // Pin the source colorimetry to match the track's tags, so VideoToolbox has
    // nothing to guess about and inserts no conversion of its own.
    CVBufferSetAttachment(buffer, kCVImageBufferColorPrimariesKey,
                          kCVImageBufferColorPrimaries_ITU_R_709_2, .shouldPropagate)
    CVBufferSetAttachment(buffer, kCVImageBufferTransferFunctionKey,
                          kCVImageBufferTransferFunction_ITU_R_709_2, .shouldPropagate)
    CVBufferSetAttachment(buffer, kCVImageBufferYCbCrMatrixKey,
                          kCVImageBufferYCbCrMatrix_ITU_R_709_2, .shouldPropagate)

    let time = CMTime(value: CMTimeValue(currentFrame), timescale: fps)
    guard adaptor.append(buffer, withPresentationTime: time) else {
        fail("frame \(currentFrame) was rejected: \(writer.error?.localizedDescription ?? "unknown error")")
    }
    currentFrame += 1
}

input.markAsFinished()
// endSession one frame past the last one, so the final frame gets its full
// display time and the asset duration is exactly totalFrames / fps.
writer.endSession(atSourceTime: CMTime(value: CMTimeValue(totalFrames), timescale: fps))

let finished = DispatchSemaphore(value: 0)
writer.finishWriting { finished.signal() }
finished.wait()

guard writer.status == .completed else {
    fail("the MP4 did not finish: \(writer.error?.localizedDescription ?? "status \(writer.status.rawValue)")")
}

// MARK: - Report

let size = (try? FileManager.default.attributesOfItem(atPath: outputURL.path)[.size] as? Int) ?? nil
guard let byteCount = size, byteCount > 0 else {
    fail("the writer reported success but \(outputURL.path) is empty")
}

let megabytes = Double(byteCount) / 1_048_576.0
print("""
wrote \(outputURL.path)
  \(options.width)x\(options.height) H.264, \(fps) fps, no audio
  \(slideCount) slide\(slideCount == 1 ? "" : "s"), \(totalFrames) frames, \
\(String(format: "%.2f", duration))s\
\(fadeFrames > 0 ? ", \(String(format: "%.2f", Double(fadeFrames) / Double(fps)))s cross-fade" : "")
  \(String(format: "%.2f", megabytes)) MB (\(byteCount) bytes) at ~\(bitrate / 1_000_000) Mbps
""")
