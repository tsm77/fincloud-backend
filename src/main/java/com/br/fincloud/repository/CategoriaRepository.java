package com.br.fincloud.repository;

import com.br.fincloud.domain.Categoria;
import com.br.fincloud.domain.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    List<Categoria> findAllByUsuarioEmail(String email);

    Optional<Categoria> findByIdAndUsuarioEmail(Long id, String email);

    boolean existsByUsuarioEmailAndNomeIgnoreCaseAndTipo(String email, String nome, TipoTransacao tipo);

    boolean existsByUsuarioEmailAndNomeIgnoreCaseAndTipoAndIdNot(String email, String nome, TipoTransacao tipo, Long id);
}