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

    // MARK: Common
    static let cancel = L(fa: "لغو", ps: "لغوه", en: "Cancel")
    static let signOut = L(fa: "خروج", ps: "وتل", en: "Sign out")
    static let pendingApproval = L(
        fa: "حساب شما در انتظار تأیید است.", ps: "ستاسو حساب د تایید په تمه دی.",
        en: "Your account is awaiting approval.")
}
