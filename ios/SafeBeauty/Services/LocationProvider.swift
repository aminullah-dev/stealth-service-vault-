import CoreLocation
import Observation

/// One location fix, asked for only when the customer taps "Nearest".
///
/// Never at launch and never in the background. The Info.plist string says
/// exactly this — "SafeBeauty uses your location only to sort salons by
/// distance" — and it has been in `project.yml` since before anything read a
/// coordinate. This is what finally uses it.
///
/// **This asks for precise location; Android asks for COARSE.** That gap is
/// not a preference, it is what iOS offers. `desiredAccuracy` below only bounds
/// how hard CoreLocation works for a fix — it changes neither the alert nor the
/// authorisation — and the one way to ask for less, `NSLocationDefaultAccuracy\
/// Reduced`, buys 1–20 km, which is wider than Kabul and would make "nearest"
/// meaningless inside the one city every live salon is in. What the alert does
/// give her is the Precise switch, and this still works with it off, just
/// coarser. The fix is read once, held in memory, never written to Firestore
/// and never sent anywhere — which is what the Info.plist sentence promises.
@MainActor
@Observable
final class LocationProvider: NSObject, CLLocationManagerDelegate {
    /// Why a request came back with nothing. Two different sentences, because
    /// "you said no" and "the phone could not get a fix" are different
    /// problems and only one of them is fixed by walking outside.
    enum Failure: Equatable { case denied, noFix }

    private(set) var coordinate: CLLocationCoordinate2D?
    private(set) var failure: Failure?

    /// A Bool rather than the coordinate itself, so a view can watch it with
    /// `onChange` — CLLocationCoordinate2D is not Equatable.
    var hasLocation: Bool { coordinate != nil }

    private let manager = CLLocationManager()

    /// True only between asking for permission and being answered.
    ///
    /// The delegate fires `locationManagerDidChangeAuthorization` once the
    /// moment it is assigned, reporting whatever the status already is. Acting
    /// on that put "location permission is needed" under the sort row at every
    /// launch for anyone who had ever declined — a warning about a request she
    /// had not made. Nothing is reported unless she asked for something.
    private var awaitingAnswer = false

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    /// Ask for a fix, prompting for permission the first time.
    ///
    /// Safe to call repeatedly: a second tap while the system dialog is up is
    /// answered by the same dialog.
    func request() {
        failure = nil
        switch manager.authorizationStatus {
        case .notDetermined:
            // The answer arrives in locationManagerDidChangeAuthorization —
            // unless Location Services are off for the whole phone, in which
            // case iOS replaces the permission alert with "Turn On Location
            // Services?", and cancelling that changes no status and fires no
            // callback. Without this check the tap would do nothing at all,
            // with nothing on screen to say why. `locationServicesEnabled()`
            // blocks and iOS logs a warning when it is called on the main
            // thread, so it is asked off it.
            awaitingAnswer = true
            Task.detached {
                let enabled = CLLocationManager.locationServicesEnabled()
                await MainActor.run {
                    guard self.awaitingAnswer else { return }
                    if enabled {
                        self.manager.requestWhenInUseAuthorization()
                    } else {
                        self.awaitingAnswer = false
                        self.failure = .noFix
                    }
                }
            }
        case .restricted, .denied:
            failure = .denied
        case .authorizedAlways, .authorizedWhenInUse:
            fetch()
        @unknown default:
            failure = .denied
        }
    }

    /// Drop the last failure, without asking for anything.
    ///
    /// Android answers a failed Nearest with a Toast, which takes itself away
    /// after a few seconds. The note under the sort row does not, so it has to
    /// be cleared when it stops describing anything — when she picks a
    /// different sort, or resets.
    func clearFailure() { failure = nil }

    private func fetch() {
        // The cached fix first — this is the same "last known location" Android
        // reads, and it is instant. `requestLocation` is the fallback for a
        // phone that has none yet, which Android simply gives up on.
        if let cached = manager.location {
            coordinate = cached.coordinate
            return
        }
        manager.requestLocation()
    }

    private func settle(lat: Double?, lng: Double?) {
        if let lat, let lng {
            coordinate = CLLocationCoordinate2D(latitude: lat, longitude: lng)
            failure = nil
        } else {
            failure = .noFix
        }
    }

    // MARK: CLLocationManagerDelegate
    //
    // Delegate callbacks are not actor-isolated, so each one takes the plain
    // numbers out of the CoreLocation objects and hops to the main actor with
    // those. Nothing that is not a Double crosses.

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            guard self.awaitingAnswer else { return }
            switch status {
            case .authorizedAlways, .authorizedWhenInUse:
                self.awaitingAnswer = false
                self.fetch()
            case .restricted, .denied:
                self.awaitingAnswer = false
                self.failure = .denied
            case .notDetermined:
                break                            // dialog still up
            @unknown default:
                self.awaitingAnswer = false
                self.failure = .denied
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager,
                                     didUpdateLocations locations: [CLLocation]) {
        let lat = locations.last?.coordinate.latitude
        let lng = locations.last?.coordinate.longitude
        Task { @MainActor in self.settle(lat: lat, lng: lng) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager,
                                     didFailWithError error: Error) {
        // CLError.denied is the one error that is not "no fix" — it is the
        // permission answer arriving as a failure, and "try again outdoors"
        // would send her outside to solve a Settings problem.
        let denied = (error as? CLError)?.code == .denied
        Task { @MainActor in
            if denied { self.failure = .denied } else { self.settle(lat: nil, lng: nil) }
        }
    }
}
