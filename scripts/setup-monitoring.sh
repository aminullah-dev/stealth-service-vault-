#!/usr/bin/env bash
#
# Create the alerting that tells a person when SafeBeauty is failing.
#
#   ./scripts/setup-monitoring.sh safebeauty          you@example.com
#   ./scripts/setup-monitoring.sh safebeauty-staging  you@example.com
#
# Idempotent: existing channels, metrics and policies are left alone, so this
# can be re-run after adding a new alert kind.
#
# The metric matches on jsonPayload.alert alone — the stable label written by
# alertable() in functions/index.js — never on message text and never on the
# runtime's resource type. Both change without anyone thinking about alerting,
# and a filter that stops matching stops alerting silently, which is
# indistinguishable from nothing going wrong.
#
# Monitoring does require a resource restriction on the POLICY condition, so
# that one enumerates every resource these logs can legitimately come from:
# both Cloud Functions generations, plus global for a drill written directly to
# the log. Pinning a single type there was the same trap one level down.
#
# Deliberately few. Three alerts that each justify interrupting someone are
# worth more than twenty that get filtered into a folder.

set -euo pipefail

PROJECT="${1:-}"
EMAIL="${2:-}"

if [[ -z "$PROJECT" || -z "$EMAIL" ]]; then
  echo "usage: $0 <project-id> <email>" >&2
  exit 2
fi

# Two catch-all alerts that key off nothing anyone had to anticipate. Every
# alert below them fires on a label chosen in advance, which means they only
# ever catch failures someone already thought of. Three jobs in this project
# were dead from the day they were written and none of the labelled alerts said
# so, because a crash is not a labelled event.
# Derived from the source, not typed out. The hand-written list had drifted to
# twelve names while fifteen onSchedule functions were deployed, so a crash in
# measureSalonReliability, resumeBroadcasts or rotateWaitlistOffers matched no
# policy and woke nobody — and this is the alert that exists precisely for jobs
# nothing else notices. A list that has to be updated by hand is a list that
# will be wrong again.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEDULED_SERVICES="$(grep -hoE '^exports\.[A-Za-z_][A-Za-z0-9_]*[[:space:]]*=[[:space:]]*onSchedule' \
  "$REPO_ROOT"/functions/domains/*.js \
  | sed -E 's/^exports\.([A-Za-z_][A-Za-z0-9_]*).*/\1/' \
  | tr 'A-Z' 'a-z' | sort -u | paste -sd'|' -)"
if [[ -z "$SCHEDULED_SERVICES" ]]; then
  # An empty alternation would compile to a filter that matches nothing, which
  # looks exactly like a healthy system.
  echo "could not derive the scheduled-function list from functions/domains — refusing to write an alert that matches nothing" >&2
  exit 3
fi
echo "── scheduled jobs watched: $(tr '|' ' ' <<<"$SCHEDULED_SERVICES")"

CATCH_ALL=(
  "safebeauty_scheduled_job_error|A scheduled job failed|0|severity>=ERROR AND resource.type=\"cloud_run_revision\" AND resource.labels.service_name=~\"^(${SCHEDULED_SERVICES})\$\"|A background job logged an error. These run on a timer, rarely, and nothing else notices when one stops working."
  "safebeauty_function_error|Function errors are spiking|10|severity>=ERROR AND resource.type=\"cloud_run_revision\"|More than ten function errors in ten minutes. A catch-all, because the labelled alerts only fire for failures someone anticipated."
)

# kind|human description|why it is worth waking someone
ALERTS=(
  "BOOKING_FAILED|Booking failed|A customer tried to book and could not. This is lost revenue and a lost customer, one per event."
  "SLOT_MISMATCH|A booking landed at a time the salon does not offer|The booking SUCCEEDED — this is not an outage. The app builds its slot grid from the device clock and the server checks it against the salon's stored hours in Kabul time, so this fires when the two disagree: a phone on another timezone, or a salon whose week changed under a live booking. Deliberately not BOOKING_FAILED, which is documented as a customer who could not book; conflating them makes a working product page as a failure and inflates any count watching that label."
  "DUPLICATE_PHONE|Two accounts share one phone number|Login resolves by subscriber number, so both people are ambiguous to it: whoever the index returns first wins and the other is locked out of an account that still exists. Cannot be resolved automatically — only a person knows whether it is one customer twice or two customers who typed the same number."
  "REGISTRATION_ORPHANED|A sign-up left a credential with no account behind it|registerAccount created the Firebase Auth account, failed to write the profile, and then could not delete the credential either. That person now holds a login that resolves to nothing and cannot register the same number again, because the number is not taken and the account is not reachable — there is no screen anywhere that explains this and no way for them to fix it. Rare by construction (the rollback runs server-side), which is exactly why it must page rather than sit in a log: 106 accounts reached this state under the old client-side flow before anyone noticed, over three months."
  "PAYMENT_FAILED|Payment path failed|Money moved, or failed to, without the record agreeing. Every event is someone's money."
  "BACKUP_FAILED|Backup failed|The nightly export did not complete. Discovered any later than now means discovering it during a restore."
  "PASSWORD_ROTATION_STUCK|A password change left an account unable to sign in|The Firestore pair (salt + pinHash) and the Firebase Auth password derived from them stopped agreeing, so NEITHER password opens the account: the new one fails the stored-hash check, and the old one passes it, is handed the old salt, derives an Auth password Firebase refuses, and is rejected. The account looks completely ordinary and simply refuses both passwords, so nobody finds this except the person locked out of it. Only adminResetPassword recovers it. Rare by construction — the rotation writes Firestore first precisely so it can put it back — which is why one event is worth waking someone."
  "INTEGRITY_CRITICAL|Integrity sweep found something critical|Two records that should agree do not, in a way that costs money."
  "ALERT_PIPELINE_TEST|Alerting drill|A deliberate test of this pipeline, fired by adminTestAlert. Not a real failure."
)

echo "── Notification channel for $EMAIL"
CHANNEL="$(gcloud beta monitoring channels list --project="$PROJECT" \
            --filter="labels.email_address='$EMAIL' AND type='email'" \
            --format='value(name)' 2>/dev/null | head -1)"

if [[ -z "$CHANNEL" ]]; then
  tmp="$(mktemp)"
  cat > "$tmp" <<JSON
{
  "type": "email",
  "displayName": "SafeBeauty on-call",
  "description": "Where SafeBeauty reports its own failures.",
  "labels": { "email_address": "$EMAIL" },
  "enabled": true
}
JSON
  CHANNEL="$(gcloud beta monitoring channels create \
              --channel-content-from-file="$tmp" --project="$PROJECT" \
              --format='value(name)' 2>&1 | grep -oE 'projects/[^]]*' | head -1)"
  rm -f "$tmp"
  echo "   created"
else
  echo "   exists"
fi
echo "   $CHANNEL"

for entry in "${ALERTS[@]}"; do
  IFS='|' read -r KIND TITLE WHY <<< "$entry"
  METRIC="safebeauty_$(echo "$KIND" | tr '[:upper:]' '[:lower:]')"

  echo "── $KIND"

  if gcloud logging metrics describe "$METRIC" --project="$PROJECT" >/dev/null 2>&1; then
    echo "   metric exists"
  else
    gcloud logging metrics create "$METRIC" --project="$PROJECT" \
      --description="Count of ${KIND} alerts emitted by alertable() in functions/index.js" \
      --log-filter="severity>=ERROR AND jsonPayload.alert=\"${KIND}\"" \
      >/dev/null
    echo "   metric created"
  fi

  if gcloud alpha monitoring policies list --project="$PROJECT" \
       --filter="displayName='SafeBeauty: $TITLE'" --format='value(name)' 2>/dev/null | grep -q .; then
    echo "   policy exists"
    continue
  fi

  tmp="$(mktemp)"
  cat > "$tmp" <<JSON
{
  "displayName": "SafeBeauty: $TITLE",
  "documentation": {
    "content": "$WHY\n\nMatched on jsonPayload.alert=\"$KIND\", written by alertable() in functions/index.js. If this alert stops firing, check that the label still exists at the call site.",
    "mimeType": "text/markdown"
  },
  "combiner": "OR",
  "conditions": [
    {
      "displayName": "$KIND occurred",
      "conditionThreshold": {
        "filter": "metric.type=\"logging.googleapis.com/user/$METRIC\" AND resource.type = one_of(\"cloud_run_revision\",\"cloud_function\",\"global\")",
        "comparison": "COMPARISON_GT",
        "thresholdValue": 0,
        "duration": "0s",
        "aggregations": [
          {
            "alignmentPeriod": "300s",
            "perSeriesAligner": "ALIGN_SUM",
            "crossSeriesReducer": "REDUCE_SUM"
          }
        ],
        "trigger": { "count": 1 }
      }
    }
  ],
  "alertStrategy": {
    "autoClose": "1800s"
  },
  "notificationChannels": ["$CHANNEL"],
  "enabled": true
}
JSON
  gcloud alpha monitoring policies create --policy-from-file="$tmp" --project="$PROJECT" >/dev/null
  rm -f "$tmp"
  echo "   policy created"
done

for entry in "${CATCH_ALL[@]}"; do
  IFS='|' read -r METRIC TITLE THRESHOLD FILTER WHY <<< "$entry"
  echo "── $TITLE"

  if gcloud logging metrics describe "$METRIC" --project="$PROJECT" >/dev/null 2>&1; then
    echo "   metric exists"
  else
    gcloud logging metrics create "$METRIC" --project="$PROJECT" \
      --description="$WHY" --log-filter="$FILTER" >/dev/null
    echo "   metric created"
  fi

  if gcloud alpha monitoring policies list --project="$PROJECT" \
       --filter="displayName='SafeBeauty: $TITLE'" --format='value(name)' 2>/dev/null | grep -q .; then
    echo "   policy exists"
    continue
  fi

  tmp="$(mktemp)"
  cat > "$tmp" <<JSON
{
  "displayName": "SafeBeauty: $TITLE",
  "documentation": { "content": "$WHY", "mimeType": "text/markdown" },
  "combiner": "OR",
  "conditions": [{
    "displayName": "$TITLE",
    "conditionThreshold": {
      "filter": "metric.type=\"logging.googleapis.com/user/$METRIC\" AND resource.type = one_of(\"cloud_run_revision\",\"cloud_function\",\"global\")",
      "comparison": "COMPARISON_GT", "thresholdValue": $THRESHOLD, "duration": "0s",
      "aggregations": [{ "alignmentPeriod": "600s", "perSeriesAligner": "ALIGN_SUM", "crossSeriesReducer": "REDUCE_SUM" }],
      "trigger": { "count": 1 }
    }
  }],
  "alertStrategy": { "autoClose": "3600s" },
  "notificationChannels": ["$CHANNEL"],
  "enabled": true
}
JSON
  gcloud alpha monitoring policies create --policy-from-file="$tmp" --project="$PROJECT" >/dev/null
  rm -f "$tmp"
  echo "   policy created"
done

echo
echo "── Done."
echo "   If the channel is new, Google has emailed $EMAIL to verify it."
echo "   Alerts do not arrive until that link is clicked."
echo
echo "   Prove the whole path works by calling adminTestAlert from the admin"
echo "   console (Health tab). A notification should arrive within ~5 minutes."
