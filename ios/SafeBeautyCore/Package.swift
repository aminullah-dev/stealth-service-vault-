// swift-tools-version: 6.0
import PackageDescription

// The logic half of the iOS app, kept as a package rather than folded into the
// app target so it builds and tests with `swift test` alone — no Xcode project,
// no simulator, no scheme. That is what makes this repository workable from
// VS Code: the parts that can be verified without a device are verified in a
// second, and the Xcode project exists only to put a UI around them.
let package = Package(
    name: "SafeBeautyCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SafeBeautyCore", targets: ["SafeBeautyCore"]),
    ],
    targets: [
        .target(name: "SafeBeautyCore"),
        .testTarget(name: "SafeBeautyCoreTests", dependencies: ["SafeBeautyCore"]),
    ]
)
