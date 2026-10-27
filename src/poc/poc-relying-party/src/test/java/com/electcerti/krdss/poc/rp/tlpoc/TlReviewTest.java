package com.electcerti.krdss.poc.rp.tlpoc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.electcerti.krdss.poc.rp.tlpoc.Fixtures.*;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;

class TlReviewTest {
    @TempDir Path root;
    @Test void inspectionDoesNotApplyAndStaleCandidatesCannotReplaceActive() throws Exception {
        var anchor=identity("TL Root",null,true);var signer=identity("TL signer",anchor,false);var ca=identity("Service",null,true);
        var store=new TlAcceptance(root,List.of(anchor.cert()),Clock.fixed(NOW,ZoneOffset.UTC));
        byte[] first=tl(signer,1,NOW.plusSeconds(3600),ca,ServiceStatus.ACCREDITED);
        var initial=store.review(first);assertThat(initial.ready()).isTrue();assertThat(store.view()).isNull();
        assertThat(store.xml(initial.candidateId())).isEqualTo(first);
        store.apply(initial.candidateId());assertThat(store.capture().sequence()).isEqualTo("1");
        var next=store.review(tl(signer,2,NOW.plusSeconds(3600),ca,ServiceStatus.SUSPENDED));
        assertThat(store.capture().sequence()).isEqualTo("1");
        assertThat(next.changes()).anyMatch(c->c.kind().equals("상태 변경")&&c.before().equals("인정")&&c.after().equals("정지"));
        var replacement=store.review(tl(signer,3,NOW.plusSeconds(3600),ca,ServiceStatus.SUSPENDED));
        assertThatThrownBy(()->store.apply(next.candidateId())).hasMessage("CANDIDATE_CHANGED");
        store.adopt(tl(signer,2,NOW.plusSeconds(3600),ca,ServiceStatus.ACCREDITED));
        assertThatThrownBy(()->store.apply(replacement.candidateId())).hasMessage("BASELINE_CHANGED");
        var invalid=store.review("<!DOCTYPE a [<!ENTITY leak SYSTEM 'file:///secret'>]><a>&leak;</a>".getBytes());
        assertThat(invalid.ready()).isFalse();assertThat(invalid.document()).isNull();
        assertThatThrownBy(()->store.apply(invalid.candidateId())).hasMessage("CANDIDATE_REJECTED");
        assertThat(store.capture().sequence()).isEqualTo("2");
    }
    @Test void rechecksExpiryAtApply() throws Exception {
        var time=new java.util.concurrent.atomic.AtomicReference<>(NOW);
        Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return time.get();}};
        var anchor=identity("TL Root",null,true);var signer=identity("Signer",anchor,false);var ca=identity("CA",null,true);
        var store=new TlAcceptance(root,List.of(anchor.cert()),clock);
        var checked=store.review(tl(signer,1,NOW.plusSeconds(10),ca,ServiceStatus.ACCREDITED));
        time.set(NOW.plusSeconds(10));assertThatThrownBy(()->store.apply(checked.candidateId())).hasMessage("TL_EXPIRED");
        assertThat(store.view()).isNull();
    }
    @Test void comparesCertificatesKeysAdditionsAndAmbiguousIdsWithoutInventingHistory() throws Exception {
        var signer=identity("Signer",null,true);var ca=identity("CA",null,true);
        var original=TlReview.describe(tl(signer,1,NOW.plusSeconds(3600),ca,ServiceStatus.ACCREDITED));
        var service=original.services().get(0);assertThat(service.timeline()).hasSize(1);
        assertThat(TlReview.compare(original,TlReview.describe(tl(signer,1,NOW.plusSeconds(3600),ca,ServiceStatus.ACCREDITED))))
                .noneMatch(c->c.kind().equals("상태 변경")||c.kind().equals("상세 변경"));
        var certificate=service.certificates().get(0);
        var renewal=new TlReview.Certificate(certificate.subject(),certificate.issuer(),certificate.notBefore(),certificate.notAfter(),"new-cert",certificate.publicKey(),true);
        var changed=new TlReview.Service(service.key(),service.provider(),service.name(),service.type(),service.status(),service.since(),List.of(renewal),service.timeline(),service.xmlPath(),service.metadata());
        assertThat(TlReview.compare(original,withServices(original,List.of(changed)))).anyMatch(c->c.kind().equals("같은 공개키 인증서 갱신"));
        var otherKey=new TlReview.Certificate(certificate.subject(),certificate.issuer(),certificate.notBefore(),certificate.notAfter(),"other-cert","other-key",true);
        var other=new TlReview.Service(service.key(),service.provider(),service.name(),service.type(),service.status(),service.since(),List.of(otherKey),service.timeline(),service.xmlPath(),service.metadata());
        assertThat(TlReview.compare(original,withServices(original,List.of(other)))).anyMatch(c->c.kind().equals("공개키 변경"));
        assertThat(TlReview.compare(original,withServices(original,List.of()))).anyMatch(c->c.kind().equals("삭제"));
        assertThat(TlReview.compare(null,original)).anyMatch(c->c.kind().equals("추가"));
        assertThat(TlReview.compare(original,withServices(original,List.of(service,service)))).anyMatch(c->c.kind().equals("비교 모호"));
    }
    private TlReview.Document withServices(TlReview.Document d,List<TlReview.Service> services){return new TlReview.Document(d.operator(),d.sequence(),d.issuedAt(),d.nextUpdate(),d.providerCount(),services,d.signingCertificates(),d.sha256(),d.formattedXml());}
}
