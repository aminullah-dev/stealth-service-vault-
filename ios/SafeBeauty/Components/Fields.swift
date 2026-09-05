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
            .multilineTextAlignment(isPhone || isEmail ? .leading : (AppLanguage.current.layoutDirection == .rightToLeft ? .trailing : .leading))
            .padding(.horizontal, 14)
            .padding(.vertical, 13)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(focused ? Brand.accent : Brand.petal.opacity(0.55),
                                          lineWidth: focused ? 1.6 : 1)
                    )
            )
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

/// An error the user can read, in their language, with room to be specific.
struct ErrorBanner: View {
    let message: String?

    var body: some View {
        if let message, !message.isEmpty {
            Text(message)
                .font(Brand.font(13))
                .foregroundStyle(Color(hex: 0xC0392B))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(Color(hex: 0xC0392B).opacity(0.09),
                            in: RoundedRectangle(cornerRadius: 10))
                .transition(.opacity)
        }
    }
}
