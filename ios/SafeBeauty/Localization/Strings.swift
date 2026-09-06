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
    /// Interpolating strings, written per language rather than assembled from
    /// fragments — word order differs and a sentence stitched from pieces reads
    /// as machine output in at least one of the three.
    static func reviewNameWarning(_ name: String) -> String {
        switch AppLanguage.current {
        case .dari: "نظر شما با نام «\(name)» برای همهٔ کاربران دیده می‌شود."
        case .pashto: "ستاسو نظر د «\(name)» په نوم ټولو کاروونکو ته ښکاري."
        case .english: "Your review will be shown to all users under the name \"\(name)\"."
        }
    }

    /// "۲ ارائه‌دهنده پیدا شد" — the line Android shows above the list and iOS
    /// did not. Here it counts what is on screen, which is the whole matching
    /// set: this list is one bounded read of at most 200 salons filtered on the
    /// device, not Android's page, so there is no page size to mistake for a
    /// total. Past 200 salons it would become a floor rather than a count, and
    /// the repository's limit is the thing to revisit then.
    static func providersFound(_ n: Int) -> String {
        switch AppLanguage.current {
        case .dari: "\(n) ارائه‌دهنده پیدا شد"
        case .pashto: "\(n) چمتو کوونکي وموندل شول"
        case .english: n == 1 ? "1 provider found" : "\(n) providers found"
        }
    }

    /// The max-price chips.
    ///
    /// Grouped like Android's `"%,d".format(p)`, but pinned to en_US rather
    /// than the device locale: `formatted()` on a phone set to Persian would
    /// give ۱٬۰۰۰ in Persian digits while the salon cards next to it say
    /// "از 80 افغانی" in Latin ones. The ≤ needs no help — the Afghani word
    /// beside it is strong RTL, so both platforms mirror the glyph the same.
    static func priceUnder(_ price: Int) -> String {
        let n = price.formatted(.number.grouping(.automatic)
            .locale(Locale(identifier: "en_US")))
        return switch AppLanguage.current {
        case .dari: "≤ \(n) افغانی"
        case .pashto: "≤ \(n) افغانۍ"
        case .english: "≤ \(n) AFN"
        }
    }

    /// The rating-floor chips: 3.0+, 4.0+, 4.5+.
    ///
    /// The RIGHT-TO-LEFT MARK is not decoration. "4.5+" holds no strong
    /// character, so SwiftUI resolves it as an LTR island and draws the plus on
    /// the right; read right-to-left that is "+4.5". Compose takes the
    /// paragraph direction from the layout instead, and Android puts the plus
    /// on the left — after the number, where an RTL reader looks for it. One
    /// invisible mark gives the string the strong character it lacks, and the
    /// two platforms then draw the same chip.
    ///
    /// The number is a literal rather than `formatted()` for the same reason as
    /// above: Android's is a literal, and a Persian locale would otherwise turn
    /// it into ۴٫۵.
    static func ratingAtLeast(_ value: String) -> String {
        switch AppLanguage.current {
        case .dari, .pashto: "\u{200F}\(value)+"
        case .english: "\(value)+"
        }
    }

    static func resetSentTo(_ email: String) -> String {
        switch AppLanguage.current {
        case .dari: "لینک بازنشانی به \(email) فرستاده شد. صندوق ورودی خود را ببینید."
        case .pashto: "د بیا تنظیم کولو لینک \(email) ته ولېږل شو. خپل صندوق وګورئ."
        case .english: "A reset link was sent to \(email). Please check your inbox."
        }
    }

    static func visitCount(_ n: Int) -> String {
        switch AppLanguage.current {
        case .dari: "(\(n) مراجعه)"
        case .pashto: "(\(n) مراجعې)"
        case .english: n == 1 ? "(1 visit)" : "(\(n) visits)"
        }
    }

    /// One sentence for VoiceOver, so the star glyph and the decimal are not
    /// read as separate, meaningless items.
    static func ratingLabel(_ rating: Double, _ visits: Int) -> String {
        let r = String(format: "%.1f", rating)
        return switch AppLanguage.current {
        case .dari: visits > 0 ? "امتیاز \(r) از ۵، \(visits) مراجعه" : "امتیاز \(r) از ۵"
        case .pashto: visits > 0 ? "\(r) له ۵ څخه، \(visits) مراجعې" : "\(r) له ۵ څخه"
        case .english: visits > 0 ? "Rated \(r) out of 5, \(visits) visits" : "Rated \(r) out of 5"
        }
    }

    static func pointsAvailable(_ n: Int) -> String {
        switch AppLanguage.current {
        case .dari: "شما \(n) امتیاز دارید."
        case .pashto: "تاسو \(n) امتیازه لرئ."
        case .english: "You have \(n) points."
        }
    }

    static func starsLabel(_ n: Int) -> String {
        switch AppLanguage.current {
        case .dari: "\(n) ستاره"
        case .pashto: "\(n) ستوري"
        case .english: n == 1 ? "1 star" : "\(n) stars"
        }
    }

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
    static let serviceName = L(fa: "نام خدمت", ps: "د خدمت نوم", en: "Service name")
    static let add = L(fa: "افزودن", ps: "زیاتول", en: "Add")
    // The Android values, verbatim: a salon owner who registered on one phone
    // and edits on the other should read the same words for the same field.
    static let city = L(fa: "شهر", ps: "ښار", en: "City")
    static let districtArea = L(fa: "ناحیه / منطقه", ps: "سیمه / ناحیه",
                                en: "District / Area")
    static let pickCityFirst = L(fa: "اول شهر را انتخاب کنید",
                                 ps: "لومړی ښار وټاکئ", en: "Choose a city first")
    static let selectOne = L(fa: "انتخاب کنید", ps: "وټاکئ", en: "Select")
    static let haveAccount = L(fa: "حساب دارید؟ وارد شوید",
                               ps: "حساب لرئ؟ ننوځئ", en: "Have an account? Sign in")

    // MARK: Errors
    //
    // Each of these is a specific thing the person can do something about. The
    // generic one is last and is used only when nothing more useful is known —
    // the registration failure that told 99 people nothing is the reason this
    // list is not shorter.
    // MARK: Forgot password
    static let forgotPasswordHelp = L(
        fa: "شمارهٔ خود را وارد کنید. اگر روی حسابتان ایمیل ثبت باشد، لینک بازنشانی به آن فرستاده می‌شود.",
        ps: "خپله شمېره ولیکئ. که ستاسو په حساب کې بریښنالیک ثبت وي، د بیا تنظیم کولو لینک ورته لېږل کیږي.",
        en: "Enter your phone number. If your account has an email address, we send a reset link to it.")
    static let sendResetLink = L(fa: "ارسال لینک بازنشانی", ps: "د بیا تنظیم کولو لینک لېږل",
                                 en: "Send reset link")
    static let resetNoEmail = L(
        fa: "روی این حساب ایمیلی ثبت نیست، پس لینکی نمی‌توان فرستاد. با پشتیبانی تماس بگیرید.",
        ps: "په دې حساب کې بریښنالیک نشته، نو لینک نه شي لېږل کېدای. له ملاتړ سره اړیکه ونیسئ.",
        en: "This account has no email address, so no link can be sent. Please contact support.")
    static let resetNoAccount = L(
        fa: "حسابی با این شماره پیدا نشد.", ps: "په دې شمېره سره حساب و نه موندل شو.",
        en: "No account was found with that phone number.")

    static let providerUseOtherApp = L(
        fa: "حساب سالون شما فعال است. مدیریت سالون فعلاً در اپ اندروید و کنسول وب انجام می‌شود.",
        ps: "ستاسو د سالون حساب فعال دی. د سالون مدیریت اوس مهال په اندرویډ اپ او ویب کنسول کې کیږي.",
        en: "Your salon account is active. Managing your salon is on the Android app and the web console for now.")
    static let noNotificationsHint = L(
        fa: "وقتی سالنی رزرو شما را تأیید یا لغو کند، اینجا خبر می‌شوید.",
        ps: "کله چې سالون ستاسو بکینګ تایید یا لغوه کړي، دلته به خبر شئ.",
        en: "When a salon confirms or cancels your booking, you will hear about it here.")
    static let noBookingsHint = L(
        fa: "از بخش سالن‌ها یک سالن انتخاب کنید و اولین نوبت خود را بگیرید.",
        ps: "د سالونونو له برخې یو سالون وټاکئ او خپل لومړی وخت ونیسئ.",
        en: "Pick a salon from the Salons tab and book your first appointment.")
    // MARK: Account
    static let changePassword = L(fa: "تغییر رمز عبور", ps: "د پټنوم بدلول", en: "Change password")
    static let currentPassword = L(fa: "رمز فعلی", ps: "اوسنی پټنوم", en: "Current password")
    static let newPassword = L(fa: "رمز جدید", ps: "نوی پټنوم", en: "New password")
    static let passwordRule = L(
        fa: "رمز جدید باید حداقل ۶ نویسه باشد.",
        ps: "نوی پټنوم باید لږ تر لږه ۶ تورې ولري.",
        en: "The new password must be at least 6 characters.")
    static let passwordChanged = L(
        fa: "رمز عبور شما عوض شد.", ps: "ستاسو پټنوم بدل شو.",
        en: "Your password has been changed.")
    static let wrongCurrentPassword = L(
        fa: "رمز فعلی درست نیست.", ps: "اوسنی پټنوم سم نه دی.",
        en: "That current password is not right.")
    static let editName = L(fa: "ویرایش نام", ps: "د نوم سمول", en: "Edit name")
    static let save = L(fa: "ذخیره", ps: "ساتل", en: "Save")
    static let redeem = L(fa: "تبدیل امتیاز", ps: "د امتیاز بدلول", en: "Redeem points")
    static let pointsToRedeem = L(fa: "چند امتیاز؟", ps: "څو امتیازه؟", en: "How many points?")
    static let redeemRule = L(
        fa: "هر ۱۰۰ امتیاز به ۱۰۰ افغانی اعتبار تبدیل می‌شود. مضربی از ۱۰۰ وارد کنید.",
        ps: "هر ۱۰۰ امتیاز په ۱۰۰ افغانیو کریډیټ بدلېږي. د ۱۰۰ مضرب ولیکئ.",
        en: "Every 100 points becomes 100 AFN of credit. Enter a multiple of 100.")

    static let messageSalon = L(fa: "پیام به سالن", ps: "سالون ته پیغام", en: "Message the salon")
    static let send = L(fa: "فرستادن", ps: "لېږل", en: "Send")
    static let chatFirstMessage = L(
        fa: "اولین پیام را بنویسید. سالن آن را در اپ خودش می‌بیند.",
        ps: "لومړی پیغام ولیکئ. سالون یې په خپل اپ کې ویني.",
        en: "Write the first message. The salon sees it in its own app.")
    // MARK: Appearance
    static let theme = L(fa: "رنگ برنامه", ps: "د اپ رنګ", en: "Theme")
    static let themeRose = L(fa: "رز", ps: "ګلابي", en: "Rose")
    static let themeLavender = L(fa: "اسطوخودوس", ps: "لاوندر", en: "Lavender")
    static let themeSage = L(fa: "مریم‌گلی", ps: "شنه", en: "Sage")
    static let themeOcean = L(fa: "اقیانوس", ps: "سمندر", en: "Ocean")
    static let themeHoney = L(fa: "عسلی", ps: "شاتيز", en: "Honey")
    static let themeMaroon = L(fa: "زرشکی", ps: "تور سور", en: "Maroon")
    static let appearanceSystem = L(fa: "خودکار", ps: "اتومات", en: "Auto")
    static let appearanceLight = L(fa: "روشن", ps: "روښانه", en: "Light")
    static let appearanceDark = L(fa: "تاریک", ps: "تیاره", en: "Dark")

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
    /// Says the account exists before it says anything else. The one thing she
    /// must not do here is register again, and that is what she will try if the
    /// message reads like a failure.
    static let errRegisteredNowSignIn = L(
        fa: "حساب شما ساخته شد. با شماره و رمز عبور خود وارد شوید.",
        ps: "ستاسو حساب جوړ شو. په خپله شمېره او پټنوم سره ننوځئ.",
        en: "Your account was created. Sign in with your phone number and password.")
    static let accountSuspended = L(
        fa: "حساب شما فعلاً معلق است. برای بررسی با پشتیبانی در تماس شوید.",
        ps: "ستاسو حساب اوس مهال ځنډول شوی. د کتنې لپاره له ملاتړ سره اړیکه ونیسئ.",
        en: "Your account is suspended for now. Contact support to have it reviewed.")
    static let errTooMany = L(
        fa: "تلاش‌های زیاد. کمی بعد دوباره امتحان کنید.",
        ps: "ډېرې هڅې. لږ وروسته بیا هڅه وکړئ.",
        en: "Too many attempts. Please try again shortly.")
    // The booking refusals, by the server's own reason code. These existed only
    // as English sentences written for a developer until the code was carried
    // through to the client.
    static let errSalonClosed = L(
        fa: "این سالن در آن روز بسته است. روز دیگری را انتخاب کنید.",
        ps: "دا سالون په هغه ورځ بند دی. بله ورځ وټاکئ.",
        en: "The salon is closed that day. Please choose another day.")
    static let errSalonUnavailable = L(
        fa: "این سالن فعلاً رزرو نمی‌پذیرد.",
        ps: "دا سالون اوس مهال بکینګ نه مني.",
        en: "This salon is not taking bookings right now.")
    static let errAfterClosing = L(
        fa: "این نوبت پیش از بسته شدن سالن تمام نمی‌شود. ساعت زودتری را انتخاب کنید.",
        ps: "دا نوبت د سالون له بندېدو مخکې نه پای ته رسېږي. مخکینی وخت وټاکئ.",
        en: "That appointment would not finish before the salon closes. Please pick an earlier time.")
    static let errStaffUnavailable = L(
        fa: "این آرایشگر در آن ساعت در دسترس نیست.",
        ps: "دا سینګارګره په هغه وخت شتون نه لري.",
        en: "That stylist is not available at that time.")
    static let errPromoLimit = L(
        fa: "این کد تخفیف دیگر قابل استفاده نیست.",
        ps: "دا د تخفیف کوډ نور نه کارول کېږي.",
        en: "That promo code can no longer be used.")
    static let errFreeUseCash = L(
        fa: "برای این رزرو پرداخت نقدی را انتخاب کنید.",
        ps: "د دې بکینګ لپاره نغدي تادیه وټاکئ.",
        en: "Please choose cash payment for this booking.")
    static let errNotBookable = L(
        fa: "این نوبت گرفته نشد. ساعت یا روز دیگری را امتحان کنید.",
        ps: "دا نوبت ونه نیول شو. بل وخت یا بله ورځ وآزمویئ.",
        en: "That booking could not be made. Please try another time or day.")
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
    static let errServicesRequired = L(
        fa: "حداقل یک خدمت اضافه کنید.", ps: "لږ تر لږه یوه خدمت اضافه کړئ.",
        en: "Add at least one service.")
    static let errDistrictRequired = L(
        fa: "ناحیه لازم است.", ps: "ناحیه اړینه ده.", en: "District is required.")
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

    // MARK: Booking
    static let chooseServices = L(fa: "خدمات را انتخاب کنید", ps: "خدمتونه وټاکئ",
                                  en: "Choose services")
    static let chooseDay = L(fa: "روز", ps: "ورځ", en: "Day")
    static let chooseTime = L(fa: "ساعت", ps: "ساعت", en: "Time")
    static let total = L(fa: "مجموع", ps: "ټول", en: "Total")
    static let book = L(fa: "رزرو", ps: "بکینګ", en: "Book")
    static let closedThatDay = L(
        fa: "این سالون آن روز بسته است.", ps: "دا سالون هغه ورځ بند دی.",
        en: "This salon is closed that day.")
    static let noTimesLeft = L(
        fa: "برای این روز وقت خالی نمانده.", ps: "د دې ورځې لپاره خالي وخت نشته.",
        en: "No times left for this day.")

    // MARK: Payment
    static let confirmBooking = L(fa: "تأیید رزرو", ps: "د بکینګ تایید", en: "Confirm booking")
    static let booked = L(fa: "رزرو شد", ps: "بکینګ وشو", en: "Booked")
    static let payment = L(fa: "پرداخت", ps: "تادیه", en: "Payment")
    static let payCash = L(fa: "نقد در سالون", ps: "په سالون کې نغد", en: "Cash at the salon")
    static let payOnline = L(fa: "آنلاین", ps: "آنلاین", en: "Online")
    static let promoCode = L(fa: "کد تخفیف (اختیاری)", ps: "د تخفیف کوډ (اختیاري)",
                             en: "Promo code (optional)")
    static let notesOptional = L(fa: "یادداشت (اختیاری)", ps: "یادښت (اختیاري)",
                                 en: "Note (optional)")
    static let estimate = L(fa: "برآورد", ps: "اټکل", en: "Estimate")
    // Said before she commits, not after. The server applies discounts this
    // screen cannot see, so promising a final number here would be a promise
    // the receipt breaks.
    static let estimateNote = L(
        fa: "مبلغ نهایی پس از تأیید محاسبه می‌شود — ممکن است با تخفیف کمتر شود.",
        ps: "وروستۍ اندازه د تایید وروسته محاسبه کیږي — کېدای شي په تخفیف سره کمه شي.",
        en: "The final amount is calculated after you confirm — a discount may lower it.")
    static let listPrice = L(fa: "قیمت", ps: "بیه", en: "Price")
    static let discount = L(fa: "تخفیف", ps: "تخفیف", en: "Discount")
    static let payNow = L(fa: "پرداخت", ps: "تادیه وکړئ", en: "Pay now")
    static let bookedCash = L(
        fa: "رزرو شما ثبت شد. مبلغ را در سالون بپردازید.",
        ps: "ستاسو بکینګ ثبت شو. پیسې په سالون کې ورکړئ.",
        en: "Your booking is confirmed. Pay at the salon.")
    static let payToConfirm = L(
        fa: "برای نهایی شدن رزرو، پرداخت را کامل کنید.",
        ps: "د بکینګ د بشپړولو لپاره تادیه بشپړه کړئ.",
        en: "Complete the payment to confirm your booking.")
    static let close = L(fa: "بستن", ps: "بندول", en: "Close")
    static let errSlotTaken = L(
        fa: "این وقت همین حالا گرفته شد. وقت دیگری انتخاب کنید.",
        ps: "دا وخت همدا اوس ونیول شو. بل وخت وټاکئ.",
        en: "That time was just taken. Please choose another.")
    static let errNeedsVerification = L(
        fa: "برای رزرو، اول هویت خود را تأیید کنید.",
        ps: "د بکینګ لپاره لومړی خپله پېژندنه تایید کړئ.",
        en: "Please verify your identity before booking.")

    // MARK: My bookings
    static let myBookings = L(fa: "رزروهای من", ps: "زما بکینګونه", en: "My bookings")
    static let upcoming = L(fa: "پیش رو", ps: "راتلونکي", en: "Upcoming")
    static let pastBookings = L(fa: "گذشته", ps: "تېر", en: "Past")
    static let noBookingsYet = L(
        fa: "هنوز رزروی ندارید.", ps: "تر اوسه مو بکینګ نشته.",
        en: "You have no bookings yet.")
    // Distinct from "you have none". A customer whose booking cannot be read
    // deserves to know one exists rather than to be told she has none — the
    // Android screen that blanked said nothing at all.
    static let someBookingsUnreadable = L(
        fa: "بعضی رزروها خوانده نشدند. با پشتیبانی تماس بگیرید.",
        ps: "ځینې بکینګونه ونه لوستل شول. له ملاتړ سره اړیکه ونیسئ.",
        en: "Some bookings could not be read. Please contact support.")
    static let cancelBooking = L(fa: "لغو رزرو", ps: "بکینګ لغوه کول", en: "Cancel booking")
    static let keepIt = L(fa: "بماند", ps: "پاتې دې شي", en: "Keep it")
    static let cancelWarning = L(
        fa: "این رزرو لغو می‌شود. اگر پرداخت کرده‌اید، مبلغ طبق قوانین بازگردانده می‌شود.",
        ps: "دا بکینګ لغوه کیږي. که مو تادیه کړې وي، پیسې د قواعدو سره سم بېرته درکول کیږي.",
        en: "This booking will be cancelled. If you paid, the amount is refunded under the rules.")
    static let statusAwaitingPayment = L(fa: "در انتظار پرداخت", ps: "د تادیې په تمه", en: "Awaiting payment")
    static let statusPending = L(fa: "در انتظار تأیید", ps: "د تایید په تمه", en: "Pending")
    static let statusConfirmed = L(fa: "تأیید شده", ps: "تایید شوی", en: "Confirmed")
    static let statusCompleted = L(fa: "انجام شده", ps: "ترسره شوی", en: "Completed")
    static let statusCancelled = L(fa: "لغو شده", ps: "لغوه شوی", en: "Cancelled")
    static let statusUnknown = L(fa: "نامشخص", ps: "نامعلوم", en: "Unknown")

    // MARK: Verification
    static let verifyIdentity = L(fa: "تأیید هویت", ps: "د پېژندنې تایید", en: "Verify identity")
    // Told before she is asked, in plain words, because what she is being
    // asked for is a photograph of her identity document.
    static let kycWhy = L(
        fa: "برای رزرو، سالون باید بداند با چه کسی قرار دارد. عکس تذکره و یک عکس از خودتان فقط برای تیم SafeBeauty دیده می‌شود — نه برای سالون و نه برای مشتریان دیگر.",
        ps: "د بکینګ لپاره، سالون باید پوه شي چې له چا سره یې وعده ده. د تذکرې انځور او ستاسو یو انځور یوازې د SafeBeauty ټیم ګوري — نه سالون او نه نور پیرودونکي.",
        en: "To book, the salon needs to know who it is expecting. Your tazkira photo and a photo of you are seen only by the SafeBeauty team — not by the salon and not by other customers.")
    static let tazkiraPhoto = L(fa: "عکس تذکره", ps: "د تذکرې انځور", en: "Tazkira photo")
    static let selfiePhoto = L(fa: "عکس خودتان", ps: "ستاسو انځور", en: "Photo of you")
    static let choosePhoto = L(fa: "انتخاب عکس", ps: "انځور وټاکئ", en: "Choose a photo")
    static let tazkiraNumber = L(fa: "شماره تذکره", ps: "د تذکرې شمېره", en: "Tazkira number")
    static let province = L(fa: "ولایت", ps: "ولایت", en: "Province")
    static let addressDetail = L(fa: "آدرس", ps: "پته", en: "Address")
    static let submitVerification = L(fa: "ارسال برای تأیید", ps: "د تایید لپاره لېږل",
                                      en: "Submit for verification")
    static let kycPreparing = L(fa: "آماده‌سازی عکس‌ها…", ps: "د انځورونو چمتو کول…",
                                en: "Preparing photos…")
    static let kycUploading = L(fa: "در حال ارسال عکس‌ها…", ps: "انځورونه لېږل کیږي…",
                                en: "Uploading photos…")
    static let kycSubmitting = L(fa: "در حال ثبت…", ps: "ثبتېږي…", en: "Submitting…")
    static let kycSubmitted = L(
        fa: "مدارک شما ارسال شد. پس از بررسی خبرتان می‌کنیم — معمولاً در یک روز کاری.",
        ps: "ستاسو اسناد ولېږل شول. د کتنې وروسته به مو خبر کړو — معمولاً په یوه کاري ورځ کې.",
        en: "Your documents were sent. We will let you know after review — usually within one working day.")
    static let kycErrTooLarge = L(
        fa: "عکس خیلی بزرگ است. عکس دیگری انتخاب کنید.",
        ps: "انځور ډېر لوی دی. بل انځور وټاکئ.",
        en: "That photo is too large. Please choose another.")
    static let kycErrUpload = L(
        fa: "ارسال عکس ناموفق بود. اتصال خود را بررسی کنید.",
        ps: "د انځور لېږل ونه شول. خپله اړیکه وګورئ.",
        en: "The upload failed. Please check your connection.")
    static let kycErrUnderReview = L(
        fa: "مدارک شما قبلاً ارسال شده و در حال بررسی است.",
        ps: "ستاسو اسناد لا دمخه لېږل شوي او تر کتنې لاندې دي.",
        en: "Your documents are already submitted and under review.")
    static let kycErrAlreadyVerified = L(
        fa: "هویت شما از قبل تأیید شده است.", ps: "ستاسو پېژندنه لا دمخه تایید شوې.",
        en: "Your identity is already verified.")
    static let kycErrMissing = L(
        fa: "شماره تذکره و آدرس لازم است.", ps: "د تذکرې شمېره او پته اړینې دي.",
        en: "The tazkira number and address are required.")
    static let verifyToBook = L(
        fa: "برای رزرو، اول هویت خود را تأیید کنید.",
        ps: "د بکینګ لپاره لومړی خپله پېژندنه تایید کړئ.",
        en: "Verify your identity before booking.")

    // MARK: Notifications & profile
    static let notifications = L(fa: "اعلان‌ها", ps: "خبرتیاوې", en: "Notifications")
    static let noNotifications = L(fa: "اعلانی ندارید.", ps: "خبرتیا نلرئ.",
                                   en: "You have no notifications.")
    static let markAllRead = L(fa: "همه خوانده شد", ps: "ټول ولوستل شول",
                               en: "Mark all read")
    static let profile = L(fa: "حساب من", ps: "زما حساب", en: "My account")
    static let walletCredit = L(fa: "اعتبار", ps: "اعتبار", en: "Credit")
    static let loyaltyPoints = L(fa: "امتیاز", ps: "ټکي", en: "Points")
    static let yourInviteCode = L(fa: "کد دعوت شما", ps: "ستاسو د بلنې کوډ",
                                  en: "Your invite code")
    static let inviteExplain = L(
        fa: "این کد را به دوستتان بدهید. وقتی هویتش تأیید شد، هر دو اعتبار می‌گیرید.",
        ps: "دا کوډ خپل ملګري ته ورکړئ. کله چې د هغې پېژندنه تایید شي، دواړه اعتبار ترلاسه کوئ.",
        en: "Give this code to a friend. When her identity is verified, you both get credit.")
    static let kycApproved = L(fa: "تأیید شده", ps: "تایید شوی", en: "Verified")
    static let kycPending = L(fa: "در حال بررسی", ps: "تر کتنې لاندې", en: "Under review")
    static let kycRejected = L(fa: "رد شده — دوباره بفرستید", ps: "رد شوی — بیا یې ولېږئ",
                               en: "Rejected — send again")
    static let kycNone = L(fa: "تأیید نشده", ps: "تایید شوی نه دی", en: "Not verified")
    static let signOutWarning = L(
        fa: "از حساب خارج می‌شوید. برای ورود دوباره به شماره و رمز نیاز دارید.",
        ps: "له حساب څخه وځئ. د بیا ننوتلو لپاره شمېرې او پټنوم ته اړتیا لرئ.",
        en: "You will be signed out. You will need your phone number and password to sign in again.")

    // MARK: Reviews
    static let writeReview = L(fa: "نظر شما", ps: "ستاسو نظر", en: "Your review")
    static let reviewComment = L(fa: "نظرتان (اختیاری)", ps: "ستاسو نظر (اختیاري)",
                                 en: "Your comment (optional)")
    static let sendReview = L(fa: "ارسال نظر", ps: "نظر لېږل", en: "Send review")
    static let reviews = L(fa: "نظرات", ps: "نظرونه", en: "Reviews")
    static let noReviewsYet = L(fa: "هنوز نظری ثبت نشده.", ps: "تر اوسه نظر نشته.",
                                en: "No reviews yet.")
    static let reviewThanks = L(
        fa: "نظر شما ثبت شد. ممنون — همین‌ها به زن بعدی کمک می‌کند انتخاب کند.",
        ps: "ستاسو نظر ثبت شو. مننه — همدا شی بلې ښځې سره د ټاکلو کې مرسته کوي.",
        en: "Your review was posted. Thank you — this is what helps the next woman choose.")
    static let errAlreadyReviewed = L(
        fa: "برای این نوبت قبلاً نظر داده‌اید.", ps: "د دې وخت لپاره مو مخکې نظر ورکړی.",
        en: "You have already reviewed this booking.")
    static let errReviewTooEarly = L(
        fa: "بعد از نوبت‌تان می‌توانید نظر بدهید.", ps: "د خپل وخت وروسته کولی شئ نظر ورکړئ.",
        en: "You can review after your visit.")
    static let errNotYourBooking = L(
        fa: "این نوبت شما نیست.", ps: "دا ستاسو وخت نه دی.", en: "That is not your booking.")

    // MARK: Search & support
    static let searchSalons = L(fa: "جستجوی سالن یا خدمات", ps: "د سالون یا خدمتونو لټون",
                                en: "Search salons or services")
    static let allCities = L(fa: "همه شهرها", ps: "ټول ښارونه", en: "All cities")
    static let favorites = L(fa: "علاقه‌مندی‌ها", ps: "خوښې", en: "Favorites")
    static let reschedule = L(fa: "تغییر زمان", ps: "د وخت بدلون", en: "Change time")

    // MARK: Provider — the salon owner's side
    // Tab names taken verbatim from Android's AppStrings, so an owner who uses
    // both phones reads the same words for the same screen.
    static let tabRequests = L(fa: "درخواست‌ها", ps: "غوښتنې", en: "Requests")
    static let tabCalendar = L(fa: "تقویم", ps: "کلنډر", en: "Calendar")
    static let tabIncome = L(fa: "درآمد", ps: "عاید", en: "Income")
    static let tabMyProfile = L(fa: "پروفایل من", ps: "زما پروفایل", en: "My Profile")
    static let accept = L(fa: "پذیرفتن", ps: "منل", en: "Accept")
    static let decline = L(fa: "رد کردن", ps: "ردول", en: "Decline")
    static let declineConfirm = L(
        fa: "این نوبت رد شود؟ اگر مشتری پرداخت کرده باشد، پول کامل برگردانده می‌شود.",
        ps: "دا نوبت رد شي؟ که پیرودونکي تادیه کړې وي، ټولې پیسې بیرته ورکول کیږي.",
        en: "Decline this booking? If the customer has paid, she is refunded in full.")
    static let noRequests = L(fa: "درخواست تازه‌ای نیست.", ps: "نوې غوښتنه نشته.",
                              en: "No new requests.")
    static let noUpcoming = L(fa: "نوبتی در پیش نیست.", ps: "راتلونکی نوبت نشته.",
                              en: "Nothing coming up.")
    // The provider console's own two sentences, verbatim. The sign is the whole
    // meaning here — commission.js says positive means the platform owes the
    // salon and negative means the salon owes the platform — so both readings
    // exist and the figure is always shown as a magnitude.
    static let platformOwesYou = L(fa: "پلتفرم به شما بدهکار است",
                                   ps: "پلیټ‌فارم تاسو ته پوروړی دی",
                                   en: "Platform owes you")
    static let youOweCommission = L(fa: "شما به پلتفرم بدهکارید (کمیسیون نقدی)",
                                    ps: "تاسو پلیټ‌فارم ته پوروړي یاست (نغدي کمیشن)",
                                    en: "You owe the platform (cash commission)")
    static let balanceSettled = L(fa: "حساب شما تسویه است.", ps: "ستاسو حساب تصفیه دی.",
                                  en: "Your balance is settled.")
    static let owedExplain = L(
        fa: "کمیسیون نوبت‌های نقدی. پس از تسویه، ادمین آن را صفر می‌کند.",
        ps: "د نغدو نوبتونو کمیشن. له تصفیې وروسته، اډمین یې صفر کوي.",
        en: "Commission on cash bookings. An admin clears it once you settle.")
    static func earnedExcludes(_ count: Int) -> String {
        switch AppLanguage.current {
        case .dari: "\(count) نوبت قیمت ثبت‌شده ندارد و در این مجموع نیامده."
        case .pashto: "\(count) نوبت ثبت شوې بیه نه لري او په دې ټوله کې نه دی راغلی."
        case .english: "\(count) visits have no recorded price and are not in this total."
        }
    }
    static let completedVisits = L(fa: "نوبت‌های انجام‌شده", ps: "ترسره شوي نوبتونه",
                                   en: "Completed visits")
    static let earnedTotal = L(fa: "مجموع دریافتی", ps: "ټوله ترلاسه شوې", en: "Total taken")
    static let awaitingYou = L(fa: "منتظر پاسخ شما", ps: "ستاسو د ځواب په تمه",
                               en: "Waiting for you")
    static func lastMinuteOff(_ percent: Int) -> String {
        switch AppLanguage.current {
        case .dari: "٪\(percent) تخفیف لحظه‌آخری روی ساعت‌های نشان‌دار"
        case .pashto: "پر نښه شوو ساعتونو \(percent)٪ د وروستي وخت تخفیف"
        case .english: "\(percent)% off the marked times — booking soon"
        }
    }
    static let salonWork = L(fa: "نمونه کارها", ps: "د کار بېلګې", en: "Their work")

    static let recommendedTitle = L(fa: "پیشنهاد برای شما", ps: "ستاسو لپاره وړاندیز",
                                    en: "Recommended for You")
    static let recommendedSubtitle = L(fa: "بر اساس رزروهای شما",
                                       ps: "ستاسو د بکینګونو له مخې",
                                       en: "Based on your bookings")

    // MARK: Group bookings — Android's strings verbatim
    static let groupBooking = L(fa: "رزرو گروهی / عروسی", ps: "ډله‌ییز / د واده بکینګ",
                                en: "Group / event booking")
    static let addGuest = L(fa: "افزودن مهمان", ps: "میلمه اضافه کړئ", en: "Add guest")
    static let guestName = L(fa: "نام مهمان", ps: "د میلمه نوم", en: "Guest name")
    static let partyPrepay = L(
        fa: "رزرو گروهی از پیش پرداخت می‌شود. سالن تمام تیمش را برای شما کنار می‌گذارد، پس آن روز چوکی‌ای برای کس دیگری ندارد.",
        ps: "ډله ییز بکنګ مخکې ورکړل کیږي. سالون ټوله ډله ستاسو لپاره ځانګړې کوي، نو هغه ورځ بل چا ته څوکۍ نه لري.",
        en: "A group booking is paid in advance. The salon sets aside its whole team for you, so it is not a chair they can offer anyone else that day.")
    static func guestNumber(_ n: Int) -> String {
        switch AppLanguage.current {
        case .dari: "مهمان \(n)"
        case .pashto: "میلمه \(n)"
        case .english: "Guest \(n)"
        }
    }
    static let errPartyEmpty = L(fa: "برای هر مهمان حداقل یک خدمت انتخاب کنید.",
                                 ps: "د هر میلمه لپاره لږ تر لږه یو خدمت وټاکئ.",
                                 en: "Choose at least one service for each guest.")

    // MARK: Choosing a stylist
    static let chooseStaff = L(fa: "آرایشگر را انتخاب کنید", ps: "آرایشګر وټاکئ",
                               en: "Choose a stylist")
    static let staffAny = L(fa: "هر کدام که در دسترس است", ps: "هر یو چې شتون ولري",
                            en: "Any available")

    // MARK: Coming back from HesabPay
    static let paymentConfirmed = L(fa: "پرداخت شما تأیید شد.", ps: "ستاسو تادیه تایید شوه.",
                                    en: "Your payment went through.")
    static let paymentFailed = L(fa: "پرداخت انجام نشد. مبلغی کسر نشده است.",
                                 ps: "تادیه ترسره نه شوه. هیڅ پیسې نه دي کمې شوې.",
                                 en: "The payment did not go through. Nothing was charged.")
    static let paymentWaiting = L(fa: "منتظر تأیید پرداخت…", ps: "د تادیې تایید ته انتظار…",
                                  en: "Waiting for the payment to confirm…")

    // MARK: Provider messages, notifications, time off
    static let tabMessages = L(fa: "پیام‌ها", ps: "پیغامونه", en: "Messages")
    static let noMessages = L(fa: "هنوز پیامی از مشتری‌ها نیست.",
                              ps: "تر اوسه د پیرودونکو پیغام نشته.",
                              en: "No messages from customers yet.")
    static let timeOff = L(fa: "روزهای تعطیل", ps: "د رخصتۍ ورځې", en: "Days off")
    static let timeOffHint = L(
        fa: "در این روزها هیچ نوبتی پیشنهاد نمی‌شود، حتی اگر ساعات کاری باز باشد.",
        ps: "په دې ورځو کې هیڅ نوبت نه وړاندې کیږي، که څه هم د کار ساعتونه پرانیستي وي.",
        en: "No times are offered on these days, even if your hours say open.")
    static let addDayOff = L(fa: "افزودن روز", ps: "ورځ زیاتول", en: "Add a day")

    // MARK: Editing the salon
    static let neighbourhood = L(fa: "محله", ps: "ګاونډ", en: "Neighbourhood")
    static let editSalon = L(fa: "ویرایش سالن", ps: "د سالون سمون", en: "Edit salon")
    static let salonListedToggle = L(fa: "سالن در فهرست باشد", ps: "سالون دې په لیست کې وي",
                                     en: "List my salon")
    static let salonListedHint = L(
        fa: "وقتی خاموش باشد، مشتری‌ها سالن شما را نمی‌بینند و نمی‌توانند رزرو کنند.",
        ps: "کله چې مړه وي، پیرودونکي ستاسو سالون نه ویني او بکینګ نه شي کولی.",
        en: "While this is off, customers cannot see your salon or book with it.")
    static let addService = L(fa: "افزودن خدمت", ps: "خدمت زیاتول", en: "Add service")
    static let priceAfn = L(fa: "قیمت (افغانی)", ps: "بیه (افغانۍ)", en: "Price (AFN)")
    static let removeService = L(fa: "حذف خدمت", ps: "خدمت لرې کول", en: "Remove service")
    static let openTime = L(fa: "باز", ps: "پرانیستل", en: "Opens")
    static let closeTime = L(fa: "بسته", ps: "تړل", en: "Closes")
    static let saved = L(fa: "ذخیره شد.", ps: "خوندي شو.", en: "Saved.")
    static let errNeedOneService = L(fa: "حداقل یک خدمت با قیمت لازم است.",
                                     ps: "لږ تر لږه یو خدمت له بیې سره اړین دی.",
                                     en: "At least one service with a price is required.")
    static let errCloseBeforeOpen = L(fa: "ساعت بسته‌شدن باید بعد از ساعت بازشدن باشد.",
                                      ps: "د تړلو ساعت باید د پرانیستلو له ساعته وروسته وي.",
                                      en: "Closing time must be after opening time.")
    static let editOnConsole = L(
        fa: "گالری، کارمندان، بسته‌ها و آفرها در کنسول سالن روی کامپیوتر ویرایش می‌شوند.",
        ps: "ګالري، کارمندان، بنډلونه او آفرونه د سالون په کنسول کې په کمپیوټر کې سمیږي.",
        en: "Gallery, staff, packages and offers are edited in the salon console on a computer.")

    // MARK: Onboarding — Android's copy verbatim, so the promise made on one
    // phone is the promise made on the other.
    static let onboardingTitle1 = L(fa: "سالن‌های زیبایی نزدیک خود را پیدا کنید",
                                    ps: "د ځان نږدې ښکلا سالونونه ومومئ",
                                    en: "Find beauty salons near you")
    static let onboardingSubtitle1 = L(
        fa: "بر اساس خدمت، منطقه و امتیاز جستجو کنید تا بهترین سالن را پیدا کنید.",
        ps: "د خدمت، سیمې، او درجې پر بنسټ لټون وکړئ ترڅو غوره سالون ومومئ.",
        en: "Search by service, neighborhood, and rating to find the perfect salon.")
    static let onboardingTitle2 = L(fa: "رزرو در چند ثانیه", ps: "په څو ثانیو کې بکینګ وکړئ",
                                    en: "Book in seconds")
    static let onboardingSubtitle2 = L(
        fa: "بدون تماس تلفنی — زمان موردنظر را انتخاب کنید و رزروتان تمام است.",
        ps: "د تلیفون کال پرته — وخت وټاکئ، بکینګ وغواړئ، او دا ده.",
        en: "No phone calls — pick a time, request your booking, and you're done.")
    static let onboardingTitle3 = L(fa: "امن و منعطف", ps: "خوندي او انعطاف منونکی",
                                    en: "Safe and flexible")
    static let onboardingSubtitle3 = L(
        fa: "هویت هر سالن بررسی می‌شود. هرطور که راحت هستید پرداخت کنید — نقدی یا آنلاین.",
        ps: "د هر سالون هویت تایید شوی دی. تاسو چې څنګه غواړئ تادیه وکړئ — نغدې یا آنلاین.",
        en: "Every salon's identity is verified. Pay however you like — cash or online.")
    static let onboardingSkip = L(fa: "رد شدن", ps: "پریږدئ", en: "Skip")
    static let onboardingNext = L(fa: "بعدی", ps: "بل", en: "Next")
    static let onboardingGetStarted = L(fa: "شروع کنید", ps: "پیل وکړئ", en: "Get Started")

    // Android's analytics strings verbatim.
    static let analyticsTotal = L(fa: "کل درخواست‌ها", ps: "ټول غوښتنې", en: "Total requests")
    static let analyticsConfirmed = L(fa: "تایید شده", ps: "تایید شوي", en: "Confirmed")
    static let analyticsCancelled = L(fa: "لغو شده", ps: "لغو شوي", en: "Cancelled")
    static let analyticsByService = L(fa: "بر اساس خدمات", ps: "د خدمت له مخې",
                                      en: "By service")
    static let noDataYet = L(fa: "هنوز رزروی نیست", ps: "لا هیڅ بکینګ نشته",
                             en: "No bookings yet")
    static let finishSalonSetup = L(fa: "ساخت سالن را کامل کنید",
                                    ps: "د سالون جوړول بشپړ کړئ",
                                    en: "Finish setting up your salon")
    static let finishSalonExplain = L(
        fa: "جزئیات سالن شما هنگام ثبت‌نام ذخیره شد ولی سالن ساخته نشد. با یک ضربه کاملش کنید.",
        ps: "ستاسو د سالون جزئیات د نوم‌لیکنې پر مهال خوندي شول خو سالون جوړ نه شو. په یوه کېکاږلو یې بشپړ کړئ.",
        en: "Your salon details were saved at registration but the salon was never created. One tap finishes it.")
    static let rateCustomer = L(fa: "ثبت بازخورد مشتری", ps: "د پیرودونکې نظر ثبتول",
                                en: "Rate this customer")
    static let customerNoShow = L(fa: "مشتری نیامد", ps: "پیرودونکې رانغله",
                                  en: "Customer did not come")
    static let customerFlag = L(fa: "این مشتری مشکل‌ساز بود", ps: "دا پیرودونکې ستونزمنه وه",
                                en: "This customer was a problem")
    static let rateCustomerNote = L(
        fa: "این بازخورد فقط برای پلتفرم است و به مشتری نشان داده نمی‌شود.",
        ps: "دا نظر یوازې د پلیټ‌فارم لپاره دی او پیرودونکې ته نه ښودل کیږي.",
        en: "This feedback is for the platform only and is never shown to the customer.")
    static let rateCustomerDone = L(fa: "بازخورد ثبت شد.", ps: "نظر ثبت شو.",
                                    en: "Feedback recorded.")
    static let commentOptional = L(fa: "توضیح (اختیاری)", ps: "تشریح (اختیاري)",
                                   en: "Note (optional)")
    static let servicesAndPrices = L(fa: "خدمات و قیمت‌ها", ps: "خدمتونه او بیې",
                                     en: "Services and prices")
    static let anonymousCustomer = L(fa: "مشتری", ps: "پیرودونکې", en: "A customer")
    static let replyToReview = L(fa: "پاسخ دادن", ps: "ځواب ورکول", en: "Reply")
    static let replyPlaceholder = L(fa: "پاسخ شما به این نظر…", ps: "دې نظر ته ستاسو ځواب…",
                                    en: "Your reply to this review…")
    static let yourReply = L(fa: "پاسخ شما", ps: "ستاسو ځواب", en: "Your reply")
    static let workingHours = L(fa: "ساعات کاری", ps: "د کار ساعتونه", en: "Working hours")
    static let closedDay = L(fa: "تعطیل", ps: "رخصت", en: "Closed")
    static let salonListed = L(fa: "در فهرست است", ps: "په لیست کې دی", en: "Listed")
    static let salonHidden = L(fa: "در فهرست نیست", ps: "په لیست کې نه دی", en: "Not listed")
    static let providerConsoleHint = L(
        fa: "برای ویرایش قیمت‌ها، ساعات کاری، گالری و کارمندان، کنسول سالن را در کامپیوتر باز کنید: safebeauty.web.app/provider",
        ps: "د بیو، د کار ساعتونو، ګالرۍ او کارمندانو د سمولو لپاره، په کمپیوټر کې د سالون کنسول پرانیځئ: safebeauty.web.app/provider",
        en: "To edit prices, working hours, gallery and staff, open the salon console on a computer: safebeauty.web.app/provider")
    static let noSalonYet = L(
        fa: "سالن شما هنوز ساخته نشده. پس از تأیید ادمین اینجا ظاهر می‌شود.",
        ps: "ستاسو سالون لا نه دی جوړ شوی. د اډمین له تاییده وروسته دلته ښکاري.",
        en: "Your salon is not set up yet. It appears here once an admin approves you.")

    // MARK: Stories
    static let stories = L(fa: "اعلان‌های امروز", ps: "د نن اعلانونه", en: "Today")

    // MARK: Profile photo and its reward
    static let changePhoto = L(fa: "تغییر عکس", ps: "عکس بدلول", en: "Change photo")
    static let photoTooLarge = L(fa: "این عکس خیلی بزرگ است. عکس دیگری انتخاب کنید.",
                                 ps: "دا عکس ډېر لوی دی. بل عکس وټاکئ.",
                                 en: "That photo is too large. Please choose another.")
    static let photoUploadFailed = L(fa: "عکس بارگذاری نشد.", ps: "عکس پورته نه شو.",
                                     en: "The photo could not be uploaded.")
    static func rewardEarned(_ points: Int) -> String {
        switch AppLanguage.current {
        case .dari: "پروفایل شما کامل شد — \(points) امتیاز گرفتید."
        case .pashto: "ستاسو پروفایل بشپړ شو — \(points) ټکي مو ترلاسه کړل."
        case .english: "Your profile is complete — you earned \(points) points."
        }
    }

    // MARK: Packages and promo codes
    static let packageApplied = L(fa: "بستهٔ خدمات اعمال شد", ps: "د خدمتونو بنډل پلی شو",
                                  en: "Package applied")
    static func packageOff(_ percent: Int) -> String {
        switch AppLanguage.current {
        case .dari: "٪\(percent) تخفیف روی این خدمات"
        case .pashto: "پر دې خدمتونو \(percent)٪ تخفیف"
        case .english: "\(percent)% off these services"
        }
    }
    static let checkCode = L(fa: "بررسی کد", ps: "کوډ وګورئ", en: "Check code")
    static let promoInvalid = L(fa: "این کد معتبر نیست.", ps: "دا کوډ سم نه دی.",
                                en: "That code is not valid.")
    static func promoSaves(_ amount: Int) -> String {
        switch AppLanguage.current {
        case .dari: "کد پذیرفته شد — \(amount) افغانی کمتر."
        case .pashto: "کوډ ومنل شو — \(amount) افغانۍ لږ."
        case .english: "Code accepted — \(amount) AFN off."
        }
    }

    // MARK: Comments and account deletion
    static let comments = L(fa: "نظرها", ps: "نظرونه", en: "Comments")
    static let noComments = L(fa: "هنوز نظری نیست", ps: "تر اوسه نظر نشته", en: "No comments yet")
    static let writeComment = L(fa: "نظرتان را بنویسید…", ps: "خپل نظر ولیکئ…",
                                en: "Write a comment…")
    static let commentTooLong = L(fa: "نظر باید کمتر از ۳۰۰ حرف باشد.",
                                  ps: "نظر باید له ۳۰۰ تورو لږ وي.",
                                  en: "A comment must be under 300 characters.")
    static let deleteComment = L(fa: "حذف نظر", ps: "نظر ړنګول", en: "Delete comment")
    static let deleteAccount = L(fa: "حذف حساب من", ps: "زما حساب ړنګول", en: "Delete my account")
    static let deleteAccountWarning = L(
        fa: "حساب شما بسته می‌شود و دیگر نمی‌توانید وارد شوید. نوبت‌های آیندهٔ شما لغو می‌شود. این کار برگشت‌پذیر نیست.",
        ps: "ستاسو حساب تړل کیږي او نور نه شئ ننوتلی. ستاسو راتلونکي نوبتونه لغوه کیږي. دا بیرته‌راګرځېدونکې نه ده.",
        en: "Your account will be closed and you will not be able to sign in. Your upcoming appointments will be cancelled. This cannot be undone.")
    static let deleteAccountConfirm = L(fa: "بله، حسابم را حذف کن", ps: "هو، زما حساب ړنګ کړه",
                                        en: "Yes, delete my account")
    static let deleteAccountDone = L(fa: "حساب شما حذف شد.", ps: "ستاسو حساب ړنګ شو.",
                                     en: "Your account has been deleted.")

    // MARK: Waitlist
    static let joinWaitlist = L(fa: "به لیست انتظار اضافه شو", ps: "د انتظار لیست ته ننوځه",
                                en: "Join the waitlist")
    static let waitlist = L(fa: "لیست انتظار", ps: "د انتظار لیست", en: "Waitlist")
    static let waitlistJoined = L(fa: "در لیست انتظار این روز هستید. اگر جایی باز شود خبرتان می‌کنیم.",
                                  ps: "د دې ورځې په انتظار لیست کې یاست. که ځای خالي شي، خبر درکوو.",
                                  en: "You are on the waitlist for that day. We will tell you if a place opens.")
    static let waitlistOffered = L(fa: "جا باز شد! زودتر رزرو کنید.",
                                   ps: "ځای خالي شو! ژر بکینګ وکړئ.",
                                   en: "A place opened. Book before it goes.")
    static let waitlistWaiting = L(fa: "در انتظار", ps: "په تمه", en: "Waiting")
    static let dismiss = L(fa: "رد کردن", ps: "رد کول", en: "Dismiss")
    static let leaveWaitlist = L(fa: "خروج از لیست انتظار", ps: "له انتظار لیسته وتل",
                                 en: "Leave the waitlist")

    // MARK: Wallet, gift cards and tips
    static let topUp = L(fa: "شارژ", ps: "چارج", en: "Top up")
    static let topUpTitle = L(fa: "شارژ کیف پول", ps: "د بټوې چارج", en: "Top up wallet")
    static let topUpRule = L(fa: "بین ۵۰ تا ۵۰٬۰۰۰ افغانی.",
                             ps: "د ۵۰ او ۵۰٬۰۰۰ افغانیو ترمنځ.",
                             en: "Between 50 and 50,000 AFN.")
    static let walletAutoApplies = L(fa: "اعتبار کیف پول شما به‌طور خودکار در رزرو بعدی اعمال می‌شود.",
                                     ps: "ستاسو د بټوې اعتبار په راتلونکي بکینګ کې پخپله پلی کیږي.",
                                     en: "Your wallet credit is applied automatically at your next booking.")
    static let giftCard = L(fa: "کارت هدیه", ps: "د ډالۍ کارت", en: "Gift card")
    static let giftTo = L(fa: "شماره تلفن گیرنده", ps: "د ترلاسه‌کوونکي د تلیفون شمېره",
                          en: "Recipient's phone number")
    static let giftMessage = L(fa: "پیام (اختیاری)", ps: "پیغام (اختیاري)", en: "Message (optional)")
    static let giftRule = L(fa: "بین ۵۰ تا ۵۰٬۰۰۰ افغانی. اعتبار مستقیم به کیف پول او می‌رود.",
                            ps: "د ۵۰ او ۵۰٬۰۰۰ افغانیو ترمنځ. اعتبار مستقیم د هغې بټوې ته ځي.",
                            en: "Between 50 and 50,000 AFN. The credit goes straight to her wallet.")
    static let tip = L(fa: "انعام", ps: "انعام", en: "Tip")
    static let tipTitle = L(fa: "انعام به سالن", ps: "سالون ته انعام", en: "Tip the salon")
    static let tipRule = L(fa: "بین ۱۰ تا ۲۰٬۰۰۰ افغانی. تمام مبلغ به سالن می‌رسد.",
                           ps: "د ۱۰ او ۲۰٬۰۰۰ افغانیو ترمنځ. ټوله پیسې سالون ته رسیږي.",
                           en: "Between 10 and 20,000 AFN. The salon receives all of it.")
    static let amountAfn = L(fa: "مبلغ (افغانی)", ps: "اندازه (افغانۍ)", en: "Amount (AFN)")
    static let payNowShort = L(fa: "پرداخت", ps: "تادیه", en: "Pay")
    static let openingCheckout = L(fa: "صفحهٔ پرداخت باز شد. پس از پرداخت به اپ برگردید.",
                                   ps: "د تادیې پاڼه پرانیستل شوه. له تادیې وروسته اپ ته راستون شئ.",
                                   en: "The payment page is open. Come back to the app when you are done.")
    static let rescheduleFrom = L(fa: "زمان فعلی", ps: "اوسنی وخت", en: "Current time")
    static let rescheduleDone = L(fa: "زمان نوبت شما تغییر کرد.",
                                  ps: "ستاسو د نوبت وخت بدل شو.",
                                  en: "Your appointment has been moved.")
    static let rescheduleClosed = L(fa: "سالن آن روز بسته است.",
                                    ps: "سالون هغه ورځ تړلی دی.",
                                    en: "The salon is closed that day.")
    static let rescheduleTaken = L(fa: "آن زمان همین حالا گرفته شد. زمان دیگری انتخاب کنید.",
                                   ps: "هغه وخت همدا اوس ونیول شو. بل وخت وټاکئ.",
                                   en: "That time was just taken. Please pick another.")
    static let rescheduleTooLate = L(fa: "این نوبت دیگر قابل تغییر نیست.",
                                     ps: "دا نوبت نور نه شي بدلېدلی.",
                                     en: "This booking can no longer be changed.")
    static let noFavourites = L(fa: "هنوز سالنی را نشان نکرده‌اید.",
                                ps: "تر اوسه مو کوم سالون نه دی خوښ کړی.",
                                en: "You have not saved any salons yet.")
    static let noFavouritesHint = L(fa: "روی ❤ در کنار نام سالن بزنید تا اینجا بماند.",
                                    ps: "د سالون د نوم تر څنګ ❤ کېکاږئ چې دلته پاتې شي.",
                                    en: "Tap ❤ beside a salon to keep it here.")
    static let addFavourite = L(fa: "افزودن به علاقه‌مندی‌ها", ps: "خوښو ته زیاتول",
                                en: "Add to favorites")
    static let removeFavourite = L(fa: "برداشتن از علاقه‌مندی‌ها", ps: "له خوښو لرې کول",
                                   en: "Remove from favorites")
    static let allNeighbourhoods = L(fa: "همه محله‌ها", ps: "ټول ګاونډونه",
                                     en: "All neighborhoods")

    // MARK: Service categories
    // The Android values verbatim. The Pashto for eyebrows is وروځې — وریځې is
    // clouds, one letter apart, and categories.js carries the same warning
    // because that mistake would silently match nothing.
    static let categoryAll = L(fa: "همه", ps: "ټول", en: "All")
    static let categoryHair = L(fa: "مو", ps: "ویښتان", en: "Hair")
    static let categoryMakeup = L(fa: "آرایش", ps: "سینګار", en: "Makeup")
    static let categoryNails = L(fa: "ناخن", ps: "نوکان", en: "Nails")
    static let categorySkincare = L(fa: "مراقبت پوست", ps: "جلدي پاملرنه", en: "Skincare")
    static let categoryEyebrows = L(fa: "ابرو", ps: "وروځې", en: "Eyebrows")
    static let noMatches = L(fa: "چیزی پیدا نشد.", ps: "څه ونه موندل شول.", en: "Nothing found.")
    static let clearFilters = L(fa: "پاک کردن فیلترها", ps: "فلټرونه پاکول",
                                en: "Clear filters")

    // MARK: Filters & sort
    // The Android values verbatim — AppStrings.kt filtersButton…maxPriceLabel,
    // plus the two location sentences the Nearest sort needs. The whole sheet
    // existed on Android and not here, so on an iPhone there was no way to sort
    // by price, by rating or by distance at all.
    static let filtersButton = L(fa: "فیلترها", ps: "فلټرونه", en: "Filters")
    static let filtersTitle = L(fa: "فیلتر و مرتب‌سازی", ps: "فلټر او ترتیب",
                                en: "Filters & sort")
    static let filtersReset = L(fa: "بازنشانی", ps: "بیا تنظیم", en: "Reset")
    static let sortByLabel = L(fa: "مرتب‌سازی بر اساس", ps: "ترتیب پر بنسټ",
                               en: "Sort by")
    static let sortRecommended = L(fa: "پیشنهادی", ps: "وړاندیز شوی",
                                   en: "Recommended")
    static let sortNearest = L(fa: "نزدیک‌ترین", ps: "نږدې", en: "Nearest")
    static let sortTopRated = L(fa: "بالاترین امتیاز", ps: "لوړ امتیاز",
                                en: "Top rated")
    static let sortCheapest = L(fa: "ارزان‌ترین", ps: "ارزانه", en: "Cheapest")
    static let minRatingLabel = L(fa: "حداقل امتیاز", ps: "لږ تر لږه امتیاز",
                                  en: "Minimum rating")
    static let maxPriceLabel = L(fa: "سقف قیمت شروع", ps: "د پیل اعظمي بیه",
                                 en: "Max starting price")
    static let filterAny = L(fa: "همه", ps: "ټول", en: "Any")
    static let locationUnavailable = L(
        fa: "موقعیت پیدا نشد. در فضای باز دوباره امتحان کنید.",
        ps: "موقعیت ونه موندل شو. په خلاصه فضا کې بیا هڅه وکړئ.",
        en: "Couldn't get a location fix. Try again outdoors.")
    static let locationPermissionNeeded = L(
        fa: "برای این کار اجازهٔ دسترسی به موقعیت لازم است.",
        ps: "د دې کار لپاره د موقعیت اجازه اړینه ده.",
        en: "Location permission is needed to do this.")
    static let support = L(fa: "پشتیبانی", ps: "ملاتړ", en: "Support")
    static let typeMessage = L(fa: "پیام‌تان را بنویسید…", ps: "خپل پیغام ولیکئ…",
                               en: "Write your message…")
    static let supportIntro = L(
        fa: "هر سؤال یا مشکلی داشتید بنویسید. تیم SafeBeauty جواب می‌دهد.",
        ps: "هره پوښتنه یا ستونزه مو وه ولیکئ. د SafeBeauty ټیم ځواب درکوي.",
        en: "Write any question or problem. The SafeBeauty team will reply.")

    // MARK: Discover & map
    static let discover = L(fa: "کشف", ps: "کشف", en: "Discover")
    static let offers = L(fa: "پیشنهادها", ps: "وړاندیزونه", en: "Offers")
    static let latest = L(fa: "تازه‌ها", ps: "تازه", en: "Latest")
    static let feedEmpty = L(
        fa: "هنوز چیزی برای نشان دادن نیست.", ps: "تر اوسه د ښودلو لپاره څه نشته.",
        en: "Nothing to show yet.")
    static let map = L(fa: "نقشه", ps: "نقشه", en: "Map")
    // Two different sentences, because they lead somewhere different: none on
    // the map at all means the map is useless to her right now; some missing
    // means the map is useful but incomplete.
    static let mapNoneP = L(
        fa: "هیچ سالنی هنوز محل خود را روی نقشه ثبت نکرده:",
        ps: "هیڅ سالون لا خپل ځای په نقشه کې نه دی ثبت کړی:",
        en: "No salon has pinned its location yet:")
    static let mapSomeMissing = L(
        fa: "این سالن‌ها روی نقشه نیستند:",
        ps: "دا سالونونه په نقشه کې نشته:",
        en: "These salons are not on the map:")

    static let language = L(fa: "زبان", ps: "ژبه", en: "Language")

    // MARK: Common
    static let cancel = L(fa: "لغو", ps: "لغوه", en: "Cancel")
    static let signOut = L(fa: "خروج", ps: "وتل", en: "Sign out")
    static let pendingApproval = L(
        fa: "حساب شما در انتظار تأیید است.", ps: "ستاسو حساب د تایید په تمه دی.",
        en: "Your account is awaiting approval.")
}
