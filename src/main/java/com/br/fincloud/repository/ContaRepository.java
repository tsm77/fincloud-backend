package com.br.fincloud.repository;

import com.br.fincloud.domain.Conta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContaRepository extends JpaRepository<Conta, Long> {


    List<Conta> findAllByUsuarioEmail(String email);

    Optional<Conta> findByIdAndUsuarioEmail(Long id, String email);
}
