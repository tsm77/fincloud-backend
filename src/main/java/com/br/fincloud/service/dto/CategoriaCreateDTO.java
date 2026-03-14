package com.br.fincloud.service.dto;

import com.br.fincloud.domain.TipoTransacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoriaCreateDTO(
        @NotBlank(message = "nome é obrigatório")
        String nome,

        @NotNull(message = "tipo é obrigatório")
        TipoTransacao tipo,

        String cor
) {}