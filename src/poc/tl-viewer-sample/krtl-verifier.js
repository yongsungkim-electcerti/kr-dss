/*
 * KR-TL 수신 검증 모듈 (PDF 뷰어·이용기관용 샘플)
 *
 * IF-07(설계 28)로 받은 TL에 대해 "TL과 연관된 검증 단계"만 수행한다. PDF 서명 무결성·가입자 인증서 경로·
 * OCSP 검증은 하지 않는다. 외부 라이브러리 없이 브라우저 DOMParser·WebCrypto만 사용한다(폐쇄망 고려).
 *
 * 수행 단계
 *   1 XML 형식            TrustServiceStatusList 루트·Id
 *   2 서명 구조            TS 119 612 Annex B.1.0 (enveloped, Reference URI=#루트Id, Transform 2개, exc-c14n)
 *   3 참조 다이제스트      모든 ds:Reference (TL 본문, XAdES SignedProperties)
 *   4 서명값              ds:SignedInfo exc-c14n → ECDSA/RSA 검증
 *   5 서명 인증서 결속    xades:SigningCertificateV2 다이제스트 = KeyInfo 서명 인증서
 *   6 서명자 체인         KeyInfo 인증서 → 사전 설정 신뢰앵커(KISA TL RootCA), 유효기간·CA·EKU tslSigning
 *   7 목록 값             발행 시각 미래, NextUpdate 순서·상한, 만료
 *   8 보유본 대비         순번 역행·동일 번호 다른 내용·발행 시각 역행 (최초 수신이면 비교 불가 명시)
 *   9 서비스 정보         상태 URI·critical 확장 지원, 이력 순서, 보유본 대비 소급 변경·이력 삭제
 *
 * 제한: 폐기 확인(CRL/OCSP) 없음, 이름 제약·정책 처리 없음, XSD 전체 검증 없음. 데모용 최소 PKIX 경로 검사다.
 */

export const NS = {
    TSL: "http://uri.etsi.org/02231/v2#",
    DS: "http://www.w3.org/2000/09/xmldsig#",
    XADES: "http://uri.etsi.org/01903/v1.3.2#",
    XML: "http://www.w3.org/XML/1998/namespace",
    XMLNS: "http://www.w3.org/2000/xmlns/",
};

const ALG = {
    EXC_C14N: "http://www.w3.org/2001/10/xml-exc-c14n#",
    ENVELOPED: "http://www.w3.org/2000/09/xmldsig#enveloped-signature",
    SHA256: "http://www.w3.org/2001/04/xmlenc#sha256",
    ECDSA_SHA256: "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256",
    RSA_SHA256: "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
};

/** PoC 프로파일 v1 (profile-poc-v1.md). 공식 URI가 아니다. */
export const POC_PROFILE = {
    prefix: "urn:example:kr-tl:poc:v1:",
    extensionNs: "urn:example:kr-tl:poc:extensions:v1",
    statuses: {
        "urn:example:kr-tl:poc:v1:status:accredited": "ACCREDITED",
        "urn:example:kr-tl:poc:v1:status:suspended": "SUSPENDED",
        "urn:example:kr-tl:poc:v1:status:withdrawn": "WITHDRAWN",
    },
    serviceTypes: {
        "urn:example:kr-tl:poc:v1:service:ca": "CA",
        "urn:example:kr-tl:poc:v1:service:ocsp": "OCSP",
        "urn:example:kr-tl:poc:v1:service:tsa": "TSA",
    },
    knownExtensions: ["TrustDomain", "TrustPointLevel"],
};

const OID = {
    EKU_TSL_SIGNING: "0.4.0.2231.3.0",
    EC_PUBLIC_KEY: "1.2.840.10045.2.1",
    RSA_ENCRYPTION: "1.2.840.113549.1.1.1",
    ECDSA_SHA256: "1.2.840.10045.4.3.2",
    ECDSA_SHA384: "1.2.840.10045.4.3.3",
    RSA_SHA256: "1.2.840.113549.1.1.11",
    P256: "1.2.840.10045.3.1.7",
    P384: "1.3.132.0.34",
    BASIC_CONSTRAINTS: "2.5.29.19",
    KEY_USAGE: "2.5.29.15",
    EXT_KEY_USAGE: "2.5.29.37",
    SKI: "2.5.29.14",
    AKI: "2.5.29.35",
};

const MAX_NEXT_UPDATE_MONTHS = 6;
/** xades:CertDigest에 허용하는 다이제스트(XMLDSig URI → WebCrypto). */
const DIGESTS = {
    "http://www.w3.org/2001/04/xmlenc#sha256": "SHA-256",
    "http://www.w3.org/2001/04/xmldsig-more#sha384": "SHA-384",
    "http://www.w3.org/2001/04/xmlenc#sha512": "SHA-512",
};
const enc = new TextEncoder();

// ===================================================================== 바이트·해시

export function base64ToBytes(text) {
    const clean = String(text).replace(/\s+/g, "");
    const binary = atob(clean);
    const out = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
    return out;
}

export function bytesToBase64(bytes) {
    let binary = "";
    for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
    return btoa(binary);
}

export const toHex = (bytes) => Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");

export async function sha256(bytes) {
    return new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
}

const equalBytes = (a, b) => a.length === b.length && a.every((v, i) => v === b[i]);

// ===================================================================== DER · X.509 (최소 구현)

function readTlv(bytes, offset) {
    const tag = bytes[offset];
    let length = bytes[offset + 1];
    let header = 2;
    if (length & 0x80) {
        const count = length & 0x7f;
        length = 0;
        for (let i = 0; i < count; i++) length = length * 256 + bytes[offset + 2 + i];
        header += count;
    }
    const start = offset + header;
    if (start + length > bytes.length) throw new Error("DER 길이가 데이터를 넘습니다.");
    return {tag, start, end: start + length, raw: bytes.subarray(offset, start + length), value: bytes.subarray(start, start + length)};
}

function children(bytes, tlv) {
    const out = [];
    for (let p = tlv.start; p < tlv.end;) {
        const child = readTlv(bytes, p);
        out.push(child);
        p = child.end;
    }
    return out;
}

function oid(value) {
    const parts = [Math.floor(value[0] / 40), value[0] % 40];
    let n = 0;
    for (let i = 1; i < value.length; i++) {
        n = n * 128 + (value[i] & 0x7f);
        if (!(value[i] & 0x80)) { parts.push(n); n = 0; }
    }
    return parts.join(".");
}

function time(tlv) {
    const s = new TextDecoder().decode(tlv.value);
    if (tlv.tag === 0x17) {               // UTCTime YYMMDDHHMMSSZ
        const y = Number(s.slice(0, 2));
        return new Date(Date.UTC(y >= 50 ? 1900 + y : 2000 + y, Number(s.slice(2, 4)) - 1, Number(s.slice(4, 6)),
            Number(s.slice(6, 8)), Number(s.slice(8, 10)), Number(s.slice(10, 12))));
    }
    return new Date(Date.UTC(Number(s.slice(0, 4)), Number(s.slice(4, 6)) - 1, Number(s.slice(6, 8)),
        Number(s.slice(8, 10)), Number(s.slice(10, 12)), Number(s.slice(12, 14))));
}

const RDN_NAMES = {"2.5.4.3": "CN", "2.5.4.6": "C", "2.5.4.10": "O", "2.5.4.11": "OU", "2.5.4.5": "SERIALNUMBER"};

function nameText(bytes, nameTlv) {
    const parts = [];
    for (const set of children(bytes, nameTlv)) {
        for (const atv of children(bytes, set)) {
            const [type, value] = children(bytes, atv);
            const label = RDN_NAMES[oid(type.value)] || oid(type.value);
            parts.push(`${label}=${new TextDecoder().decode(value.value)}`);
        }
    }
    return parts.reverse().join(", ");
}

/** X.509 인증서에서 검증에 필요한 값만 꺼낸다. */
export function parseCertificate(der) {
    const cert = readTlv(der, 0);
    const [tbs, sigAlg, sigValue] = children(der, cert);
    let fields = children(der, tbs);
    if (fields[0].tag === 0xa0) fields = fields.slice(1);
    const [serial, , issuer, validity, subject, spki] = fields;
    const [notBefore, notAfter] = children(der, validity);
    const [spkiAlg] = children(der, spki);
    const spkiAlgParts = children(der, spkiAlg);
    const result = {
        der,
        tbs: tbs.raw,
        signatureAlgorithm: oid(children(der, sigAlg)[0].value),
        signature: sigValue.value.subarray(1),          // BIT STRING 미사용 비트 바이트 제외
        serial: toHex(serial.value),
        issuerRaw: issuer.raw,
        subjectRaw: subject.raw,
        issuer: nameText(der, issuer),
        subject: nameText(der, subject),
        notBefore: time(notBefore),
        notAfter: time(notAfter),
        spki: spki.raw,
        keyAlgorithm: oid(spkiAlgParts[0].value),
        curve: spkiAlgParts[1] && spkiAlgParts[1].tag === 0x06 ? oid(spkiAlgParts[1].value) : null,
        ca: false,
        pathLen: null,
        keyUsage: null,
        extKeyUsage: null,
        ski: null,
        aki: null,
    };
    const extContainer = fields.find((f) => f.tag === 0xa3);
    if (extContainer) {
        const [extensions] = children(der, extContainer);
        for (const ext of children(der, extensions)) {
            const parts = children(der, ext);
            const id = oid(parts[0].value);
            const value = readTlv(der, parts[parts.length - 1].start);   // OCTET STRING 안의 값
            if (id === OID.BASIC_CONSTRAINTS) {
                const bc = children(der, value);
                result.ca = bc.length > 0 && bc[0].tag === 0x01 && bc[0].value[0] !== 0;
                const len = bc.find((x) => x.tag === 0x02);
                if (len) result.pathLen = len.value.reduce((n, b) => n * 256 + b, 0);
            } else if (id === OID.KEY_USAGE) {
                const bits = value.value;
                result.keyUsage = {digitalSignature: !!(bits[1] & 0x80), keyCertSign: !!(bits[1] & 0x04)};
            } else if (id === OID.EXT_KEY_USAGE) {
                result.extKeyUsage = children(der, value).map((x) => oid(x.value));
            } else if (id === OID.SKI) {
                result.ski = toHex(value.value);
            } else if (id === OID.AKI) {
                const keyId = children(der, value).find((x) => x.tag === 0x80);
                if (keyId) result.aki = toHex(keyId.value);
            }
        }
    }
    return result;
}

export function pemToDerList(text) {
    const out = [];
    const re = /-----BEGIN CERTIFICATE-----([\s\S]*?)-----END CERTIFICATE-----/g;
    let m;
    while ((m = re.exec(text)) !== null) out.push(base64ToBytes(m[1]));
    return out;
}

/** DER ECDSA 서명(SEQUENCE{r,s})을 WebCrypto가 쓰는 r||s 고정 길이로 바꾼다. */
function ecdsaDerToRaw(der, size) {
    const seq = readTlv(der, 0);
    const [r, s] = children(der, seq);
    const fit = (v) => {
        let b = v.value;
        while (b.length > size && b[0] === 0) b = b.subarray(1);
        const out = new Uint8Array(size);
        out.set(b, size - b.length);
        return out;
    };
    const raw = new Uint8Array(size * 2);
    raw.set(fit(r), 0);
    raw.set(fit(s), size);
    return raw;
}

async function importKey(cert, hash = "SHA-256") {
    if (cert.keyAlgorithm === OID.EC_PUBLIC_KEY) {
        const namedCurve = cert.curve === OID.P384 ? "P-384" : "P-256";
        return {key: await crypto.subtle.importKey("spki", cert.spki, {name: "ECDSA", namedCurve}, false, ["verify"]),
            ec: true, size: namedCurve === "P-384" ? 48 : 32, hash};
    }
    if (cert.keyAlgorithm === OID.RSA_ENCRYPTION) {
        return {key: await crypto.subtle.importKey("spki", cert.spki, {name: "RSASSA-PKCS1-v1_5", hash}, false, ["verify"]),
            ec: false, hash};
    }
    throw new Error("지원하지 않는 공개키 알고리즘: " + cert.keyAlgorithm);
}

/** subject 인증서의 서명을 issuer 공개키로 검증. */
async function certSignedBy(subject, issuer) {
    if (!equalBytes(subject.issuerRaw, issuer.subjectRaw)) return false;
    const hash = subject.signatureAlgorithm === OID.ECDSA_SHA384 ? "SHA-384" : "SHA-256";
    const k = await importKey(issuer, hash);
    if (k.ec) {
        return crypto.subtle.verify({name: "ECDSA", hash}, k.key, ecdsaDerToRaw(subject.signature, k.size), subject.tbs);
    }
    return crypto.subtle.verify({name: "RSASSA-PKCS1-v1_5"}, k.key, subject.signature, subject.tbs);
}

// ===================================================================== Exclusive XML Canonicalization 1.0

const escapeText = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/\r/g, "&#xD;");
const escapeAttr = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/"/g, "&quot;")
    .replace(/\t/g, "&#x9;").replace(/\n/g, "&#xA;").replace(/\r/g, "&#xD;");

/**
 * exc-c14n(주석 제외, InclusiveNamespaces 없음). excluded 요소(enveloped 서명)는 출력하지 않는다.
 * rendered: 출력 조상이 이미 선언한 prefix → URI.
 */
function excC14n(node, excluded = null, rendered = new Map()) {
    if (node.nodeType === Node.TEXT_NODE || node.nodeType === Node.CDATA_SECTION_NODE) return escapeText(node.data);
    if (node.nodeType === Node.PROCESSING_INSTRUCTION_NODE) return `<?${node.target}${node.data ? " " + node.data : ""}?>`;
    if (node.nodeType !== Node.ELEMENT_NODE || node === excluded) return "";

    const used = new Map();                      // 이 요소가 눈에 보이게 쓰는 prefix → URI
    used.set(node.prefix || "", node.namespaceURI || "");
    const attrs = [];
    for (const a of Array.from(node.attributes)) {
        if (a.namespaceURI === NS.XMLNS) continue;
        attrs.push(a);
        if (a.prefix && a.prefix !== "xml") used.set(a.prefix, a.namespaceURI);
    }
    const decls = [];
    const next = new Map(rendered);
    for (const [prefix, uri] of used) {
        const already = rendered.has(prefix) ? rendered.get(prefix) : (prefix === "" ? "" : undefined);
        if (already === uri) continue;
        decls.push([prefix, uri]);
        next.set(prefix, uri);
    }
    decls.sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0));
    attrs.sort((a, b) => {
        const ua = a.namespaceURI || "", ub = b.namespaceURI || "";
        if (ua !== ub) return ua < ub ? -1 : 1;
        return a.localName < b.localName ? -1 : a.localName > b.localName ? 1 : 0;
    });
    let out = "<" + node.nodeName;
    for (const [prefix, uri] of decls) out += prefix ? ` xmlns:${prefix}="${escapeAttr(uri)}"` : ` xmlns="${escapeAttr(uri)}"`;
    for (const a of attrs) out += ` ${a.name}="${escapeAttr(a.value)}"`;
    out += ">";
    for (const child of Array.from(node.childNodes)) out += excC14n(child, excluded, next);
    return out + "</" + node.nodeName + ">";
}

// ===================================================================== XML 도우미

const kids = (el, ns, name) => Array.from(el.children).filter((c) => c.namespaceURI === ns && c.localName === name);
const kid = (el, ns, name) => kids(el, ns, name)[0] || null;
const textOf = (el, ns, name) => {
    const c = el && kid(el, ns, name);
    return c ? c.textContent.trim() : null;
};

function findById(doc, id) {
    const all = doc.getElementsByTagName("*");
    for (const el of all) if (el.getAttribute("Id") === id) return el;
    return null;
}

// ===================================================================== TL 해석

function parseServiceInfo(info, historical) {
    const identity = kid(info, NS.TSL, "ServiceDigitalIdentity");
    const certs = [], skis = [];
    if (identity) {
        for (const d of kids(identity, NS.TSL, "DigitalId")) {
            const c = textOf(d, NS.TSL, "X509Certificate");
            if (c) certs.push(base64ToBytes(c));
            const s = textOf(d, NS.TSL, "X509SKI");
            if (s) skis.push(toHex(base64ToBytes(s)));
        }
    }
    const extensions = [];
    const container = kid(info, NS.TSL, "ServiceInformationExtensions");
    if (container) {
        for (const e of kids(container, NS.TSL, "Extension")) {
            const critical = ["true", "1"].includes(e.getAttribute("Critical"));
            for (const payload of Array.from(e.children)) {
                extensions.push({critical, ns: payload.namespaceURI, name: payload.localName, value: payload.textContent.trim()});
            }
        }
    }
    const names = kid(info, NS.TSL, "ServiceName");
    return {
        type: textOf(info, NS.TSL, "ServiceTypeIdentifier"),
        name: names ? (kid(names, NS.TSL, "Name") || {textContent: ""}).textContent.trim() : "",
        status: textOf(info, NS.TSL, "ServiceStatus"),
        start: new Date(textOf(info, NS.TSL, "StatusStartingTime")),
        certificates: historical ? [] : certs,
        skis,
        extensions,
    };
}

export function parseTrustList(doc) {
    const root = doc.documentElement;
    const si = kid(root, NS.TSL, "SchemeInformation");
    if (!si) throw new Error("SchemeInformation이 없습니다.");
    const operator = kid(si, NS.TSL, "SchemeOperatorName");
    const providers = [];
    const list = kid(root, NS.TSL, "TrustServiceProviderList");
    for (const p of list ? kids(list, NS.TSL, "TrustServiceProvider") : []) {
        const info = kid(p, NS.TSL, "TSPInformation");
        const tspName = info && kid(info, NS.TSL, "TSPName");
        const services = [];
        const tspServices = kid(p, NS.TSL, "TSPServices");
        for (const s of tspServices ? kids(tspServices, NS.TSL, "TSPService") : []) {
            const current = parseServiceInfo(kid(s, NS.TSL, "ServiceInformation"), false);
            const historyEl = kid(s, NS.TSL, "ServiceHistory");
            const history = historyEl ? kids(historyEl, NS.TSL, "ServiceHistoryInstance").map((h) => parseServiceInfo(h, true)) : [];
            services.push({current, history});
        }
        providers.push({name: tspName ? (kid(tspName, NS.TSL, "Name") || {textContent: ""}).textContent.trim() : "", services});
    }
    const nextUpdate = kid(si, NS.TSL, "NextUpdate");
    return {
        version: Number(textOf(si, NS.TSL, "TSLVersionIdentifier")),
        sequence: BigInt(textOf(si, NS.TSL, "TSLSequenceNumber")),
        operator: operator ? (kid(operator, NS.TSL, "Name") || {textContent: ""}).textContent.trim() : "",
        issuedAt: new Date(textOf(si, NS.TSL, "ListIssueDateTime")),
        nextUpdate: nextUpdate ? new Date(textOf(nextUpdate, NS.TSL, "dateTime")) : null,
        providers,
    };
}

/** 서비스의 시점 상태(반열린 구간). 이력 순서가 깨졌으면 INVALID_HISTORY. */
export function resolveStatus(service, at) {
    let later = service.current.start;
    for (const h of service.history) {
        if (!(h.start < later)) return {reason: "INVALID_HISTORY"};
        later = h.start;
    }
    if (at >= service.current.start) return {reason: "FOUND", entry: service.current, until: null};
    let until = service.current.start;
    for (const h of service.history) {
        if (at >= h.start) return {reason: "FOUND", entry: h, until};
        until = h.start;
    }
    return {reason: "NOT_ACCREDITED_AT_TIME"};
}

/** 서비스 식별: 현재 SKI 또는 인증서 공개키(SPKI) 일치. */
function serviceKey(service) {
    if (service.current.skis.length) return "ski:" + service.current.skis[0];
    if (service.current.certificates.length) return "der:" + toHex(service.current.certificates[0]);
    return null;
}

// ===================================================================== 검증

class Report {
    constructor() { this.steps = []; this.reasons = []; }
    step(id, title, status, detail, reasons = []) {
        this.steps.push({id, title, status, detail});
        for (const r of reasons) if (!this.reasons.includes(r)) this.reasons.push(r);
        return status !== "fail";
    }
    get passed() { return this.steps.every((s) => s.status !== "fail"); }
}

/**
 * @param bytes      IF-07에서 받은 원문 바이트
 * @param options    {anchors: [DER...], at: Date, toleranceMs, accepted: {sequence, sha256, issuedAt, xml}|null}
 */
export async function verifyTrustList(bytes, options) {
    const at = options.at || new Date();
    const tolerance = options.toleranceMs ?? 5 * 60 * 1000;
    const report = new Report();
    const digest = toHex(await sha256(bytes));
    report.sha256 = digest;

    // 1 XML 형식
    const doc = new DOMParser().parseFromString(new TextDecoder("utf-8").decode(bytes), "application/xml");
    const root = doc.documentElement;
    if (doc.getElementsByTagName("parsererror").length || root.namespaceURI !== NS.TSL
        || root.localName !== "TrustServiceStatusList" || !root.getAttribute("Id")) {
        report.step("xml", "XML 형식", "fail", "TrustServiceStatusList(Id 포함) 루트가 아닙니다.", ["XML_INVALID"]);
        return finish(report, null);
    }
    report.step("xml", "XML 형식", "pass", `루트 TrustServiceStatusList Id="${root.getAttribute("Id")}"`);

    // 2~6 서명
    const signer = await verifySignature(doc, root, bytes, options.anchors || [], at, report);

    // 7~9 내용
    let tl;
    try {
        tl = parseTrustList(doc);
    } catch (e) {
        report.step("scheme", "목록 값", "fail", "TL 내용을 해석할 수 없습니다: " + e.message, ["XML_INVALID"]);
        return finish(report, null);
    }
    report.trustList = tl;
    report.signer = signer;
    checkScheme(tl, at, tolerance, report);
    await checkAgainstAccepted(tl, digest, options.accepted, report);
    checkServices(tl, options.accepted, report);
    return finish(report, tl);
}

function finish(report, tl) {
    report.accepted = report.passed;
    report.trustList = tl;
    return report;
}

async function verifySignature(doc, root, bytes, anchors, at, report) {
    const signatures = doc.getElementsByTagNameNS(NS.DS, "Signature");
    const sig = signatures[0];
    const rootId = root.getAttribute("Id");
    const signedInfo = sig && kid(sig, NS.DS, "SignedInfo");
    // 2 구조
    const problems = [];
    if (signatures.length !== 1) problems.push("ds:Signature가 정확히 하나가 아닙니다.");
    else if (sig.parentNode !== root || sig !== root.lastElementChild) problems.push("ds:Signature가 루트의 마지막 자식이 아닙니다.");
    if (!signedInfo) problems.push("SignedInfo가 없습니다.");
    let tlRef = null;
    if (signedInfo) {
        const c14n = kid(signedInfo, NS.DS, "CanonicalizationMethod");
        if (!c14n || c14n.getAttribute("Algorithm") !== ALG.EXC_C14N) problems.push("CanonicalizationMethod가 exc-c14n이 아닙니다.");
        const method = kid(signedInfo, NS.DS, "SignatureMethod");
        if (!method || ![ALG.ECDSA_SHA256, ALG.RSA_SHA256].includes(method.getAttribute("Algorithm"))) {
            problems.push("지원하지 않는 SignatureMethod입니다.");
        }
        tlRef = kids(signedInfo, NS.DS, "Reference").find((r) => r.getAttribute("URI") === "#" + rootId) || null;
        if (!tlRef) problems.push(`TL 루트(#${rootId})를 가리키는 Reference가 없습니다.`);
        else {
            const transforms = Array.from(tlRef.getElementsByTagNameNS(NS.DS, "Transform")).map((t) => t.getAttribute("Algorithm"));
            if (transforms.length !== 2 || transforms[0] !== ALG.ENVELOPED || transforms[1] !== ALG.EXC_C14N) {
                problems.push("TL Reference의 Transform이 enveloped-signature, exc-c14n 두 개가 아닙니다.");
            }
        }
    }
    if (problems.length) {
        report.step("sigStructure", "서명 구조 (TS 119 612 B.1.0)", "fail", problems.join(" "), ["SIGNATURE_INVALID"]);
        return null;
    }
    report.step("sigStructure", "서명 구조 (TS 119 612 B.1.0)", "pass", `enveloped · Reference URI=#${rootId} · exc-c14n`);

    // 3 참조 다이제스트
    const digestProblems = [];
    for (const ref of kids(signedInfo, NS.DS, "Reference")) {
        const uri = ref.getAttribute("URI");
        if (!uri || !uri.startsWith("#")) { digestProblems.push(`지원하지 않는 Reference URI: ${uri}`); continue; }
        const target = uri.slice(1) === rootId ? root : findById(doc, uri.slice(1));
        if (!target) { digestProblems.push(`참조 대상 없음: ${uri}`); continue; }
        if (target !== root && !sig.contains(target)) { digestProblems.push(`서명 밖의 대상을 참조합니다: ${uri}`); continue; }
        const transforms = Array.from(ref.getElementsByTagNameNS(NS.DS, "Transform")).map((t) => t.getAttribute("Algorithm"));
        if (transforms.some((t) => t !== ALG.ENVELOPED && t !== ALG.EXC_C14N)) { digestProblems.push(`지원하지 않는 Transform: ${uri}`); continue; }
        const method = kid(ref, NS.DS, "DigestMethod");
        if (!method || method.getAttribute("Algorithm") !== ALG.SHA256) { digestProblems.push(`SHA-256이 아닌 DigestMethod: ${uri}`); continue; }
        const canonical = excC14n(target, transforms.includes(ALG.ENVELOPED) ? sig : null);
        const actual = bytesToBase64(await sha256(enc.encode(canonical)));
        if (actual !== textOf(ref, NS.DS, "DigestValue")) digestProblems.push(`다이제스트 불일치: ${uri}`);
    }
    if (digestProblems.length) {
        report.step("digest", "참조 다이제스트", "fail", digestProblems.join(" "), ["SIGNATURE_INVALID"]);
        return null;
    }
    report.step("digest", "참조 다이제스트", "pass", "TL 본문·SignedProperties 다이제스트 일치 (SHA-256)");

    // 4 서명값
    const keyInfo = kid(sig, NS.DS, "KeyInfo");
    const keyInfoCerts = (keyInfo ? Array.from(keyInfo.getElementsByTagNameNS(NS.DS, "X509Certificate")) : [])
        .map((c) => parseCertificate(base64ToBytes(c.textContent)));
    if (!keyInfoCerts.length) {
        report.step("sigValue", "서명값", "fail", "KeyInfo에 서명 인증서가 없습니다.", ["SIGNATURE_INVALID"]);
        return null;
    }
    const signerCert = keyInfoCerts[0];
    let valid = false;
    try {
        const k = await importKey(signerCert);
        const value = base64ToBytes(textOf(sig, NS.DS, "SignatureValue"));
        const data = enc.encode(excC14n(signedInfo));
        valid = k.ec
            ? await crypto.subtle.verify({name: "ECDSA", hash: "SHA-256"}, k.key, value, data)
            : await crypto.subtle.verify({name: "RSASSA-PKCS1-v1_5"}, k.key, value, data);
    } catch (e) {
        report.step("sigValue", "서명값", "fail", "서명값을 검증할 수 없습니다: " + e.message, ["SIGNATURE_INVALID"]);
        return null;
    }
    if (!report.step("sigValue", "서명값", valid ? "pass" : "fail",
        valid ? `SignedInfo 서명 일치 (${signerCert.subject})` : "SignedInfo 서명이 일치하지 않습니다.", valid ? [] : ["SIGNATURE_INVALID"])) {
        return null;
    }

    // 5 서명 인증서 결속 (SigningCertificateV2)
    const certDigest = Array.from(sig.getElementsByTagNameNS(NS.XADES, "CertDigest"))[0];
    if (!certDigest) {
        report.step("sigCert", "서명 인증서 결속 (XAdES)", "fail", "SigningCertificateV2/CertDigest가 없습니다.", ["SIGNATURE_INVALID"]);
    } else {
        const expected = textOf(certDigest, NS.DS, "DigestValue");
        const method = kid(certDigest, NS.DS, "DigestMethod");
        const hash = DIGESTS[method ? method.getAttribute("Algorithm") : ""];
        const actual = hash ? bytesToBase64(new Uint8Array(await crypto.subtle.digest(hash, signerCert.der))) : null;
        report.step("sigCert", "서명 인증서 결속 (XAdES)", expected === actual ? "pass" : "fail",
            expected === actual ? "SigningCertificateV2 다이제스트 = KeyInfo 서명 인증서" : "서명 속성의 인증서 다이제스트가 다릅니다.",
            expected === actual ? [] : ["SIGNATURE_INVALID"]);
    }

    // 6 서명자 체인
    await checkSignerChain(signerCert, keyInfoCerts.slice(1), anchors, at, report);
    return signerCert;
}

async function checkSignerChain(signer, intermediates, anchorDers, at, report) {
    const title = "서명자 체인 → 신뢰앵커";
    if (!anchorDers.length) {
        return report.step("chain", title, "fail", "사전 설정된 신뢰앵커(KISA TL RootCA)가 없습니다.", ["SIGNER_UNTRUSTED"]);
    }
    const anchors = anchorDers.map(parseCertificate);
    const path = [signer];
    const notes = [];
    let current = signer;
    for (let depth = 0; depth < 6; depth++) {
        const anchor = anchors.find((a) => equalBytes(current.issuerRaw, a.subjectRaw));
        if (anchor) {
            if (!(await certSignedBy(current, anchor))) {
                return report.step("chain", title, "fail", `${current.subject}의 서명을 신뢰앵커로 확인할 수 없습니다.`, ["SIGNER_UNTRUSTED"]);
            }
            path.push(anchor);
            break;
        }
        const issuer = intermediates.find((c) => equalBytes(current.issuerRaw, c.subjectRaw) && c !== current);
        if (!issuer) {
            return report.step("chain", title, "fail", `${current.issuer} 발급자를 KeyInfo·신뢰앵커에서 찾지 못했습니다.`, ["SIGNER_UNTRUSTED"]);
        }
        if (!(await certSignedBy(current, issuer))) {
            return report.step("chain", title, "fail", `${current.subject}의 발급자 서명이 일치하지 않습니다.`, ["SIGNER_UNTRUSTED"]);
        }
        path.push(issuer);
        current = issuer;
    }
    if (path.length < 2 || !anchors.includes(path[path.length - 1])) {
        return report.step("chain", title, "fail", "신뢰앵커까지 경로를 만들지 못했습니다.", ["SIGNER_UNTRUSTED"]);
    }
    for (const c of path) {
        if (at < c.notBefore || at > c.notAfter) {
            return report.step("chain", title, "fail", `${c.subject} 인증서가 검증 시각에 유효하지 않습니다.`, ["SIGNER_UNTRUSTED"]);
        }
    }
    for (const c of path.slice(1)) {
        if (!c.ca || (c.keyUsage && !c.keyUsage.keyCertSign)) {
            return report.step("chain", title, "fail", `${c.subject}가 CA 인증서가 아닙니다.`, ["SIGNER_UNTRUSTED"]);
        }
    }
    if (!signer.extKeyUsage || !signer.extKeyUsage.includes(OID.EKU_TSL_SIGNING)) {
        return report.step("chain", title, "fail", "서명 인증서에 TL 서명 용도(id-tsl-kp-tslSigning)가 없습니다.", ["SIGNER_UNTRUSTED"]);
    }
    notes.push(path.map((c) => c.subject.replace(/^.*CN=/, "").replace(/,.*$/, "")).join(" → "));
    notes.push("폐기 확인(CRL/OCSP) 미수행");
    return report.step("chain", title, "pass", notes.join(" · "));
}

function checkScheme(tl, at, tolerance, report) {
    const problems = [], reasons = [];
    if (tl.sequence <= 0n) { problems.push("순번이 1 이상이 아닙니다."); reasons.push("XML_INVALID"); }
    if (tl.issuedAt.getTime() > at.getTime() + tolerance) {
        problems.push(`발행 시각(${tl.issuedAt.toISOString()})이 검증 시각보다 미래입니다.`);
        reasons.push("ISSUE_TIME_IN_FUTURE");
    }
    if (!tl.nextUpdate || !(tl.nextUpdate > tl.issuedAt)) {
        problems.push("NextUpdate가 발행 시각 이후가 아닙니다.");
        reasons.push("INVALID_UPDATE_INTERVAL");
    } else {
        const limit = new Date(tl.issuedAt);
        limit.setUTCMonth(limit.getUTCMonth() + MAX_NEXT_UPDATE_MONTHS);
        if (tl.nextUpdate > limit) { problems.push("NextUpdate가 발행 후 6개월을 넘습니다."); reasons.push("INVALID_UPDATE_INTERVAL"); }
        if (tl.nextUpdate < at) { problems.push(`NextUpdate(${tl.nextUpdate.toISOString()})가 지나 만료되었습니다.`); reasons.push("TL_EXPIRED"); }
    }
    report.step("scheme", "목록 값 (발행·만료)", problems.length ? "fail" : "pass",
        problems.length ? problems.join(" ") : `순번 ${tl.sequence} · 발행 ${tl.issuedAt.toISOString()} · NextUpdate ${tl.nextUpdate.toISOString()}`,
        reasons);
}

async function checkAgainstAccepted(tl, digest, accepted, report) {
    const title = "보유본 대비 (순번·시각)";
    if (!accepted) {
        return report.step("accepted", title, "info", "최초 수신: 보유본이 없어 순번 역행·발행 시각 역행·이력 삭제를 확인할 수 없습니다.");
    }
    const prevSeq = BigInt(accepted.sequence);
    if (tl.sequence < prevSeq) {
        return report.step("accepted", title, "fail", `순번 ${tl.sequence}이(가) 보유본 순번 ${prevSeq}보다 낮습니다.`, ["SEQUENCE_ROLLBACK"]);
    }
    if (tl.sequence === prevSeq) {
        if (digest !== accepted.sha256) {
            return report.step("accepted", title, "fail", `보유본과 같은 순번 ${prevSeq}이지만 내용이 다릅니다.`, ["SEQUENCE_CONFLICT"]);
        }
        return report.step("accepted", title, "pass", `보유본과 같은 발행본입니다(순번 ${prevSeq}, 바이트 동일).`);
    }
    if (!(tl.issuedAt > new Date(accepted.issuedAt))) {
        return report.step("accepted", title, "fail", `새 순번이지만 발행 시각이 보유본(${accepted.issuedAt}) 이후가 아닙니다.`, ["ISSUE_TIME_REGRESSION"]);
    }
    return report.step("accepted", title, "pass", `순번 ${prevSeq} → ${tl.sequence}, 발행 시각 전진`);
}

function checkServices(tl, accepted, report) {
    const problems = [], reasons = [];
    let count = 0;
    const byKey = new Map();
    for (const p of tl.providers) {
        for (const s of p.services) {
            count++;
            const label = `${p.name} / ${s.current.name}`;
            for (const entry of [s.current, ...s.history]) {
                if (!(entry.status in POC_PROFILE.statuses)) {
                    problems.push(`${label}: 지원하지 않는 상태 URI ${entry.status}`);
                    reasons.push("UNSUPPORTED_STATUS");
                }
                for (const ext of entry.extensions) {
                    const known = ext.ns === POC_PROFILE.extensionNs && POC_PROFILE.knownExtensions.includes(ext.name);
                    if (ext.critical && !known) {
                        problems.push(`${label}: 인식하지 못한 critical 확장 {${ext.ns}}${ext.name}`);
                        reasons.push("UNSUPPORTED_CRITICAL_EXTENSION");
                    }
                }
            }
            if (resolveStatus(s, s.current.start).reason === "INVALID_HISTORY") {
                problems.push(`${label}: 이력 시작 시각이 최신순으로 감소하지 않습니다.`);
                reasons.push("INVALID_HISTORY");
            }
            const key = serviceKey(s);
            if (key) byKey.set(key, {label, service: s});
        }
    }
    if (accepted && accepted.xml) {
        let previous;
        try {
            previous = parseTrustList(new DOMParser().parseFromString(accepted.xml, "application/xml"));
        } catch (e) {
            previous = null;
        }
        const prevIssued = new Date(accepted.issuedAt);
        for (const p of previous ? previous.providers : []) {
            for (const old of p.services) {
                const match = byKey.get(serviceKey(old));
                if (!match) continue;
                const now = match.service;
                const same = (a, b) => a.status === b.status && a.start.getTime() === b.start.getTime();
                if (same(now.current, old.current)) continue;
                if (!now.history.some((h) => same(h, old.current))) {
                    problems.push(`${match.label}: 보유본의 현재 상태가 새 TL의 이력에서 사라졌습니다.`);
                    reasons.push("INVALID_HISTORY");
                }
                if (now.current.start < prevIssued) {
                    problems.push(`${match.label}: 새 상태 시작(${now.current.start.toISOString()})이 보유본 발행 시각보다 이릅니다.`);
                    reasons.push("RETROACTIVE_STATUS_CHANGE");
                }
            }
        }
    }
    report.step("services", "서비스 정보 (상태·확장·이력)", problems.length ? "fail" : "pass",
        problems.length ? problems.join(" ") : `서비스 ${count}개: 상태 URI·critical 확장 지원, 이력 순서 정상`
            + (accepted ? "" : " (보유본 대비 소급·이력 삭제는 비교 불가)"),
        reasons);
}

// ===================================================================== IF-07 조회

/**
 * IF-07 GET. cache:"no-store"로 브라우저 캐시를 우회해 서버의 304를 그대로 받는다.
 * 반환: {status, bytes?, etag?, error?}
 */
export async function fetchTrustList(url, etag) {
    const headers = {Accept: "application/vnd.etsi.tsl+xml"};
    if (etag) headers["If-None-Match"] = etag;
    const response = await fetch(url, {headers, cache: "no-store", credentials: "omit", mode: "cors"});
    const result = {status: response.status, etag: response.headers.get("ETag")};
    if (response.status === 200) result.bytes = new Uint8Array(await response.arrayBuffer());
    else if (response.status !== 304) result.error = (await response.text()).slice(0, 80);
    return result;
}

/** IF-07 보조 해시(raw 32바이트). 실패해도 TL 검증을 대신하지 않는다. */
export async function fetchHash(url) {
    const response = await fetch(url, {cache: "no-store", credentials: "omit", mode: "cors"});
    if (response.status !== 200) return {status: response.status};
    return {status: 200, hash: toHex(new Uint8Array(await response.arrayBuffer()))};
}

/**
 * 수용 TL에서 인증서로 서비스를 찾아 시점 상태를 조회한다(PDF 서명자 판정 중 TL 단계만).
 *   match=SELF   : 입력 인증서가 등재 서비스의 디지털 ID(같은 공개키 또는 SKI)
 *   match=ISSUER : 입력 인증서의 AKI가 등재 CA 서비스의 SKI (가입자 인증서의 직접 발급 CA)
 * 체인 서명 검증·폐기 확인·레벨 0 Root까지의 경로 구성은 이 샘플 범위가 아니다.
 */
export async function lookupService(tl, certDer, at) {
    const cert = parseCertificate(certDer);
    const spkiHex = toHex(cert.spki);
    for (const match of ["SELF", "ISSUER"]) {
        for (const p of tl.providers) {
            for (const s of p.services) {
                const keys = s.current.certificates.map((d) => toHex(parseCertificate(d).spki));
                const hit = match === "SELF"
                    ? keys.includes(spkiHex) || (cert.ski && s.current.skis.includes(cert.ski))
                    : cert.aki && s.current.skis.includes(cert.aki) && !keys.includes(spkiHex);
                if (hit) return {match, provider: p.name, service: s, resolution: resolveStatus(s, at), cert};
            }
        }
    }
    return {match: null, provider: null, service: null, resolution: {reason: "NOT_LISTED"}, cert};
}
