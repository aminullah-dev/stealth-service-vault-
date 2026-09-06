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
