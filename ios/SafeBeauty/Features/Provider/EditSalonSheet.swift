import SwiftUI
import SafeBeautyCore

/// What a salon owner can change about her own salon, from her phone.
///
/// Android's provider profile tab is 1,429 lines and edits everything. This
/// edits the part that is urgent: a price is wrong, a service is gone, the shop
/// is shut on Thursday, or she needs to come off the listings for a week. None
/// of those should wait until she is next at a computer.
///
/// Gallery, staff, packages and offers stay on the console — they are
/// photo-and-table work that a phone makes worse, and the screen says so rather
/// than leaving her to hunt.
struct EditSalonSheet: View {
    let repo: ProviderRepository

    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var cityKey = ""
    @State private var district = ""
    @State private var areaKey = ""
    @State private var services: [String] = []
    @State private var prices: [String: String] = [:]
    @State private var hours: [WorkingHours] = []
    @State private var isAvailable = true
    @State private var blockedDates: [String] = []
    @State private var newDayOff = Date()
    @State private var newService = ""
    @State private var working = false
    @State private var error: String?
    @State private var saved = false
    @State private var loaded = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    BrandField(label: .salonName, text: $name)

                    Toggle(L.salonListedToggle.t, isOn: $isAvailable)
                        .font(Brand.font(15, .medium))
                        .foregroundStyle(Brand.ink).tint(Brand.accent)
                    Text(L.salonListedHint.t)
                        .font(Brand.font(12.5)).foregroundStyle(Brand.accent)

                    BrandPicker(label: .city, selection: $cityKey,
                                options: Areas.liveCities.map {
                                    (key: $0.key, title: Areas.label(of: $0))
                                })
                    BrandPicker(label: cityKey.isEmpty ? .pickCityFirst : .districtArea,
                                selection: $district,
                                options: Areas.districtsIn(cityKey).map {
                                    (key: $0.key, title: Areas.label(of: $0))
                                },
                                isEnabled: !cityKey.isEmpty)
                    // The finer level, offered only where the data records one.
                    // An empty picker reads as broken; no picker reads as "this
                    // district has none", which is true.
                    if !Areas.neighbourhoodsIn(district).isEmpty {
                        BrandPicker(label: .neighbourhood, selection: $areaKey,
                                    options: Areas.neighbourhoodsIn(district).map {
                                        (key: $0.key, title: Areas.label(of: $0))
                                    })
                    }

                    servicesSection
                    hoursSection
                    daysOffSection

                    Text(L.editOnConsole.t)
                        .font(Brand.font(12.5)).foregroundStyle(Brand.accent)

                    if saved { ErrorBanner(message: L.saved.t, tone: .notice) }
                    ErrorBanner(message: error)

                    BrandButton(title: .save, isLoading: working) {
                        Task { await save() }
                    }
                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 24).padding(.top, 16)
            }
            .background(Brand.cream.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(L.editSalon.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.cancel.t) { dismiss() }
                        .foregroundStyle(Brand.accent).disabled(working)
                }
            }
        }
        .onAppear { load() }
        .onChange(of: cityKey) { old, _ in
            // Her old district belongs to the city she just left. Only cleared
            // on a real change, so opening the sheet does not wipe it.
            if !old.isEmpty { district = ""; areaKey = "" }
        }
        .onChange(of: district) { old, _ in if !old.isEmpty { areaKey = "" } }
    }

    private var servicesSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L.servicesAndPrices.t)
                .font(Brand.font(15, .bold)).foregroundStyle(Brand.ink)

            ForEach(services, id: \.self) { service in
                HStack(spacing: 8) {
                    Text(service)
                        .font(Brand.font(14)).foregroundStyle(Brand.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    TextField(L.priceAfn.t, text: Binding(
                        get: { prices[service] ?? "" },
                        set: { prices[service] = $0 }))
                        .font(Brand.font(14))
                        .keyboardType(.numberPad)
                        .environment(\.layoutDirection, .leftToRight)
                        .multilineTextAlignment(.leading)
                        .frame(width: 90)
                        .padding(.horizontal, 10).padding(.vertical, 8)
                        .background(.white, in: RoundedRectangle(cornerRadius: 10))
                    Button {
                        services.removeAll { $0 == service }
                        prices[service] = nil
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 13)).foregroundStyle(Color(hex: 0xC0392B))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L.removeService.t)
                }
            }

            HStack(spacing: 8) {
                BrandField(label: .serviceName, text: $newService)
                Button(L.add.t) {
                    let s = newService.trimmingCharacters(in: .whitespaces)
                    guard !s.isEmpty, !services.contains(s) else { return }
                    services.append(s)
                    prices[s] = ""
                    newService = ""
                }
                .font(Brand.font(14, .medium)).foregroundStyle(Brand.accent)
                .disabled(newService.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
    }

    private var hoursSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L.workingHours.t)
                .font(Brand.font(15, .bold)).foregroundStyle(Brand.ink)
            ForEach($hours, id: \.dayOfWeek) { $day in
                VStack(spacing: 6) {
                    Toggle(Self.dayName(day.dayOfWeek), isOn: $day.isOpen)
                        .font(Brand.font(14, .medium))
                        .foregroundStyle(Brand.ink).tint(Brand.accent)
                    if day.isOpen {
                        HStack(spacing: 10) {
                            hourStepper(L.openTime.t, hour: $day.openHour)
                            hourStepper(L.closeTime.t, hour: $day.closeHour)
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }

    /// The days she is shut regardless of her hours — Eid, a wedding, illness.
    ///
    /// Stored as Kabul-local "yyyy-MM-dd", which is what DayGrid filters on and
    /// what rescheduleAppointment checks server-side. A different timezone here
    /// would block the wrong day by a few hours either side of midnight.
    private var daysOffSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L.timeOff.t).font(Brand.font(15, .bold)).foregroundStyle(Brand.ink)
            Text(L.timeOffHint.t).font(Brand.font(12.5)).foregroundStyle(Brand.accent)

            ForEach(blockedDates, id: \.self) { day in
                HStack {
                    Text(day)
                        .font(Brand.font(14))
                        .environment(\.layoutDirection, .leftToRight)
                        .foregroundStyle(Brand.ink)
                    Spacer()
                    Button {
                        blockedDates.removeAll { $0 == day }
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 13)).foregroundStyle(Color(hex: 0xC0392B))
                    }
                    .buttonStyle(.plain)
                }
            }

            HStack(spacing: 10) {
                DatePicker("", selection: $newDayOff, in: Date()...,
                           displayedComponents: .date)
                    .labelsHidden()
                Button(L.addDayOff.t) {
                    let key = Self.kabulDayKey(newDayOff)
                    guard !blockedDates.contains(key) else { return }
                    blockedDates.append(key)
                    blockedDates.sort()
                }
                .font(Brand.font(14, .medium)).foregroundStyle(Brand.accent)
            }
        }
    }

    /// The same key DayGrid and the server compute. Locale-independent on
    /// purpose: a Persian calendar would produce ۱۴۰۴-۰۶-۱۸ and match nothing.
    static func kabulDayKey(_ date: Date) -> String {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = DayGrid.kabul
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: date)
    }

    /// Whole hours only. A salon that opens at 09:15 is not a thing anyone here
    /// has ever asked for, and a full time picker per day is seven wheels on one
    /// screen.
    private func hourStepper(_ label: String, hour: Binding<Int>) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(Brand.font(11.5)).foregroundStyle(Brand.accent)
            Stepper(value: hour, in: 0...23) {
                Text(verbatim: String(format: "%02d:00", hour.wrappedValue))
                    .font(Brand.font(14, .medium))
                    .environment(\.layoutDirection, .leftToRight)
                    .foregroundStyle(Brand.ink)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private static func dayName(_ dayOfWeek: Int) -> String {
        let f = DateFormatter()
        f.locale = AppLanguage.current.locale
        let symbols = f.standaloneWeekdaySymbols ?? []
        let index = ((dayOfWeek - 1) % 7 + 7) % 7
        return symbols.indices.contains(index) ? symbols[index] : ""
    }

    private func load() {
        guard !loaded, let salon = repo.salon else { return }
        loaded = true
        name = salon.salonName
        district = salon.districtKey.isEmpty ? Areas.canonicalKey(salon.district)
                                             : salon.districtKey
        cityKey = Areas.cityOf(district)
        areaKey = salon.areaKey
        services = salon.services
        prices = Dictionary(uniqueKeysWithValues:
            salon.services.map { ($0, salon.pricePerService[$0].map(String.init) ?? "") })
        // Sorted so the week reads in order rather than in whatever order the
        // document happened to store it.
        hours = salon.workingHours.sorted { $0.dayOfWeek < $1.dayOfWeek }
        isAvailable = salon.isAvailable
        blockedDates = salon.blockedDates.sorted()
    }

    private func save() async {
        error = nil; saved = false
        // Every offered service needs a price the booking maths can use.
        // resolveServicesTotal refuses a non-positive one, so a service saved
        // without a price is a service nobody can book.
        var priced: [String: Int] = [:]
        for service in services {
            guard let value = Int(prices[service] ?? ""), value > 0 else { continue }
            priced[service] = value
        }
        guard !priced.isEmpty else { error = L.errNeedOneService.t; return }
        // A day that closes before it opens produces no slots at all, and the
        // grid would simply look empty with nothing saying why.
        if hours.contains(where: { $0.isOpen && $0.closeMinuteOfDay <= $0.openMinuteOfDay }) {
            error = L.errCloseBeforeOpen.t
            return
        }

        working = true; defer { working = false }
        do {
            try await repo.saveSalon(
                name: name.trimmingCharacters(in: .whitespaces),
                district: district, areaKey: areaKey,
                services: services.filter { priced[$0] != nil },
                prices: priced, hours: hours, blockedDates: blockedDates,
                isAvailable: isAvailable)
            saved = true
        } catch {
            self.error = L.errNetwork.t
        }
    }
}
