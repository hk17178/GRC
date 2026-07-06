package com.mandao.grc.modules.audit.management;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 证书有效期台账服务（收口批 B24 / M3-13）。
 *
 * 隔离/留痕范式同其它模块：@Transactional → 切面注入 visible_orgs，RLS 裁剪 + WITH CHECK；
 * 登记/吊销留痕入链。到期提醒由内核到期扫描（ExpiryScanService）统一调度，本服务只管 CRUD。
 */
@Service
public class CertificateService {

    private final CertificateRepository repository;
    private final HashChainService hashChainService;

    public CertificateService(CertificateRepository repository, HashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<Certificate> list() {
        return repository.findAllByOrderByExpiryDateAsc();
    }

    /** 登记证书。 */
    @Transactional
    public Certificate create(Long orgId, String name, String framework, String certNo, String issuer,
                              LocalDate issuedDate, LocalDate expiryDate, String actor) {
        if (expiryDate == null) {
            throw new IllegalArgumentException("证书到期日必填");
        }
        Certificate c = new Certificate(orgId, name, framework, certNo, issuer, issuedDate, expiryDate, actor);
        Certificate saved = repository.save(c);
        hashChainService.append(orgId, "CERT_REGISTER", actor, "CERTIFICATE:" + saved.getId(),
                "登记证书 name=" + name + " 体系=" + framework + " 到期=" + expiryDate);
        return saved;
    }

    /** 吊销证书（置 REVOKED）。 */
    @Transactional
    public Certificate revoke(Long id, String actor) {
        Certificate c = get(id);
        c.setStatus("REVOKED");
        Certificate saved = repository.save(c);
        hashChainService.append(c.getOrgId(), "CERT_REVOKE", actor, "CERTIFICATE:" + id, "吊销证书");
        return saved;
    }

    private Certificate get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("证书不存在或不可见：id=" + id));
    }
}
