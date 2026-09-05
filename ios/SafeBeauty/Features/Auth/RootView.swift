import SwiftUI
import SafeBeautyCore

/// The first screen, and for now a deliberate one: it proves the stack is real
/// rather than showing a placeholder that proves nothing.
///
/// It exercises the two things that would otherwise be discovered late — that
/// the shared Core package is linked and its derivation runs on-device, and
/// that the layout is right-to-left because the app is Dari-first, not because
/// a particular view remembered to ask.
struct RootView: View {
    @State private var language = AppLanguage.current
    @State private var coreCheck: String = "…"

    var body: some View {
        ZStack {
            Brand.cream.ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer()

                Circle()
                    .fill(Brand.gradient)
                    .frame(width: 96, height: 96)
                    .overlay(
                        Text("SB")
                            .font(Brand.font(34, .bold))
                            .foregroundStyle(.white)
                    )

                Text("SafeBeauty")
                    .font(Brand.font(30, .bold))
                    .foregroundStyle(Brand.ink)

                Text(verbatim: language.endonym)
                    .font(Brand.font(16))
                    .foregroundStyle(Brand.accent)

                // The parity check, on-device rather than only in a test on a
                // Mac. If CommonCrypto behaves differently on the simulator or
                // a real phone than it does under `swift test`, this says so on
                // the first screen instead of at someone's first login.
                Text(verbatim: coreCheck)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(coreCheck.hasPrefix("✓") ? Brand.deep : .red)
                    .padding(.horizontal, 24)
                    .multilineTextAlignment(.center)

                Spacer()

                Picker("", selection: $language) {
                    ForEach(AppLanguage.allCases) { lang in
                        Text(verbatim: lang.endonym).tag(lang)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 32)
                .padding(.bottom, 40)
                .onChange(of: language) { _, new in AppLanguage.current = new }
            }
        }
        .environment(\.layoutDirection, language.layoutDirection)
        .task { coreCheck = Self.verifyCore() }
    }

    /// Derives a known vector and compares it to the value Android and the web
    /// console produce for the same input.
    private static func verifyCore() -> String {
        let expected = "srK0bug0SqF5wWEw8eXgmev4lKFS7HEArhteCPRD9y8="
        do {
            let got = try PinHasher.deriveAuthPassword("142857", saltBase64: "c2FsdHNhbHRzYWx0c2Fs")
            return got == expected
                ? "✓ crypto parity with Android"
                : "✗ MISMATCH — logins would fail\n\(got)"
        } catch {
            return "✗ derivation threw: \(error)"
        }
    }
}

#Preview { RootView() }
