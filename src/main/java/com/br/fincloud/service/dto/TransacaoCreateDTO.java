package com.br.fincloud.service.dto;

import com.br.fincloud.domain.TipoTransacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TransacaoCreateDTO(
        @NotNull Long contaId,
        @NotNull Long categoriaId,
        @NotNull TipoTransacao tipo,
        @NotNull LocalDate data,

        // ✅ lista de itens
        @NotEmpty List<ItemDTO> itens
) {
        public record ItemDTO(
                @NotBlank String descricao,
                BigDecimal valor,
                Integer numeroParcela,
                Integer totalParcelas
        ) {}
}