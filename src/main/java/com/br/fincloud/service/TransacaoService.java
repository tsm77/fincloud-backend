package com.br.fincloud.service;

import com.br.fincloud.domain.*;
import com.br.fincloud.repository.*;
import com.br.fincloud.service.dto.*;
import com.br.fincloud.service.exception.NotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransacaoService {

    private final TransacaoRepository transacaoRepository;
    private final ContaRepository contaRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public TransacaoService(
            TransacaoRepository transacaoRepository,
            ContaRepository contaRepository,
            CategoriaRepository categoriaRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.transacaoRepository = transacaoRepository;
        this.contaRepository = contaRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public TransacaoResponseDTO criar(TransacaoCreateDTO dto) {
        String email = emailLogado();

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuário logado não encontrado"));

        Conta conta = contaRepository.findByIdAndUsuarioEmail(dto.contaId(), email)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada"));

        Categoria categoria = categoriaRepository.findByIdAndUsuarioEmail(dto.categoriaId(), email)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));

        Transacao t = new Transacao();
        t.setUsuario(usuario);
        t.setConta(conta);
        t.setCategoria(categoria);
        t.setTipo(dto.tipo());
        t.setData(dto.data());
        t.setDescricao("Compra com itens"); // opcional

        java.math.BigDecimal total = java.math.BigDecimal.ZERO;

        for (var itemDTO : dto.itens()) {
            if (itemDTO.valor() == null || itemDTO.valor().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Valor do item deve ser maior que zero");
            }

            TransacaoItem item = new TransacaoItem();
            item.setDescricao(itemDTO.descricao());
            item.setValor(itemDTO.valor());
            item.setNumeroParcela(itemDTO.numeroParcela() == null ? 1 : itemDTO.numeroParcela());
            item.setTotalParcelas(itemDTO.totalParcelas() == null ? 1 : itemDTO.totalParcelas());

            t.addItem(item);

            total = total.add(item.getValor());
        }

        // ✅ define valor total no cabeçalho (recomendado ter esse campo)
        t.setValor(total);

        Transacao salva = transacaoRepository.save(t);

        aplicarEfeitoSaldo(conta, dto.tipo(), total);
        contaRepository.save(conta);

        return toResponseDTO(salva);
    }

    public List<TransacaoResponseDTO> listar() {
        String email = emailLogado();
        return transacaoRepository.findAllByUsuarioEmail(email)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public TransacaoResponseDTO buscarPorId(Long id) {
        String email = emailLogado();
        Transacao t = transacaoRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> new NotFoundException("Transação não encontrada"));
        return toResponseDTO(t);
    }

    @Transactional
    public TransacaoResponseDTO editar(Long id, TransacaoUpdateDTO dto) {
        String email = emailLogado();

        Transacao atual = transacaoRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> new NotFoundException("Transação não encontrada"));

        validarValor(dto.valor());

        // 1) DESFAZ efeito antigo no saldo da conta antiga
        Conta contaAntiga = atual.getConta();
        desfazerEfeitoSaldo(contaAntiga, atual.getTipo(), atual.getValor());
        contaRepository.save(contaAntiga);

        // 2) carrega nova conta/categoria (podem mudar)
        Conta novaConta = contaRepository.findByIdAndUsuarioEmail(dto.contaId(), email)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada"));

        Categoria novaCategoria = categoriaRepository.findByIdAndUsuarioEmail(dto.categoriaId(), email)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));

        // 3) atualiza transação
        atual.setConta(novaConta);
        atual.setCategoria(novaCategoria);
        atual.setTipo(dto.tipo());
        atual.setValor(dto.valor());
        atual.setData(dto.data());
        atual.setDescricao(dto.descricao());

        Transacao atualizada = transacaoRepository.save(atual);

        // 4) APLICA efeito novo na nova conta
        aplicarEfeitoSaldo(novaConta, dto.tipo(), dto.valor());
        contaRepository.save(novaConta);

        return toResponseDTO(atualizada);
    }

    @Transactional
    public void remover(Long id) {
        String email = emailLogado();

        Transacao t = transacaoRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> new NotFoundException("Transação não encontrada"));

        // desfaz efeito no saldo
        Conta conta = t.getConta();
        desfazerEfeitoSaldo(conta, t.getTipo(), t.getValor());
        contaRepository.save(conta);

        transacaoRepository.delete(t);
    }

    // -------- regras de saldo --------

    private void aplicarEfeitoSaldo(Conta conta, TipoTransacao tipo, BigDecimal valor) {
        if (conta.getSaldoInicial() == null) conta.setSaldoInicial(BigDecimal.ZERO);

        if (tipo == TipoTransacao.RECEITA) {
            conta.setSaldoInicial(conta.getSaldoInicial().add(valor));
        } else {
            conta.setSaldoInicial(conta.getSaldoInicial().subtract(valor));
        }
    }

    private void desfazerEfeitoSaldo(Conta conta, TipoTransacao tipo, BigDecimal valor) {
        // desfazer é o inverso
        if (tipo == TipoTransacao.RECEITA) {
            conta.setSaldoInicial(conta.getSaldoInicial().subtract(valor));
        } else {
            conta.setSaldoInicial(conta.getSaldoInicial().add(valor));
        }
    }

    private void validarValor(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("valor deve ser maior que zero");
        }
    }

    private TransacaoResponseDTO toResponseDTO(Transacao t) {
        return new TransacaoResponseDTO(
                t.getId(),
                t.getTipo(),
                t.getValor(),
                t.getData(),
                t.getDescricao(),
                t.getConta().getId(),
                t.getConta().getNome(),
                t.getCategoria().getId(),
                t.getCategoria().getNome(),
                t.getDataCriacao()
        );
    }

    private String emailLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new NotFoundException("Usuário não autenticado");
        }
        return auth.getPrincipal().toString();
    }
}