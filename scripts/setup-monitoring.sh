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

# kind|human description|why it is worth waking someone
ALERTS=(
  "BOOKING_FAILED|Booking failed|A customer tried to book and could not. This is lost revenue and a lost customer, one per event."
  "PAYMENT_FAILED|Payment path failed|Money moved, or failed to, without the record agreeing. Every event is someone's money."
  "BACKUP_FAILED|Backup failed|The nightly export did not complete. Discovered any later than now means discovering it during a restore."
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

echo
echo "── Done."
echo "   If the channel is new, Google has emailed $EMAIL to verify it."
echo "   Alerts do not arrive until that link is clicked."
echo
echo "   Prove the whole path works by calling adminTestAlert from the admin"
echo "   console (Health tab). A notification should arrive within ~5 minutes."
