package com.br.fincloud.service.dto;

import com.br.fincloud.domain.TipoTransacao;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransacaoUpdateDTO(
        @NotNull(message = "contaId é obrigatório")
        Long contaId,

        @NotNull(message = "categoriaId é obrigatório")
        Long categoriaId,

        @NotNull(message = "tipo é obrigatório")
        TipoTransacao tipo,

        @NotNull(message = "valor é obrigatório")
        BigDecimal valor,

        @NotNull(message = "data é obrigatória")
        LocalDate data,

        String descricao
) {}
