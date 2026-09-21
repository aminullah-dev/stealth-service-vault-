#!/usr/bin/env python3
"""Load the invented demo world into a LOCAL Firestore emulator.

For the App Store screenshots of the iOS app — see README.md beside this file.

Reads safebeauty-staging with GET requests only, and writes only to an emulator
on this machine. Production is never addressed. Only documents with a `demo-`
id are copied, and a review/offer/gallery row only if its salon was copied, so
a real salon that ever lands in staging cannot reach a screenshot.

The emulator's rules are set from here, through the emulator's own API, rather
than from a firebase.json: this repository's default Firebase project is
production, and a firebase.json holding open read rules is one `firebase
deploy` away from shipping them there.
"""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

STAGING = "safebeauty-staging"
PROJECT = "demo-screens"          # must match options.projectID in SafeBeautyApp.swift
EMULATOR = os.environ.get("FIRESTORE_EMULATOR_HOST", "127.0.0.1:8080")
COLLECTIONS = ["salons", "reviews", "salon_offers", "salon_gallery"]

# Reads open, because the simulator can never be signed in (securityd -34018,
# see CLAUDE.md) and the real rules require isSignedIn(). Writes closed: the
# app writes nothing while browsing, and seeding uses the admin bearer below,
# which the emulator exempts from rules.
RULES = """rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read: if true;
      allow write: if false;
    }
  }
}
"""


def _request(method, url, body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            text = res.read().decode()
            return res.status, (json.loads(text) if text else {})
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def staging_list(collection, token):
    """Every document in one staging collection. GET only."""
    base = (f"https://firestore.googleapis.com/v1/projects/{STAGING}"
            f"/databases/(default)/documents/{collection}?pageSize=300")
    assert f"/projects/{STAGING}/" in base
    docs, page = [], ""
    while True:
        status, body = _request("GET", base + (f"&pageToken={page}" if page else ""), headers={
            "Authorization": f"Bearer {token}",
            "x-goog-user-project": STAGING,
        })
        if status != 200:
            sys.exit(f"staging GET {collection} failed: {status} {str(body)[:300]}")
        docs += body.get("documents", [])
        page = body.get("nextPageToken", "")
        if not page:
            return docs


def emulator(method, path, body=None, admin=True):
    host = EMULATOR.split(":")[0]
    if host not in ("127.0.0.1", "localhost"):
        sys.exit(f"REFUSING: emulator host {EMULATOR} is not this machine")
    headers = {"Authorization": "Bearer owner"} if admin else {}
    return _request(method, f"http://{EMULATOR}{path}", body, headers)


def field(doc, name):
    value = doc.get("fields", {}).get(name, {})
    return next(iter(value.values()), None) if value else None


def main():
    token = subprocess.run(["gcloud", "auth", "print-access-token"],
                           capture_output=True, text=True, check=True).stdout.strip()
    db = f"/v1/projects/{PROJECT}/databases/(default)/documents"

    status, body = emulator("PUT", f"/emulator/v1/projects/{PROJECT}:securityRules",
                            {"rules": {"files": [{"name": "screenshots.rules", "content": RULES}]}})
    if status != 200:
        sys.exit(f"could not set emulator rules: {status} {body}")
    # Start empty, so the emulator holds the demo world and nothing else.
    status, body = emulator("DELETE", f"/emulator/v1/projects/{PROJECT}/databases/(default)/documents")
    if status != 200:
        sys.exit(f"could not clear emulator: {status} {body}")

    salon_ids = set()
    for collection in COLLECTIONS:
        copied, skipped = 0, []
        for doc in staging_list(collection, token):
            doc_id = doc["name"].rsplit("/", 1)[1]
            salon = doc_id if collection == "salons" else field(doc, "salonId")
            if not doc_id.startswith("demo-") or (collection != "salons" and salon not in salon_ids):
                skipped.append(doc_id)
                continue
            status, body = emulator("PATCH", f"{db}/{collection}/{doc_id}", {"fields": doc["fields"]})
            if status != 200:
                sys.exit(f"emulator write {collection}/{doc_id} failed: {status} {body}")
            if collection == "salons":
                salon_ids.add(doc_id)
            copied += 1
        print(f"{collection:14} copied {copied:3}" + (f"   SKIPPED (not demo): {skipped}" if skipped else ""))

    # Read back the way the app will: no credential at all, so through the rules.
    status, body = emulator("GET", f"{db}/salons?pageSize=300", admin=False)
    names = sorted(field(d, "salonName") for d in body.get("documents", [])) if status == 200 else []
    print(f"unauthenticated read of salons: HTTP {status}, {len(names)} salons: {', '.join(names)}")
    status, _ = emulator("PATCH", f"{db}/salons/write-probe",
                         {"fields": {"x": {"stringValue": "y"}}}, admin=False)
    print(f"unauthenticated write probe: HTTP {status} (403 expected)")
    if len(names) != len(salon_ids) or status != 403:
        sys.exit("emulator is not in the expected state")


if __name__ == "__main__":
    main()
