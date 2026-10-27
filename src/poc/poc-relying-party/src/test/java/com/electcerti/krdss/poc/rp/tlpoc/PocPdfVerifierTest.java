package com.electcerti.krdss.poc.rp.tlpoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import org.apache.pdfbox.pdmodel.*;
import static org.assertj.core.api.Assertions.*;
import static com.electcerti.krdss.poc.rp.tlpoc.Fixtures.*;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;
import com.electcerti.krdss.poc.rp.pdf.PdfBaselineSigner;

class PocPdfVerifierTest {
    @TempDir Path root;
    static byte[] signedPdf(Identity leaf, Identity ca) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PDDocument()) { pdf.addPage(new PDPage()); pdf.save(output); }
        var ks = KeyStore.getInstance("PKCS12"); ks.load(null, null);
        char[] password = "test-only".toCharArray();
        ks.setKeyEntry("test", leaf.keys().getPrivate(), password, new java.security.cert.Certificate[]{leaf.cert(), ca.cert()});
        var encoded = new ByteArrayOutputStream(); ks.store(encoded, password);
        return new PdfBaselineSigner().sign(output.toByteArray(), "test.pdf", encoded.toByteArray(), password, "PoC", "local").bytes();
    }
    @Test void realPdfIntegrityIsIndependentOfTlStateAndMissingTl() throws Exception {
        var rootCa = identity("TL Root", null, true); var signer = identity("TL Signer", rootCa, false);
        var ca = identity("Service", null, true); var leaf = identity("Subscriber", ca, false);
        var store = new TlAcceptance(root, List.of(rootCa.cert()), Clock.fixed(NOW, ZoneOffset.UTC));
        var pdf = signedPdf(leaf, ca); var verifier = new PocPdfVerifier();
        store.adopt(tl(signer, 1, NOW.plusSeconds(3600), ca, ServiceStatus.ACCREDITED));
        var normal = verifier.verify(pdf, store.capture(), NOW, List.of());
        assertThat(normal.integrity()).isEqualTo("PASSED"); assertThat(normal.chain()).isEqualTo("VALID");
        assertThat(normal.trust()).isEqualTo("TRUST_CONDITIONS_MET");
        store.adopt(tl(signer, 2, NOW.plusSeconds(3600), ca, ServiceStatus.SUSPENDED));
        var suspended = verifier.verify(pdf, store.capture(), NOW, List.of());
        assertThat(suspended.integrity()).isEqualTo("PASSED"); assertThat(suspended.serviceStatus()).isEqualTo("SUSPENDED");
        assertThat(suspended.trust()).isEqualTo("TRUST_CONDITIONS_NOT_MET");
        var missing = verifier.verify(pdf, null, NOW, List.of());
        assertThat(missing.integrity()).isEqualTo("PASSED"); assertThat(missing.trust()).isEqualTo("NO_VALID_TL");
        byte[] changed = pdf.clone(); changed[7] = changed[7] == '4' ? (byte)'5' : (byte)'4';
        assertThat(verifier.verify(changed, store.capture(), NOW, List.of()).integrity()).isEqualTo("SIGNATURE_INVALID");
    }
    @Test void sameNameDifferentKeyIsNotTrustedAndBrokenIssuerIsDistinct() throws Exception {
        var anchor = identity("TL Root", null, true); var signer = identity("TL Signer", anchor, false);
        var listed = identity("Same CA", null, true); var other = identity("Same CA", null, true);
        var leaf = identity("Subscriber", other, false);
        var store = new TlAcceptance(root, List.of(anchor.cert()), Clock.fixed(NOW, ZoneOffset.UTC));
        store.adopt(tl(signer, 1, NOW.plusSeconds(3600), listed, ServiceStatus.ACCREDITED));
        var verifier = new PocPdfVerifier();
        var absent = verifier.evaluate(leaf.cert(), List.of(leaf.cert(), other.cert()), store.capture(), NOW);
        assertThat(absent.chain()).isEqualTo("VALID"); assertThat(absent.trust()).isEqualTo("NOT_IN_TL");
        var broken = verifier.evaluate(leaf.cert(), List.of(leaf.cert(), listed.cert()), store.capture(), NOW);
        assertThat(broken.chain()).isEqualTo("CHAIN_INVALID");
    }
    @Test void suspendedCaCannotBeBypassedViaRootAndRestorationKeepsPast() throws Exception {
        var anchor=identity("TL Root",null,true);var signer=identity("TL Signer",anchor,false);
        var rootCa=identity("Service Root",null,true);var ca=identity("Service CA",rootCa,true);var leaf=identity("Subscriber",ca,false);
        var store=new TlAcceptance(root,List.of(anchor.cert()),Clock.fixed(NOW,ZoneOffset.UTC));
        store.adopt(tl(signer,1,NOW.plusSeconds(3600),ca,ServiceStatus.SUSPENDED));
        var snapshot=store.capture();var service=snapshot.list().trustServiceProviders().get(0).services().get(0);
        var rootService=com.electcerti.krdss.tl.builder.KrTrustListXml.fromXml(tl(signer,2,NOW.plusSeconds(3600),rootCa,ServiceStatus.ACCREDITED))
                .trustServiceProviders().get(0).services().get(0);
        var old=service.current();
        var history=new com.electcerti.krdss.tl.model.KrTrustList.ServiceHistoryInstance(old.serviceTypeUri(),old.names(),
                new com.electcerti.krdss.tl.model.KrTrustList.HistoricalDigitalIdentity(List.of(),null),old.statusUri(),old.statusStartingTime(),List.of());
        var restored=new com.electcerti.krdss.tl.model.KrTrustList.TrustService("restored",new com.electcerti.krdss.tl.model.KrTrustList.ServiceInformation(
                old.serviceTypeUri(),old.names(),old.digitalIdentity(),com.electcerti.krdss.tl.model.PocTrustListProfile.STATUS_ACCREDITED,NOW.plusSeconds(10),List.of()),List.of(history));
        var list=new com.electcerti.krdss.tl.model.KrTrustList(snapshot.list().schemeInformation(),List.of(new com.electcerti.krdss.tl.model.KrTrustList.TrustServiceProvider("p",List.of(rootService,restored))));
        var combined=new TlAcceptance.Snapshot(snapshot.id(),snapshot.sequence(),snapshot.sha256(),snapshot.acceptedAt(),snapshot.nextUpdate(),list,snapshot.xml());
        var verifier=new PocPdfVerifier();
        assertThat(verifier.evaluate(leaf.cert(),List.of(leaf.cert(),ca.cert(),rootCa.cert()),combined,NOW).serviceStatus()).isEqualTo("SUSPENDED");
        assertThat(verifier.evaluate(leaf.cert(),List.of(leaf.cert(),ca.cert(),rootCa.cert()),combined,NOW.plusSeconds(10)).serviceStatus()).isEqualTo("ACCREDITED");
    }
}
