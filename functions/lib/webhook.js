// Pure decision helpers for the HesabPay webhook (hesabPayWebhook in index.js),
// extracted so the payment success/failure signals and amount-binding can be
// unit-tested without Firebase or the network. These make no I/O — they only
// interpret a callback payload.

// True when the callback clearly signals a successful payment. HesabPay marks
// success with success:true / status_code:10; legacy status strings are a
// defensive fallback.
function isPaidSignal(payload) {
  const p = payload || {};
  const statusStr = String(p.status || "").toUpperCase();
  return (
    p.success === true ||
    p.status_code === 10 ||
    statusStr === "PAID" || statusStr === "SUCCESS" || statusStr === "COMPLETED"
  );
}

// True only on an EXPLICIT failure. An unknown/intermediate callback is neither
// paid nor failed (a no-op), so it can't destroy a payment that is still in
// flight — which would show the customer a false "payment failed".
function isFailSignal(payload) {
  const p = payload || {};
  const statusStr = String(p.status || "").toUpperCase();
  return (
    p.success === false ||
    statusStr === "FAILED" || statusStr === "CANCELLED" || statusStr === "DECLINED"
  );
}

// True when a paid callback reports LESS than the recorded price — the real
// attack (settle a 5000 booking with a 1 payment). A non-finite reported amount
// is NOT treated as underpayment, because HesabPay's amount unit isn't
// guaranteed and a false reject would block real customers; the transaction_id
// replay guard is the primary defense.
function isUnderpaid(reportedAmount, expectedAmount) {
  const r = Number(reportedAmount);
  if (!Number.isFinite(r)) return false;
  return r < Number(expectedAmount);
}

module.exports = { isPaidSignal, isFailSignal, isUnderpaid };
