package com.br.fincloud.service;

import com.br.fincloud.domain.Conta;
import com.br.fincloud.domain.Usuario;
import com.br.fincloud.repository.ContaRepository;
import com.br.fincloud.repository.UsuarioRepository;
import com.br.fincloud.service.dto.ContaCreateDTO;
import com.br.fincloud.service.dto.ContaResponseDTO;
import com.br.fincloud.service.dto.ContaUpdateDTO;
import com.br.fincloud.service.exception.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ContaService {

    private final ContaRepository contaRepository;
    private final UsuarioRepository usuarioRepository;

    public ContaService(ContaRepository contaRepository, UsuarioRepository usuarioRepository) {
        this.contaRepository = contaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public ContaResponseDTO criar(ContaCreateDTO dto) {
        Usuario usuario = usuarioLogado();

        Conta conta = new Conta();
        conta.setUsuario(usuario);
        conta.setNome(dto.nome());
        conta.setTipo(dto.tipo());

        BigDecimal saldo = dto.saldoInicial() != null ? dto.saldoInicial() : BigDecimal.ZERO;
        conta.setSaldoInicial(saldo);

        Conta salva = contaRepository.save(conta);
        return toResponseDTO(salva);
    }

    public List<ContaResponseDTO> listar() {
        String email = emailLogado();
        return contaRepository.findAllByUsuarioEmail(email)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public ContaResponseDTO buscarPorId(Long id) {
        String email = emailLogado();
        Conta conta = contaRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada"));
        return toResponseDTO(conta);
    }

    public ContaResponseDTO editar(Long id, ContaUpdateDTO dto) {
        String email = emailLogado();
        Conta conta = contaRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada"));

        conta.setNome(dto.nome());
        conta.setTipo(dto.tipo());

        if (dto.saldoInicial() != null) {
            conta.setSaldoInicial(dto.saldoInicial());
        }

        if (dto.ativa() != null) {
            conta.setAtiva(dto.ativa());
        }

        Conta atualizada = contaRepository.save(conta);
        return toResponseDTO(atualizada);
    }

    public void remover(Long id) {
        String email = emailLogado();
        Conta conta = contaRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada"));
        contaRepository.delete(conta);
    }

    private ContaResponseDTO toResponseDTO(Conta c) {
        return new ContaResponseDTO(
                c.getId(),
                c.getNome(),
                c.getTipo(),
                c.getSaldoInicial(),
                c.getAtiva(),
                c.getDataCriacao()
        );
    }

    private Usuario usuarioLogado() {
        String email = emailLogado();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuário logado não encontrado"));
    }

    private String emailLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getPrincipal() == null) {
            throw new NotFoundException("Usuário não autenticado");
        }

        Object principal = auth.getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        return principal.toString();
    }
}