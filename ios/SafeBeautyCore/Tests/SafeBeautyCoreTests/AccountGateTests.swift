import Foundation
import Testing
@testable import SafeBeautyCore

@Suite("AccountGate mirrors Android's sign-in gate in AppNavGraph.kt")
struct AccountGateTests {

    @Test("a verified, approved provider gets the salon app")
    func verifiedProvider() {
        #expect(AccountGate.destination(role: "PROVIDER", status: "APPROVED", kycStatus: "APPROVED")
                == .providerApp)
    }

    @Test("an approved account with an unverified identity goes to KYC, in every KYC state",
          arguments: ["NONE", "PENDING", "REJECTED", "", "approved"])
    func unverifiedProvider(kyc: String) {
        // The exact account the gap let through: the Approvals tab sets status,
        // nobody has seen the tazkira.
        #expect(AccountGate.destination(role: "PROVIDER", status: "APPROVED", kycStatus: kyc)
                == .providerVerification)
    }

    @Test("a provider still awaiting her application is told so, verified or not")
    func pendingProvider() {
        #expect(AccountGate.destination(role: "PROVIDER", status: "PENDING", kycStatus: "NONE")
                == .providerPendingApproval)
        #expect(AccountGate.destination(role: "PROVIDER", status: "PENDING", kycStatus: "APPROVED")
                == .providerPendingApproval)
    }

    @Test("suspension wins over everything, verified or not")
    func suspended() {
        #expect(AccountGate.destination(role: "PROVIDER", status: "SUSPENDED", kycStatus: "APPROVED")
                == .suspended)
        #expect(AccountGate.destination(role: "CUSTOMER", status: "SUSPENDED", kycStatus: "NONE")
                == .suspended)
    }

    @Test("a declined provider does not reach the salon app")
    func declinedProvider() {
        #expect(AccountGate.destination(role: "PROVIDER", status: "REJECTED", kycStatus: "APPROVED")
                == .providerOther)
    }

    @Test("customers are not gated at sign-in — Android gates them at booking")
    func customer() {
        #expect(AccountGate.destination(role: "CUSTOMER", status: "APPROVED", kycStatus: "NONE")
                == .customerApp)
        #expect(AccountGate.destination(role: "CUSTOMER", status: "PENDING", kycStatus: "NONE")
                == .pendingApproval)
    }

    @Test("a customer may book only once verified")
    func customerBooking() {
        #expect(AccountGate.customerMayBook(kycStatus: "APPROVED"))
        for s in ["NONE", "PENDING", "REJECTED", ""] {
            #expect(!AccountGate.customerMayBook(kycStatus: s))
        }
    }

    @Test("the form is offered only where submitKyc would accept it")
    func formStates() {
        #expect(AccountGate.showsKycForm(kycStatus: "NONE"))
        #expect(AccountGate.showsKycForm(kycStatus: "REJECTED"))
        #expect(!AccountGate.showsKycForm(kycStatus: "PENDING"))
        #expect(!AccountGate.showsKycForm(kycStatus: "APPROVED"))
    }
}

@Suite("KycForm validates in KycViewModel.validate's order")
struct KycFormTests {

    @Test("complete form passes")
    func complete() {
        #expect(KycForm.firstProblem(tazkiraNumber: "123", province: "Kabul", address: "Karte Naw",
                                     hasTazkiraPhoto: true, hasSelfie: true) == nil)
    }

    @Test("the first missing thing on the page is the one reported")
    func order() {
        #expect(KycForm.firstProblem(tazkiraNumber: " ", province: "", address: "",
                                     hasTazkiraPhoto: false, hasSelfie: false) == .tazkiraNumberRequired)
        #expect(KycForm.firstProblem(tazkiraNumber: "1", province: "", address: "",
                                     hasTazkiraPhoto: false, hasSelfie: false) == .provinceRequired)
        #expect(KycForm.firstProblem(tazkiraNumber: "1", province: "K", address: "\n",
                                     hasTazkiraPhoto: false, hasSelfie: false) == .addressRequired)
        #expect(KycForm.firstProblem(tazkiraNumber: "1", province: "K", address: "A",
                                     hasTazkiraPhoto: false, hasSelfie: false) == .tazkiraPhotoRequired)
        #expect(KycForm.firstProblem(tazkiraNumber: "1", province: "K", address: "A",
                                     hasTazkiraPhoto: true, hasSelfie: false) == .selfieRequired)
    }
}
