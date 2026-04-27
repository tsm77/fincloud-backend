package com.br.fincloud.service;

import com.br.fincloud.domain.*;
import com.br.fincloud.repository.*;
import com.br.fincloud.service.dto.*;
import com.br.fincloud.service.exception.NotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
    public List<TransacaoResponseDTO> criar(TransacaoCreateDTO dto) {

        String email = emailLogado();

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuário logado não encontrado"));

        Conta conta = contaRepository.findByIdAndUsuarioEmail(dto.contaId(), email)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada"));

        Categoria categoria = categoriaRepository.findByIdAndUsuarioEmail(dto.categoriaId(), email)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));

        List<Transacao> transacoesGeradas = new ArrayList<>();

        for (var itemDTO : dto.itens()) {

            if (itemDTO.valor() == null || itemDTO.valor().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Valor do item deve ser maior que zero");
            }

            int totalParcelas = itemDTO.totalParcelas() == null ? 1 : itemDTO.totalParcelas();

            // 💰 VALOR TOTAL DO ITEM
            BigDecimal valorTotal = itemDTO.valor();

            // 💰 VALOR DE CADA PARCELA
            BigDecimal valorParcela = valorTotal.divide(
                    BigDecimal.valueOf(totalParcelas),
                    2,
                    RoundingMode.HALF_UP
            );

            for (int parcela = 1; parcela <= totalParcelas; parcela++) {

                Transacao t = new Transacao();
                t.setUsuario(usuario);
                t.setConta(conta);
                t.setCategoria(categoria);
                t.setTipo(dto.tipo());

                // 📅 data ajustada por parcela
                t.setData(dto.data().plusMonths(parcela - 1));

                // 🧠 descrição com parcela
                String descricao = itemDTO.descricao();

                if (totalParcelas > 1) {
                    descricao += " (" + parcela + "/" + totalParcelas + ")";
                }

                t.setDescricao(descricao);

                // 💰 agora CORRETO → valor da parcela
                t.setValor(valorParcela);

                Transacao salva = transacaoRepository.save(t);
                transacoesGeradas.add(salva);

                // 💳 aplica efeito no saldo com valor da parcela
                aplicarEfeitoSaldo(conta, dto.tipo(), valorParcela);
            }
        }

        contaRepository.save(conta);

        return transacoesGeradas.stream()
                .map(this::toResponseDTO)
                .toList();
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
    public List<TransacaoResponseDTO> editar(Long id, TransacaoUpdateDTO dto) {

        String email = emailLogado();

        Transacao atual = transacaoRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> new NotFoundException("Transação não encontrada"));

        Conta conta = atual.getConta();

        // 🔥 1. DESFAZ saldo antigo
        desfazerEfeitoSaldo(conta, atual.getTipo(), atual.getValor());
        contaRepository.save(conta);

        // 🔥 2. REMOVE transação antiga
        transacaoRepository.delete(atual);

        // 🔥 3. RECRIA usando mesma lógica do criar
        return criar(new TransacaoCreateDTO(
                dto.contaId(),
                dto.categoriaId(),
                dto.tipo(),
                dto.data(),
                dto.itens()
        ));
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

    @Transactional
    public void atualizarPago(Long id, boolean pago) {
        Transacao t = transacaoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transação não encontrada"));

        t.setPago(pago);
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
                t.getDataCriacao(),
                t.getPago() != null ? t.getPago() : false
        );
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