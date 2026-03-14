package com.br.fincloud.service.dto;

import com.br.fincloud.domain.TipoConta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ContaCreateDTO(
        @NotBlank(message = "nome é obrigatório")
        String nome,

        @NotNull(message = "tipo é obrigatório")
        TipoConta tipo,

        BigDecimal saldoInicial
) {}