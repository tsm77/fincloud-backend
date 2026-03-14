package com.br.fincloud.service.dto;
import com.br.fincloud.domain.TipoConta;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ContaResponseDTO(
        Long id,
        String nome,
        TipoConta tipo,
        BigDecimal saldoInicial,
        Boolean ativa,
        LocalDateTime dataCriacao
) {}