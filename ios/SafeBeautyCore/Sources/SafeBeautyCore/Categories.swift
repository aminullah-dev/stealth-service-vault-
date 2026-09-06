import Foundation

/// The category vocabulary the server writes onto every salon.
///
/// `functions/lib/categories.js` owns this list: it reads a salon's free-text
/// services and derives the `categories` array that the chips filter on. This is
/// the fourth copy — the JS, `DashboardViewModel.CATEGORY_KEYS`, the Firestore
/// index, and now iOS — so `CategoriesParityTests` checks it against the JS
/// rather than trusting four hand-written lists to stay level. A sixth category
/// added server-side and not here is a chip iPhone customers never get, and
/// nothing else would notice.
///
/// Keys, not labels. The stored value is "Hair"; what a customer reads is «مو»,
/// and the app target maps between them.
public enum Categories {
    public static let canonical = ["Hair", "Makeup", "Nails", "Skincare", "Eyebrows"]
}
