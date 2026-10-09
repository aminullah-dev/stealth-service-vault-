import SwiftUI
import UIKit

/// The camera, for a photograph taken now rather than chosen from the library.
///
/// Android takes the KYC selfie with the camera (`TakePicturePreview`) and iOS
/// only ever offered the photo library — so the "photo of you" an admin compared
/// against the tazkira could be any picture on the phone. SwiftUI has no camera
/// view of its own; UIImagePickerController is still the supported way to take
/// one photograph and hand it back. NSCameraUsageDescription is already in
/// project.yml for exactly this.
///
/// Callers check `isAvailable` first: the simulator and some iPads have no
/// camera, and there the library is the only option rather than a broken button.
struct CameraPicker: UIViewControllerRepresentable {
    var preferFront = true
    let onPicked: (UIImage?) -> Void

    static var isAvailable: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        if preferFront, UIImagePickerController.isCameraDeviceAvailable(.front) {
            picker.cameraDevice = .front
        }
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPicked: onPicked) }

    @MainActor
    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onPicked: (UIImage?) -> Void
        init(onPicked: @escaping (UIImage?) -> Void) { self.onPicked = onPicked }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            onPicked(info[.originalImage] as? UIImage)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onPicked(nil)
        }
    }
}
