package com.br.fincloud.controller;

import com.br.fincloud.service.TransacaoService;
import com.br.fincloud.service.dto.TransacaoCreateDTO;
import com.br.fincloud.service.dto.TransacaoResponseDTO;
import com.br.fincloud.service.dto.TransacaoUpdateDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@Tag(name = "Transações")
@RestController
@RequestMapping("/api/transacoes")
@CrossOrigin("*")
public class TransacaoController {

    private final TransacaoService service;

    public TransacaoController(TransacaoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<List<TransacaoResponseDTO>> cria(@RequestBody @Valid TransacaoCreateDTO dto) {
        return ResponseEntity.ok(service.criar(dto));
    }

    @GetMapping
    public ResponseEntity<List<TransacaoResponseDTO>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransacaoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<List<TransacaoResponseDTO>> editar(@PathVariable Long id,
                                                       @RequestBody @Valid TransacaoUpdateDTO dto) {
        return ResponseEntity.ok(service.editar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        service.remover(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/pago")
    public ResponseEntity<Void> atualizarPago(
            @PathVariable Long id,
            @RequestParam boolean pago
    ) {
        service.atualizarPago(id, pago);
        return ResponseEntity.ok().build();
    }
}