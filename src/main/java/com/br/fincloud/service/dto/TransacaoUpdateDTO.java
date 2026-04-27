package com.br.fincloud.service.dto;

import com.br.fincloud.domain.TipoTransacao;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TransacaoUpdateDTO(
        Long contaId,
        Long categoriaId,
        TipoTransacao tipo,
        LocalDate data,
        List<TransacaoCreateDTO.ItemDTO> itens
) {}
