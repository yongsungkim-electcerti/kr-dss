#!/usr/bin/env python3
"""특허-A Mode 1(WebAuthn 로컬 서명) HTTP E2E 하니스 — 브라우저 없이 검증.

합성 패스키(EC P-256)로 register→begin→finish 전 구간을 호출해 TOTAL_PASSED 를 확인한다.
RP(poc-relying-party, :8080)가 떠 있어야 한다(scripts/poc-up.ps1 -Mode mode1).

사용:
  python scripts/mode1-e2e.py
  python scripts/mode1-e2e.py "서명할 원문"

필요 패키지: requests, cryptography
"""
import base64, hashlib, json, struct, sys
import requests
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes, serialization

BASE = "http://localhost:8080"
ORIGIN = "http://localhost:8080"
RP_ID = "localhost"


def b64u(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).decode().rstrip("=")


def main() -> int:
    text = sys.argv[1] if len(sys.argv) > 1 else "Mode1 E2E 전자계약서"

    # 1) 합성 패스키
    sk = ec.generate_private_key(ec.SECP256R1())
    spki = sk.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    cred_id = b"py-cred-1"

    # 2) 등록 — 공개키(SPKI)로 CA 인증서 발급·저장
    r = requests.post(f"{BASE}/api/local/register",
                      json={"publicKey": b64u(spki), "credentialId": b64u(cred_id), "coseAlg": -7})
    r.raise_for_status()
    print("register:", r.json()["coseAlg"])

    # 3) begin — 결속 challenge
    r = requests.post(f"{BASE}/api/local/sign/begin", json={"text": text, "credentialId": b64u(cred_id)})
    r.raise_for_status()
    begin = r.json()
    print("begin    :", begin["challenge"][:16], "…")

    # 4) 어서션 합성 (challenge 서명)
    client_data = json.dumps({"type": "webauthn.get", "challenge": begin["challenge"],
                              "origin": ORIGIN, "crossOrigin": False}, separators=(",", ":")).encode()
    auth_data = hashlib.sha256(RP_ID.encode()).digest() + bytes([0x05]) + struct.pack(">I", 1)
    sig = sk.sign(auth_data + hashlib.sha256(client_data).digest(), ec.ECDSA(hashes.SHA256()))

    # 5) finish — 컨테이너 조립 + 정책 라우터 검증
    r = requests.post(f"{BASE}/api/local/sign/finish", json={
        "ticket": begin["ticket"], "webauthnCredId": b64u(cred_id),
        "clientDataJSON": b64u(client_data), "authenticatorData": b64u(auth_data),
        "signature": b64u(sig)})
    r.raise_for_status()
    rep = r.json()["report"]
    print("finish   :", rep["indication"], "path=", rep["signaturePath"])
    for c in rep.get("checks", []):
        print(("  PASS " if c["passed"] else "  FAIL "), c["name"], "—", c["message"])
    return 0 if rep["indication"] == "TOTAL_PASSED" else 1


if __name__ == "__main__":
    sys.exit(main())
