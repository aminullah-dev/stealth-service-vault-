import SwiftUI

/// The form pieces, in one place so a screen added later inherits the rules
/// rather than re-deriving them.
///
/// Two of those rules are not cosmetic. Arabic script is connected, so letter
/// spacing breaks the joins and makes a word look misspelled rather than
/// styled — tracking is applied only for English. And a phone field is always
/// left-to-right even inside a right-to-left screen, because "+93 700…" read
/// right-to-left puts the country code on the wrong end.

struct BrandField: View {
    let label: L
    @Binding var text: String
    var isSecure = false
    var isPhone = false
    var isEmail = false

    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label.t)
                .font(Brand.font(13, .medium))
                .foregroundStyle(Brand.ink.opacity(0.75))

            Group {
                if isSecure {
                    SecureField("", text: $text)
                } else {
                    TextField("", text: $text)
                }
            }
            .focused($focused)
            .font(Brand.font(16))
            .foregroundStyle(Brand.ink)
            .textInputAutocapitalization(isEmail || isPhone ? .never : .words)
            .autocorrectionDisabled(isEmail || isPhone)
            .keyboardType(isPhone ? .phonePad : (isEmail ? .emailAddress : .default))
            // A number is not prose: it reads left-to-right in every language,
            // and forcing that here is what keeps "+93" at the front.
            .environment(\.layoutDirection, isPhone || isEmail ? .leftToRight : AppLanguage.current.layoutDirection)
            // .leading in both directions, never .trailing. TextAlignment is
            // resolved AGAINST the layout direction set on the line above, so
            // asking for .trailing in an RTL field put the text on the physical
            // LEFT — the one place it must not be. .leading already means "the
            // side text starts on", which is what this wants in either script.
            .multilineTextAlignment(.leading)
            .padding(.horizontal, 14)
            .padding(.vertical, 13)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Brand.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(focused ? Brand.accent : Brand.petal.opacity(0.55),
                                          lineWidth: focused ? 1.6 : 1)
                    )
            )
        }
    }
}

/// A labelled choice from a fixed list, wearing BrandField's chrome.
///
/// A district is not free text: the app stores a stable key and every filter
/// compares keys, so a typed answer only matches by the server's fuzzy pass and
/// a typo never matches at all. The list is the only place a salon owner can
/// give an address the rest of the product can read back.
struct BrandPicker: View {
    let label: L
    /// The selected key, "" for nothing chosen yet.
    @Binding var selection: String
    /// Key and the label to show for it, in the order they should be offered.
    let options: [(key: String, title: String)]
    var isEnabled = true

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label.t)
                .font(Brand.font(13, .medium))
                .foregroundStyle(Brand.ink.opacity(0.75))

            Menu {
                ForEach(options, id: \.key) { option in
                    Button(option.title) { selection = option.key }
                }
            } label: {
                HStack {
                    Text(options.first { $0.key == selection }?.title ?? L.selectOne.t)
                        .font(Brand.font(16))
                        // Grey until something is chosen, so an untouched picker
                        // does not read as an answer already given.
                        .foregroundStyle(selection.isEmpty ? Brand.ink.opacity(0.45) : Brand.ink)
                        .lineLimit(1)
                    Spacer(minLength: 8)
                    Image(systemName: "chevron.down")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Brand.accent)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 14)
                .padding(.vertical, 13)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Brand.surface)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .strokeBorder(Brand.petal.opacity(0.55), lineWidth: 1)
                        )
                )
            }
            .disabled(!isEnabled || options.isEmpty)
            .opacity(isEnabled ? 1 : 0.5)
        }
    }
}

struct BrandButton: View {
    let title: L
    var isLoading = false
    var isEnabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                // The label stays in the layout while loading rather than being
                // replaced, so the button does not change size and move what is
                // underneath it out from under the user's thumb.
                Text(title.t)
                    .font(Brand.font(16, .bold))
                    .opacity(isLoading ? 0 : 1)
                if isLoading { ProgressView().tint(.white) }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 15)
            .background(isEnabled ? AnyShapeStyle(Brand.gradient)
                                  : AnyShapeStyle(Brand.petal.opacity(0.45)))
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .disabled(!isEnabled || isLoading)
    }
}

/// Something the user can read, in their language, with room to be specific.
///
/// The tone is a separate axis from the text because the app learned it the
/// hard way: "your account was created, sign in" was routed through the only
/// banner that existed, which is alarm red, and read as a failure — sending
/// her back to register, which is the one thing that message exists to prevent.
/// Good news in the failure's clothes is worse than no news.
struct ErrorBanner: View {
    let message: String?
    var tone: Tone = .error

    enum Tone {
        case error, notice

        /// The brand is entirely rose and gold, which is the problem: the first
        /// attempt at this used Brand.deep for the notice, and at a 9% tint over
        /// cream the two banners came out (243,229,234) and (249,230,232) — six
        /// units apart in red, one in green, two in blue. Gold is no better
        /// (within ten of the error). Any hue in this palette collapses at that
        /// opacity, so the notice gets a green from outside it, and the tint is
        /// carried at full strength on a bar rather than washed across the box.
        @MainActor var ink: Color {
            switch self {
            case .error: Brand.danger
            case .notice: Brand.success
            }
        }
        var icon: String {
            switch self {
            case .error: "exclamationmark.triangle.fill"
            case .notice: "checkmark.circle.fill"
            }
        }
    }

    var body: some View {
        if let message, !message.isEmpty {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Image(systemName: tone.icon).font(.system(size: 12))
                Text(message).font(Brand.font(13))
            }
            .foregroundStyle(tone.ink)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(tone.ink.opacity(0.10),
                        in: RoundedRectangle(cornerRadius: 10))
            // A solid leading bar, so the two are told apart by shape and not
            // only by hue — the pair is read side by side on a cheap screen in
            // daylight, and both are shown at once when a sign-in then fails.
            // `leading` and not `left`: it must sit on the right in Dari.
            .overlay(alignment: .leading) {
                RoundedRectangle(cornerRadius: 2)
                    .fill(tone.ink)
                    .frame(width: 3)
                    .padding(.vertical, 8)
            }
            .transition(.opacity)
        }
    }
}
