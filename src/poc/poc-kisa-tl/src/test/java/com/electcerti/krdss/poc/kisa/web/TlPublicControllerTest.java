package com.electcerti.krdss.poc.kisa.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.electcerti.krdss.poc.kisa.issuance.Issuance.Purpose;
import com.electcerti.krdss.poc.kisa.issuance.Issuance.Request;
import com.electcerti.krdss.poc.kisa.issuance.IssuanceService;
import com.electcerti.krdss.poc.kisa.issuance.TestPki;
import com.electcerti.krdss.poc.kisa.registry.RegistryException;
import com.electcerti.krdss.poc.kisa.registry.RegistryException.Code;
import com.electcerti.krdss.poc.kisa.registry.RegistryService;
import com.electcerti.krdss.poc.kisa.registry.RegistryService.ProviderInput;
import com.electcerti.krdss.poc.kisa.registry.RegistryService.ServiceInput;
import com.electcerti.krdss.poc.kisa.registry.RegistryStore;
import com.electcerti.krdss.tl.model.PocTrustListProfile.ServiceType;
import com.electcerti.krdss.tl.model.PocTrustListProfile.TrustDomain;
import com.electcerti.krdss.tl.model.PocTrustListProfile.TrustPointLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TlPublicControllerTest {
    private static final String TSL = "application/vnd.etsi.tsl+xml";

    @TempDir Path root;
    @TempDir Path signerDir;
    private final Clock clock = Clock.fixed(TestPki.T0, ZoneOffset.UTC);
    private RegistryStore store;
    private IssuanceService issuance;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        TestPki.writeSigner(signerDir);
        store = RegistryStore.open(root, clock);
        var registry = new RegistryService(store, clock);
        issuance = new IssuanceService(store, signerDir, clock);
        var pid = registry.addProvider(1, new ProviderInput("가상공동인증A", null, null, null))
                .draft().providers().get(0).providerId();
        var keys = TestPki.keyPair();
        var ca = TestPki.certificate(keys, "CN=가상공동인증A CA, C=KR", keys.getPrivate(), null, true);
        registry.addService(store.current().revision(), pid, new ServiceInput(ServiceType.CA, "CA", null,
                TrustDomain.JOINT, TrustPointLevel.CA, TestPki.T0.minusSeconds(86400),
                List.of(Base64.getEncoder().encodeToString(ca.getEncoded()))));
        mvc = MockMvcBuilders.standaloneSetup(new TlPublicController(issuance)).build();
    }

    @AfterEach
    void close() {
        store.close();
    }

    private String issue(Purpose purpose, String sequence) {
        return issuance.issue(new Request(UUID.randomUUID().toString(), store.current().revision(), purpose, sequence,
                null, null)).manifest().issuanceId();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    void notPublishedUntilAdministratorSelects() throws Exception {
        issue(Purpose.BASELINE, null);
        mvc.perform(get("/tl/kr-tl.xml")).andExpect(status().isNotFound())
                .andExpect(content().string("TL_NOT_PUBLISHED"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        mvc.perform(get("/tl/kr-tl.sha2")).andExpect(status().isNotFound());
    }

    @Test
    void servesStoredBytesWithHashEtagAndConditionalGet() throws Exception {
        var id = issue(Purpose.BASELINE, null);
        issuance.select(0, id);
        byte[] stored = issuance.signedXml(id);
        String etag = "\"sha256-" + sha256(stored) + "\"";

        var result = mvc.perform(get("/tl/kr-tl.xml").header(HttpHeaders.ACCEPT, TSL))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, TSL))
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform"))
                .andReturn();
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(stored);

        mvc.perform(get("/tl/kr-tl.xml").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag));
        mvc.perform(get("/tl/kr-tl.xml").header(HttpHeaders.IF_NONE_MATCH, "\"sha256-00\""))
                .andExpect(status().isOk());

        var sha2 = mvc.perform(get("/tl/kr-tl.sha2")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(sha2).hasSize(32);
        assertThat(HexFormat.of().formatHex(sha2)).isEqualTo(sha256(stored));
        mvc.perform(head("/tl/kr-tl.xml")).andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    @Test
    void selectedTrialWithLowerSequenceIsServedAsIs() throws Exception {
        var baseline = issue(Purpose.BASELINE, null);
        issuance.select(0, baseline);
        var trial = issue(Purpose.TRIAL, "1");
        issuance.select(1, trial);
        var body = mvc.perform(get("/tl/kr-tl.xml")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(issuance.signedXml(trial));
        // 제공본을 바꿔도 정상 기준은 그대로다.
        assertThat(store.state().baselineIssuanceId()).isEqualTo(baseline);
    }

    @Test
    void selectionRequiresCurrentRevisionAndCommittedIssuance() {
        var id = issue(Purpose.BASELINE, null);
        issuance.select(0, id);
        assertThatThrownBy(() -> issuance.select(0, id)).satisfies(e ->
                assertThat(((RegistryException) e).code()).isEqualTo(Code.REVISION_CONFLICT));
        assertThatThrownBy(() -> issuance.select(1, "iss-unknown")).satisfies(e ->
                assertThat(((RegistryException) e).code()).isEqualTo(Code.NOT_FOUND));
        assertThat(store.state().servedIssuanceId()).isEqualTo(id);
    }

    @Test
    void damagedServedFileIsUnavailableAndNotReplaced() throws Exception {
        var id = issue(Purpose.BASELINE, null);
        issuance.select(0, id);
        Path xml = root.resolve("issued").resolve(id).resolve("signed.xml");
        Files.write(xml, (Files.readString(xml) + " ").getBytes());
        mvc.perform(get("/tl/kr-tl.xml")).andExpect(status().isServiceUnavailable())
                .andExpect(content().string("TL_UNAVAILABLE"));
        mvc.perform(get("/tl/kr-tl.sha2")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void protocolErrors() throws Exception {
        issuance.select(0, issue(Purpose.BASELINE, null));
        mvc.perform(get("/tl/kr-tl.xml").header(HttpHeaders.ACCEPT, "application/json"))
                .andExpect(status().isNotAcceptable()).andExpect(content().string("NOT_ACCEPTABLE"));
        mvc.perform(get("/tl/kr-tl.xml").header(HttpHeaders.ACCEPT, "*/*")).andExpect(status().isOk());
        mvc.perform(post("/tl/kr-tl.xml")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "GET, HEAD, OPTIONS"));
        // CORS preflight(OPTIONS)는 standalone MockMvc가 처리하지 못해 기동 서버에서 확인한다.
        mvc.perform(get("/tl/kr-tl.xml").header(HttpHeaders.ORIGIN, "http://viewer.example"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.ETAG));
    }
}
