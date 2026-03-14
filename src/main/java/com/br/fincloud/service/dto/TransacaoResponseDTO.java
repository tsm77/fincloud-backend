package com.br.fincloud.service.dto;

import com.br.fincloud.domain.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransacaoResponseDTO(
        Long id,
        TipoTransacao tipo,
        BigDecimal valor,
        LocalDate data,
        String descricao,
        Long contaId,
        String contaNome,
        Long categoriaId,
        String categoriaNome,
        LocalDateTime dataCriacao
) {}