package com.mandao.grc.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 签名令牌仓储（平台表，无 RLS——token 即凭证）。 */
public interface SignatureTicketRepository extends JpaRepository<SignatureTicket, Long> {

    Optional<SignatureTicket> findByToken(String token);
}
