package com.electcerti.krdss.poc.rp.tlpoc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.electcerti.krdss.poc.rp.tlpoc.Fixtures.*;
import java.nio.file.*;
import java.time.Instant;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;

class TlPocControllerTest {
    @TempDir Path root;
    @Test void apiAdoptsVerifiesAndListsRealResult() throws Exception {
        var anchor=identity("TL Root",null,true);var signer=identity("TL Signer",anchor,false);
        var ca=identity("Service",null,true);var leaf=identity("Subscriber",ca,false);
        Path anchors=root.resolve("anchors.der");Files.write(anchors,anchor.cert().getEncoded());
        var mvc=MockMvcBuilders.standaloneSetup(new TlPocController(root.resolve("data").toString(),anchors.toString())).build();
        var inspected = mvc.perform(multipart("/api/tl-poc/inspect").file(new MockMultipartFile("tl","tl.xml","application/xml",
                tl(signer,1,Instant.now().plusSeconds(3600),ca,ServiceStatus.ACCREDITED))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(true)).andReturn();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(inspected.getResponse().getContentAsString()).get("candidateId").asText();
        mvc.perform(get("/api/tl-poc/active")).andExpect(jsonPath("$.active").isEmpty());
        mvc.perform(post("/api/tl-poc/adopt").param("candidateId",id)).andExpect(status().isOk()).andExpect(jsonPath("$.sequence").value("1"));
        byte[] pdf = PocPdfVerifierTest.signedPdf(leaf,ca);
        mvc.perform(multipart("/api/tl-poc/verify").file(new MockMultipartFile("pdf","test.pdf","application/pdf",pdf)).param("at",NOW.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.integrity").value("PASSED"))
                .andExpect(jsonPath("$.result.trust").value("TRUST_CONDITIONS_MET"));
        mvc.perform(get("/api/tl-poc/runs")).andExpect(status().isOk()).andExpect(jsonPath("$[0].tlSequence").value("1"));
        mvc.perform(multipart("/api/tl-poc/inspect").file(new MockMultipartFile("tl",new byte[]{1}))).andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(false));
        mvc.perform(get("/api/tl-poc/active")).andExpect(jsonPath("$.active.sequence").value("1"));
        // 명시적으로 지정된 로컬 자료 폴더에 공개 시험 자료만 내보낸다. 개인키는 내보내지 않는다.
        String export = System.getenv("KRTL_POC_FIXTURES");
        if (export != null && !export.isBlank()) {
            Path dir = Path.of(export); Files.createDirectories(dir);
            Files.write(dir.resolve("tl-root.der"), anchor.cert().getEncoded());
            Files.write(dir.resolve("signed.pdf"), pdf);
            Files.write(dir.resolve("tl-1.xml"), tl(signer,1,Instant.now().plusSeconds(86400),ca,ServiceStatus.ACCREDITED));
            Files.write(dir.resolve("tl-2.xml"), tl(signer,2,Instant.now().plusSeconds(86400),ca,ServiceStatus.SUSPENDED));
        }
    }
}
