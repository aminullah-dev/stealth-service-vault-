"""App Store Connect API client — enough of it to run a release without Xcode.

Why this exists: on 2026-09-09 Xcode's Apple ID session dropped mid-afternoon
and `xcodebuild` answered "No Accounts" for the rest of the day while the GUI
still showed the account signed in. An API key has no session to lose. See
DEPLOY.md for the upload command and for why the key must be Admin, not App
Manager.

The key itself lives in ~/.appstoreconnect/private_keys/ and is never read by
anything here except openssl, which signs the JWT. *.p8 is gitignored.

    from scripts.asc import call
    call("GET", "/v1/apps/6810050614/appStoreVersions")

`upload()` is separate from `call()` on purpose: Apple's asset URLs are
pre-signed, and sending our own Authorization header alongside the AWS
signature makes the PUT fail with a bare 400 and no explanation.
"""
import base64, hashlib, json, subprocess, time, urllib.request, urllib.error, os, sys

KEY_ID = "4SKX647AH5"
ISSUER = "0e948a64-b5af-4815-bc6c-f7943bb4f637"
P8 = os.path.expanduser(f"~/.appstoreconnect/private_keys/AuthKey_{KEY_ID}.p8")
BASE = "https://api.appstoreconnect.apple.com"

def _b64(b): return base64.urlsafe_b64encode(b).rstrip(b"=")

def token():
    now = int(time.time())
    hdr = _b64(json.dumps({"alg":"ES256","kid":KEY_ID,"typ":"JWT"},separators=(",",":")).encode())
    pay = _b64(json.dumps({"iss":ISSUER,"iat":now,"exp":now+900,"aud":"appstoreconnect-v1"},separators=(",",":")).encode())
    signing_input = hdr + b"." + pay
    der = subprocess.run(["openssl","dgst","-sha256","-sign",P8],
                         input=signing_input, capture_output=True, check=True).stdout
    # DER SEQUENCE{INTEGER r, INTEGER s} -> raw r||s, 32 bytes each
    def rd(buf, i):
        assert buf[i] == 0x02
        ln = buf[i+1]; v = buf[i+2:i+2+ln]
        return v.lstrip(b"\x00").rjust(32, b"\x00"), i+2+ln
    i = 2 if der[1] < 0x80 else 3
    r, i = rd(der, i); s, _ = rd(der, i)
    return (signing_input + b"." + _b64(r+s)).decode()

def call(method, path, body=None, raw=None, headers=None, full_url=None):
    url = full_url or (BASE + path)
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    h = {"Authorization": f"Bearer {token()}"}
    if body is not None: h["Content-Type"] = "application/json"
    if headers: h.update(headers)
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            b = r.read()
            return json.loads(b) if b else {}
    except urllib.error.HTTPError as e:
        b = e.read().decode()
        raise SystemExit(f"{method} {url}\nHTTP {e.code}\n{b[:900]}")

def upload(op, chunk):
    """PUT to Apple's pre-signed asset URL. NO Authorization header — the URL
    carries its own AWS signature and an extra auth header invalidates it."""
    h = {x["name"]: x["value"] for x in op.get("requestHeaders", [])}
    req = urllib.request.Request(op["url"], data=chunk, headers=h, method=op["method"])
    try:
        with urllib.request.urlopen(req) as r: return r.status
    except urllib.error.HTTPError as e:
        raise SystemExit(f"{op['method']} asset upload\nHTTP {e.code}\n{e.read().decode()[:500]}")
