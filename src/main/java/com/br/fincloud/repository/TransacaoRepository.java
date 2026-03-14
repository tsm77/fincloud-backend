package com.br.fincloud.repository;

import com.br.fincloud.domain.Transacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {
    List<Transacao> findAllByUsuarioEmail(String email);

    Optional<Transacao> findByIdAndUsuarioEmail(Long id, String email);
}
