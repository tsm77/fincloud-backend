package com.br.fincloud.service.dto;

import com.br.fincloud.domain.TipoTransacao;

public record CategoriaResponseDTO(
        Long id,
        String nome,
        TipoTransacao tipo,
        String cor,
        Boolean ativa
) {}