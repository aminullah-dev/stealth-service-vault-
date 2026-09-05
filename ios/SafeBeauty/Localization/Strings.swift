import Foundation

/// Every user-facing string, with a value in all three languages beside it.
///
/// Not a .xcstrings catalogue, deliberately. Android keeps these in
/// AppStrings.kt with the rule that a key exists in all three blocks or it is
/// not done, and that rule is enforceable by reading the file. A String
/// Catalogue is a binary-ish JSON blob that Xcode owns, is painful to review in
/// a diff, and is exactly the kind of file this project decided not to have
/// when it kept the Xcode project out of git. A missing translation here is
/// visible in the same line as the other two.
///
/// Dari is the reference. Where a Pashto line is doing real work it is written
/// in Pashto rather than translated from Dari — past-tense transitive verbs
/// agree with the object, not the subject, and a sentence that gets that wrong
/// reads as machine output to a native speaker.
struct L {
    let fa: String
    let ps: String
    let en: String

    var t: String {
        switch AppLanguage.current {
        case .dari: fa
        case .pashto: ps
        case .english: en
        }
    }
}

extension L {
    // MARK: Sign in
    static let signIn = L(fa: "ورود", ps: "ننوتل", en: "Sign in")
    static let phone = L(fa: "شماره تلفن", ps: "د تلیفون شمېره", en: "Phone number")
    static let password = L(fa: "رمز عبور", ps: "پټنوم", en: "Password")
    static let forgotPassword = L(fa: "رمز را فراموش کرده‌اید؟", ps: "پټنوم مو هېر شوی؟",
                                  en: "Forgot your password?")
    static let noAccountYet = L(fa: "حساب ندارید؟ ثبت‌نام کنید",
                                ps: "حساب نه لرئ؟ نوم لیکنه وکړئ",
                                en: "No account? Register")

    // MARK: Register
    static let register = L(fa: "ثبت‌نام", ps: "نوم لیکنه", en: "Register")
    static let fullName = L(fa: "نام", ps: "نوم", en: "Name")
    static let emailOptional = L(fa: "ایمیل (اختیاری)", ps: "بریښنالیک (اختیاري)",
                                 en: "Email (optional)")
    static let confirmPassword = L(fa: "تکرار رمز عبور", ps: "پټنوم بیا ولیکئ",
                                   en: "Repeat password")
    static let iAmASalon = L(fa: "صاحب سالون هستم", ps: "زه د سالون مالک یم",
                             en: "I own a salon")
    static let salonName = L(fa: "نام سالون", ps: "د سالون نوم", en: "Salon name")
    static let district = L(fa: "ناحیه", ps: "ناحیه", en: "District")
    static let haveAccount = L(fa: "حساب دارید؟ وارد شوید",
                               ps: "حساب لرئ؟ ننوځئ", en: "Have an account? Sign in")

    // MARK: Errors
    //
    // Each of these is a specific thing the person can do something about. The
    // generic one is last and is used only when nothing more useful is known —
    // the registration failure that told 99 people nothing is the reason this
    // list is not shorter.
    static let errWrongLogin = L(
        fa: "شماره یا رمز عبور درست نیست.",
        ps: "شمېره یا پټنوم سم نه دی.",
        en: "That phone number or password is not right.")
    static let errPhoneTaken = L(
        fa: "حسابی با این شماره از قبل وجود دارد.",
        ps: "په دې شمېره سره حساب لا دمخه شتون لري.",
        en: "An account with this phone number already exists.")
    static let errEmailTaken = L(
        fa: "حسابی با این ایمیل از قبل وجود دارد. وارد شوید، یا بدون ایمیل ثبت‌نام کنید.",
        ps: "په دې بریښنالیک سره حساب لا دمخه شتون لري. ننوځئ، یا پرته له بریښنالیکه نوم لیکنه وکړئ.",
        en: "An account with this email already exists. Sign in, or register without an email.")
    static let errSuspended = L(
        fa: "حساب شما معلق شده است. با پشتیبانی تماس بگیرید.",
        ps: "ستاسو حساب ځنډول شوی. له ملاتړ سره اړیکه ونیسئ.",
        en: "Your account is suspended. Please contact support.")
    static let errTooMany = L(
        fa: "تلاش‌های زیاد. کمی بعد دوباره امتحان کنید.",
        ps: "ډېرې هڅې. لږ وروسته بیا هڅه وکړئ.",
        en: "Too many attempts. Please try again shortly.")
    static let errNetwork = L(
        fa: "اتصال برقرار نشد. اینترنت خود را بررسی کنید.",
        ps: "اړیکه ونه نیول شوه. خپل انټرنټ وګورئ.",
        en: "Could not connect. Please check your internet.")

    // MARK: Validation
    static let errNameRequired = L(fa: "نام لازم است.", ps: "نوم اړین دی.", en: "Name is required.")
    static let errPhoneInvalid = L(
        fa: "شماره تلفن معتبر نیست.", ps: "د تلیفون شمېره سمه نه ده.",
        en: "That phone number is not valid.")
    static let errPasswordShort = L(
        fa: "رمز عبور باید حداقل ۶ نویسه باشد.",
        ps: "پټنوم باید لږ تر لږه ۶ تورې ولري.",
        en: "The password must be at least 6 characters.")
    static let errPasswordMismatch = L(
        fa: "دو رمز عبور یکی نیستند.", ps: "دواړه پټنومونه یو شان نه دي.",
        en: "The two passwords do not match.")
    static let errSalonNameRequired = L(
        fa: "نام سالون لازم است.", ps: "د سالون نوم اړین دی.", en: "Salon name is required.")

    // MARK: Salons
    static let salons = L(fa: "سالن‌ها", ps: "سالونونه", en: "Salons")
    static let noSalonsYet = L(
        fa: "هنوز سالونی در دسترس نیست.", ps: "تر اوسه هیڅ سالون شتون نلري.",
        en: "No salons are available yet.")
    static let couldNotLoad = L(
        fa: "بارگذاری ناموفق بود.", ps: "بارول ونه شول.", en: "Could not load.")
    static let retry = L(fa: "دوباره", ps: "بیا", en: "Retry")
    static let from = L(fa: "از", ps: "له", en: "from")
    static let afn = L(fa: "افغانی", ps: "افغانۍ", en: "AFN")
    static let verified = L(fa: "تأیید شده", ps: "تایید شوی", en: "Verified")

    // MARK: Common
    static let cancel = L(fa: "لغو", ps: "لغوه", en: "Cancel")
    static let signOut = L(fa: "خروج", ps: "وتل", en: "Sign out")
    static let pendingApproval = L(
        fa: "حساب شما در انتظار تأیید است.", ps: "ستاسو حساب د تایید په تمه دی.",
        en: "Your account is awaiting approval.")
}
