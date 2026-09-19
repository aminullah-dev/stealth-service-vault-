"use strict";
/**
 * The fictional demo world: six invented Kabul salons, their owners, reviews
 * and offers. Nothing here corresponds to a real salon, a real person or a
 * real phone number — every name is made up and every number is in the
 * +93 70 000 00xx block reserved here for the demo.
 */

// Deterministic ids so re-running the seed updates rather than duplicates.
const uid = (n) => `demo-${n}`;

// Kabul, roughly. Coordinates are approximate district centres — enough for the
// map screen to look right, precise enough to point at nobody.
const SALONS = [
  {
    id: "demo-salon-gol-e-sorkh",
    opensFriday: true,
    salonName: "سالن گل سرخ",
    providerId: uid("owner-gol-e-sorkh"),
    providerName: "زرغونه احمدی",
    providerPhone: "+93700000011",
    district: "KBL_D2_ShahreNaw",
    areaKey: "KBL_Shahr_e_Naw",
    services: ["کوتاهی مو", "رنگ مو", "کراتین مو", "آرایش عروس", "آرایش مجلسی"],
    pricePerService: {
      "کوتاهی مو": 350, "رنگ مو": 1800, "کراتین مو": 3500,
      "آرایش عروس": 9000, "آرایش مجلسی": 2200,
    },
    durationPerService: {
      "کوتاهی مو": 45, "رنگ مو": 120, "کراتین مو": 180,
      "آرایش عروس": 180, "آرایش مجلسی": 90,
    },
    serviceTiming: { "رنگ مو": { activeBefore: 30, processing: 60, activeAfter: 30 } },
    rating: 14/3, confirmedCount: 63, isVerified: true,
    latitude: 34.5350, longitude: 69.1720,
    slotDurationMinutes: 30,
    staff: [
      { id: "st-gs-1", name: "زرغونه", specialty: "Hair", photoUrls: [], active: true },
      { id: "st-gs-2", name: "مریم", specialty: "Makeup", photoUrls: [], active: true },
    ],
    lastMinuteEnabled: true, lastMinutePercent: 15, lastMinuteWindowHours: 6,
    packages: [
      { id: "pk-gs-1", name: "بسته عروسی", services: ["آرایش عروس", "کوتاهی مو"], discountPercent: 12 },
    ],
  },
  {
    id: "demo-salon-nastaran",
    salonName: "آرایشگاه نسترن",
    providerId: uid("owner-nastaran"),
    providerName: "فرشته نوری",
    providerPhone: "+93700000012",
    district: "KBL_D3_KarteChar",
    areaKey: "KBL_Karte_Char",
    services: ["مانیکور", "پدیکور", "کاشت ناخن", "ناخن ژل", "آرایش مجلسی"],
    pricePerService: {
      "مانیکور": 400, "پدیکور": 500, "کاشت ناخن": 1500,
      "ناخن ژل": 900, "آرایش مجلسی": 2000,
    },
    durationPerService: {
      "مانیکور": 45, "پدیکور": 60, "کاشت ناخن": 120, "ناخن ژل": 75, "آرایش مجلسی": 90,
    },
    serviceTiming: {},
    rating: 4.5, confirmedCount: 41, isVerified: true,
    latitude: 34.5087, longitude: 69.1450,
    slotDurationMinutes: 30,
    staff: [
      { id: "st-ns-1", name: "نسترن", specialty: "Nails", photoUrls: [], active: true },
    ],
    lastMinuteEnabled: false, lastMinutePercent: 0, lastMinuteWindowHours: 0,
    packages: [
      { id: "pk-ns-1", name: "دست و پا", services: ["مانیکور", "پدیکور"], discountPercent: 15 },
    ],
  },
  {
    id: "demo-salon-banafsha",
    salonName: "سالن بنفشه",
    providerId: uid("owner-banafsha"),
    providerName: "سمیرا رحیمی",
    providerPhone: "+93700000013",
    district: "KBL_D11_KhairKhana",
    areaKey: "KBL_Khair_Khana",
    services: ["بند انداختن ابرو", "تتو ابرو", "مژه", "فیشل پوست", "ماسک پوست"],
    pricePerService: {
      "بند انداختن ابرو": 200, "تتو ابرو": 3000, "مژه": 1200,
      "فیشل پوست": 1000, "ماسک پوست": 700,
    },
    durationPerService: {
      "بند انداختن ابرو": 30, "تتو ابرو": 120, "مژه": 90,
      "فیشل پوست": 60, "ماسک پوست": 45,
    },
    serviceTiming: { "ماسک پوست": { activeBefore: 10, processing: 25, activeAfter: 10 } },
    rating: 4.25, confirmedCount: 22, isVerified: false,
    latitude: 34.5540, longitude: 69.1330,
    slotDurationMinutes: 30,
    staff: [],
    lastMinuteEnabled: true, lastMinutePercent: 10, lastMinuteWindowHours: 4,
    packages: [],
  },
  {
    id: "demo-salon-yasamin",
    opensFriday: true,
    salonName: "سالن یاسمین",
    providerId: uid("owner-yasamin"),
    providerName: "لیلا صدیقی",
    providerPhone: "+93700000014",
    district: "KBL_D10_WazirAkbarKhan",
    areaKey: "KBL_Wazir_Akbar_Khan",
    services: ["آرایش عروس", "میکاپ مجلسی", "کوتاهی مو", "هایلایت مو", "فیشل پوست"],
    pricePerService: {
      "آرایش عروس": 12000, "میکاپ مجلسی": 2500, "کوتاهی مو": 500,
      "هایلایت مو": 2600, "فیشل پوست": 1200,
    },
    durationPerService: {
      "آرایش عروس": 210, "میکاپ مجلسی": 90, "کوتاهی مو": 45,
      "هایلایت مو": 150, "فیشل پوست": 60,
    },
    serviceTiming: { "هایلایت مو": { activeBefore: 45, processing: 60, activeAfter: 45 } },
    rating: 4.75, confirmedCount: 88, isVerified: true,
    latitude: 34.5360, longitude: 69.1810,
    slotDurationMinutes: 30,
    staff: [
      { id: "st-ys-1", name: "یاسمین", specialty: "Makeup", photoUrls: [], active: true },
      { id: "st-ys-2", name: "حمیرا", specialty: "Hair", photoUrls: [], active: true },
      { id: "st-ys-3", name: "شکریه", specialty: "Skincare", photoUrls: [], active: false },
    ],
    lastMinuteEnabled: false, lastMinutePercent: 0, lastMinuteWindowHours: 0,
    packages: [
      { id: "pk-ys-1", name: "بسته کامل عروس", services: ["آرایش عروس", "هایلایت مو"], discountPercent: 10 },
    ],
  },
  {
    id: "demo-salon-parwana",
    salonName: "سالن پروانه",
    providerId: uid("owner-parwana"),
    providerName: "نیلوفر امینی",
    providerPhone: "+93700000015",
    district: "KBL_D9_Makroryan",
    areaKey: "KBL_Makroryan",
    services: ["رنگ مو", "کوتاهی مو", "مانیکور", "ماسک پوست", "بند انداختن ابرو"],
    pricePerService: {
      "رنگ مو": 1500, "کوتاهی مو": 300, "مانیکور": 350,
      "ماسک پوست": 600, "بند انداختن ابرو": 150,
    },
    durationPerService: {
      "رنگ مو": 120, "کوتاهی مو": 45, "مانیکور": 45,
      "ماسک پوست": 45, "بند انداختن ابرو": 30,
    },
    serviceTiming: {},
    rating: 4.0, confirmedCount: 15, isVerified: false,
    latitude: 34.5420, longitude: 69.1980,
    slotDurationMinutes: 30,
    staff: [],
    lastMinuteEnabled: true, lastMinutePercent: 20, lastMinuteWindowHours: 8,
    packages: [],
  },
  {
    id: "demo-salon-lala",
    salonName: "سالن لاله",
    providerId: uid("owner-lala"),
    providerName: "عادله کریمی",
    providerPhone: "+93700000016",
    district: "KBL_D4_KoteSangi",
    areaKey: "KBL_Kote_Sangi",
    services: ["کوتاهی مو", "صافی مو", "ناخن ژل", "بند انداختن ابرو", "فیشل پوست"],
    pricePerService: {
      "کوتاهی مو": 250, "صافی مو": 2000, "ناخن ژل": 800,
      "بند انداختن ابرو": 150, "فیشل پوست": 900,
    },
    durationPerService: {
      "کوتاهی مو": 45, "صافی مو": 150, "ناخن ژل": 75,
      "بند انداختن ابرو": 30, "فیشل پوست": 60,
    },
    serviceTiming: {},
    rating: 13/3, confirmedCount: 30, isVerified: false,
    latitude: 34.5150, longitude: 69.1180,
    slotDurationMinutes: 30,
    staff: [
      { id: "st-ll-1", name: "لاله", specialty: "Hair", photoUrls: [], active: true },
    ],
    lastMinuteEnabled: false, lastMinutePercent: 0, lastMinuteWindowHours: 0,
    packages: [],
  },
];

// Invented customers who left the reviews. None of these is a real person.
const REVIEWS = [
  // Averages are deliberate: they are what the salon card shows and what the
  // list sorts by, so each salon's set is chosen to land on a distinct,
  // plausible score (4.75 / 4.67 / 4.5 / 4.33 / 4.25 / 4.0).
  { salon: "demo-salon-gol-e-sorkh", customerName: "سحر",   rating: 5, comment: "رنگ مو دقیقاً همان چیزی شد که می‌خواستم. برخورد شان خیلی گرم بود.", reply: "تشکر از اعتماد تان، منتظر دیدار دوباره هستیم.", daysAgo: 4 },
  { salon: "demo-salon-gol-e-sorkh", customerName: "مرسل",  rating: 5, comment: "وقت را دقیق نگه داشتند و سر ساعت شروع کردند.", reply: "", daysAgo: 11 },
  { salon: "demo-salon-gol-e-sorkh", customerName: "بهار",  rating: 4, comment: "کار خوب بود، فقط کمی منتظر ماندم.", reply: "معذرت می‌خواهیم، آن روز رزرو زیاد داشتیم.", daysAgo: 19 },

  { salon: "demo-salon-nastaran",    customerName: "زهرا",  rating: 5, comment: "کاشت ناخن تمیز و با حوصله. قیمت هم مناسب بود.", reply: "", daysAgo: 6 },
  { salon: "demo-salon-nastaran",    customerName: "عالیه", rating: 5, comment: "ژل ناخن بعد از سه هفته هنوز سالم است.", reply: "تشکر، از مواد باکیفیت استفاده می‌کنیم.", daysAgo: 26 },
  { salon: "demo-salon-nastaran",    customerName: "فریبا", rating: 4, comment: "پدیکور خوب بود. جای پارک کمی مشکل دارد.", reply: "", daysAgo: 15 },
  { salon: "demo-salon-nastaran",    customerName: "شریفه", rating: 4, comment: "کار شان پاک است، اما روز پنجشنبه خیلی شلوغ می‌شود.", reply: "", daysAgo: 33 },

  { salon: "demo-salon-banafsha",    customerName: "شکیبا", rating: 5, comment: "بند انداختن ابرو خیلی ظریف انجام شد.", reply: "ممنون از نظر تان.", daysAgo: 3 },
  { salon: "demo-salon-banafsha",    customerName: "رویا",  rating: 4, comment: "فیشل پوست خوب بود و محیط آرام دارد.", reply: "", daysAgo: 13 },
  { salon: "demo-salon-banafsha",    customerName: "الهه",  rating: 4, comment: "تتو ابرو طبیعی شد، فقط یک هفته سرخی داشت.", reply: "", daysAgo: 22 },
  { salon: "demo-salon-banafsha",    customerName: "نادیه", rating: 4, comment: "قیمت مناسب است. وقت گرفتن از طریق اپلیکیشن آسان بود.", reply: "", daysAgo: 29 },

  { salon: "demo-salon-yasamin",     customerName: "نرگس",  rating: 5, comment: "آرایش عروسی خواهرم را اینجا کردیم، همه تعریف کردند.", reply: "برای خواهر تان آرزوی خوشبختی داریم.", daysAgo: 8 },
  { salon: "demo-salon-yasamin",     customerName: "تمنا",  rating: 5, comment: "هایلایت دقیقاً مطابق عکسی بود که نشان دادم.", reply: "", daysAgo: 21 },
  { salon: "demo-salon-yasamin",     customerName: "مژگان", rating: 5, comment: "سه بار رفته‌ام و هر بار همان کیفیت را داشت.", reply: "", daysAgo: 27 },
  { salon: "demo-salon-yasamin",     customerName: "صدف",   rating: 4, comment: "کیفیت عالی است، فقط قیمت‌ها کمی بالا.", reply: "", daysAgo: 30 },

  { salon: "demo-salon-parwana",     customerName: "حدیثه", rating: 4, comment: "قیمت مناسب و کار پاک. دوباره می‌روم.", reply: "", daysAgo: 5 },
  { salon: "demo-salon-parwana",     customerName: "سونیا", rating: 4, comment: "رنگ مو خوب شد، فقط کمی بیشتر از وقت مقرر طول کشید.", reply: "", daysAgo: 12 },
  { salon: "demo-salon-parwana",     customerName: "لینا",  rating: 4, comment: "برخورد شان خوب بود، محیط کمی کوچک است.", reply: "", daysAgo: 24 },

  { salon: "demo-salon-lala",        customerName: "مینا",  rating: 5, comment: "صافی مو را خیلی خوب انجام دادند.", reply: "", daysAgo: 9 },
  { salon: "demo-salon-lala",        customerName: "پریسا", rating: 4, comment: "نزدیک خانه ما است و وقت‌دهی شان منظم است.", reply: "", daysAgo: 17 },
  { salon: "demo-salon-lala",        customerName: "فرزانه", rating: 4, comment: "ناخن ژل خوب بود. کاش ساعت کاری شان درازتر می‌بود.", reply: "", daysAgo: 25 },
];

const OFFERS = [
  {
    id: "demo-offer-gol-e-sorkh",
    salon: "demo-salon-gol-e-sorkh",
    title: "۲۰٪ تخفیف رنگ مو تا آخر هفته",
    description: "برای رزرو آنلاین رنگ مو، تا روز پنجشنبه بیست فیصد تخفیف می‌گیرید.",
    service: "رنگ مو",
    discountPercent: 20, discountAmount: 0, expiresInDays: 7,
  },
  {
    id: "demo-offer-nastaran",
    salon: "demo-salon-nastaran",
    title: "مانیکور و پدیکور یکجا — ۳۰۰ افغانی کمتر",
    description: "هر دو خدمت را یکجا رزرو کنید و سیصد افغانی کمتر بپردازید.",
    service: "مانیکور",
    discountPercent: 0, discountAmount: 300, expiresInDays: 14,
  },
];

module.exports = { SALONS, REVIEWS, OFFERS };
