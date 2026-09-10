"""Google Play Developer API client.

No google-auth / cryptography on this Mac, so the service-account JWT is
signed with RS256 by shelling out to `openssl dgst -sha256 -sign`. That is
the only unusual part; everything below is plain urllib.
"""
import base64, json, os, subprocess, tempfile, time, urllib.request, urllib.error

KEY = os.path.expanduser("~/.config/safebeauty/play-publisher.json")
PKG = "com.security.stealthapp"
_token = None


def _b64(b):
    return base64.urlsafe_b64encode(b).rstrip(b"=")


def token():
    global _token
    if _token:
        return _token
    sa = json.load(open(KEY))
    now = int(time.time())
    hdr = _b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    claims = _b64(json.dumps({
        "iss": sa["client_email"],
        "scope": "https://www.googleapis.com/auth/androidpublisher",
        "aud": sa["token_uri"],
        "iat": now, "exp": now + 3600,
    }).encode())
    signing_input = hdr + b"." + claims

    with tempfile.NamedTemporaryFile("w", suffix=".pem", delete=False) as f:
        f.write(sa["private_key"])
        pem = f.name
    try:
        sig = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", pem],
            input=signing_input, capture_output=True, check=True).stdout
    finally:
        os.unlink(pem)

    assertion = signing_input + b"." + _b64(sig)
    body = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": assertion.decode(),
    }).encode()
    r = urllib.request.urlopen(urllib.request.Request(sa["token_uri"], data=body))
    _token = json.load(r)["access_token"]
    return _token


def call(method, path, body=None, raw=None, content_type=None, base=None):
    base = base or "https://androidpublisher.googleapis.com"
    url = base + path
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + token())
    if raw is not None:
        req.add_header("Content-Type", content_type or "application/octet-stream")
    elif body is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req) as r:
            txt = r.read()
            return json.loads(txt) if txt else {}
    except urllib.error.HTTPError as e:
        raise SystemExit(f"{method} {url}\n{e.code} {e.read().decode()}")
