import SwiftUI
import SafeBeautyCore

/// The three cards a first-time user sees before the sign-in screen.
///
/// Android has shown these since the beginning and iOS opened straight on a
/// phone-number field — which asks a woman to hand over her number before
/// anything has told her what the app is for. The copy is Android's word for
/// word: the promise made on one phone should be the promise made on the other.
struct OnboardingView: View {
    let onDone: () -> Void

    @State private var page = 0
    @State private var lang = LanguageStore.shared

    private struct Card { let icon: String; let title: L; let subtitle: L }

    private let cards = [
        Card(icon: "magnifyingglass", title: .onboardingTitle1, subtitle: .onboardingSubtitle1),
        Card(icon: "calendar.badge.clock", title: .onboardingTitle2, subtitle: .onboardingSubtitle2),
        Card(icon: "checkmark.shield.fill", title: .onboardingTitle3, subtitle: .onboardingSubtitle3),
    ]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(L.onboardingSkip.t) { onDone() }
                    .font(Brand.font(14.5, .medium))
                    .foregroundStyle(Brand.accent)
                Spacer()
            }
            .padding(.horizontal, 22).padding(.top, 10)

            TabView(selection: $page) {
                ForEach(cards.indices, id: \.self) { i in
                    VStack(spacing: 18) {
                        Spacer()
                        ZStack {
                            Circle().fill(Brand.gradient).frame(width: 108, height: 108)
                            Image(systemName: cards[i].icon)
                                .font(.system(size: 42)).foregroundStyle(.white)
                        }
                        Text(cards[i].title.t)
                            .font(Brand.font(22, .bold))
                            .foregroundStyle(Brand.ink)
                            .multilineTextAlignment(.center)
                        Text(cards[i].subtitle.t)
                            .font(Brand.font(15))
                            .foregroundStyle(Brand.accent)
                            .multilineTextAlignment(.center)
                        Spacer()
                    }
                    .padding(.horizontal, 34)
                    .tag(i)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))

            BrandButton(title: page == cards.count - 1 ? .onboardingGetStarted : .onboardingNext) {
                if page == cards.count - 1 { onDone() }
                else { withAnimation { page += 1 } }
            }
            .padding(.horizontal, 34).padding(.bottom, 26)

            // The language picker is here too, and deliberately. A woman whose
            // phone is set to Urdu lands on Dari by default, and the first
            // screen is where she needs to be able to change that — not four
            // taps into an account she has not made yet.
            @Bindable var lang = lang
            Picker("", selection: $lang.current) {
                ForEach(AppLanguage.allCases) { Text(verbatim: $0.endonym).tag($0) }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 34).padding(.bottom, 22)
        }
        .background(Brand.cream.ignoresSafeArea())
    }
}

/// Whether she has been shown it. Local to the device, like Android's.
enum OnboardingState {
    private static let key = "safebeauty.onboarding.seen"
    static var seen: Bool {
        get { UserDefaults.standard.bool(forKey: key) }
        set { UserDefaults.standard.set(newValue, forKey: key) }
    }
}
