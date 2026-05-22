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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TransacaoService {
    private static final Pattern PARCELA_PATTERN = Pattern.compile("^(.*?)\\s*\\((\\d+)/(\\d+)\\)$");

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
                t.setNumeroParcela(parcela);
                t.setTotalParcelas(totalParcelas);

                // 🧠 descrição com parcela
                t.setDescricao(formatarDescricaoParcela(itemDTO.descricao(), parcela, totalParcelas));

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

        Conta novaConta = contaRepository.findByIdAndUsuarioEmail(dto.contaId(), email)
                .orElseThrow(() -> new NotFoundException("Conta nÃ£o encontrada"));

        Categoria novaCategoria = categoriaRepository.findByIdAndUsuarioEmail(dto.categoriaId(), email)
                .orElseThrow(() -> new NotFoundException("Categoria nÃ£o encontrada"));

        TransacaoCreateDTO.ItemDTO itemDTO = dto.itens() == null || dto.itens().isEmpty()
                ? new TransacaoCreateDTO.ItemDTO(atual.getDescricao(), atual.getValor(), atual.getNumeroParcela(), atual.getTotalParcelas())
                : dto.itens().get(0);

        ParcelaInfo parcelaAtual = obterParcelaInfo(atual);
        if (parcelaAtual.total() <= 1) {
            int totalInferido = inferirTotalParcelas(email, parcelaAtual.descricaoBase());
            if (totalInferido > 1) {
                parcelaAtual = new ParcelaInfo(parcelaAtual.descricaoBase(), 1, totalInferido);
            }
        }

        String novaDescricaoBase = descricaoBase(itemDTO.descricao());
        BigDecimal novoValor = itemDTO.valor() != null ? itemDTO.valor() : atual.getValor();
        TipoTransacao novoTipo = dto.tipo() != null ? dto.tipo() : atual.getTipo();
        LocalDate novaDataBase = dto.data() != null ? dto.data() : atual.getData();
        int novoTotalParcelas = itemDTO.totalParcelas() != null ? itemDTO.totalParcelas() : parcelaAtual.total();

        if (novoValor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Valor do item deve ser maior que zero");
        }

        if (novoTotalParcelas <= 0) {
            throw new RuntimeException("Total de parcelas deve ser maior que zero");
        }

        BigDecimal novoValorParcela = novoValor.divide(
                BigDecimal.valueOf(novoTotalParcelas),
                2,
                RoundingMode.HALF_UP
        );
        LocalDate primeiraData = novaDataBase.minusMonths(Math.max(parcelaAtual.numero() - 1, 0));

        List<Transacao> transacoesParaAtualizar = buscarParcelasParaAtualizar(email, atual, parcelaAtual);
        Map<Integer, Transacao> transacoesPorNumero = transacoesParaAtualizar.stream()
                .collect(Collectors.toMap(
                        transacao -> obterParcelaInfo(transacao).numero(),
                        Function.identity(),
                        (primeira, segunda) -> primeira
                ));
        List<Transacao> transacoesAtualizadas = new ArrayList<>();

        for (Transacao transacao : transacoesParaAtualizar) {
            int numeroParcela = obterParcelaInfo(transacao).numero();

            if (numeroParcela > novoTotalParcelas) {
                Conta contaAntiga = transacao.getConta();
                desfazerEfeitoSaldo(contaAntiga, transacao.getTipo(), transacao.getValor());
                contaRepository.save(contaAntiga);
                transacaoRepository.delete(transacao);
            }
        }

        for (int numeroParcela = 1; numeroParcela <= novoTotalParcelas; numeroParcela++) {
            Transacao transacao = transacoesPorNumero.get(numeroParcela);

            if (transacao == null) {
                transacao = new Transacao();
                transacao.setUsuario(atual.getUsuario());
            } else {
                Conta contaAntiga = transacao.getConta();
                desfazerEfeitoSaldo(contaAntiga, transacao.getTipo(), transacao.getValor());
                contaRepository.save(contaAntiga);
            }

            transacao.setConta(novaConta);
            transacao.setCategoria(novaCategoria);
            transacao.setTipo(novoTipo);
            transacao.setValor(novoValorParcela);
            transacao.setData(primeiraData.plusMonths(numeroParcela - 1));
            transacao.setNumeroParcela(numeroParcela);
            transacao.setTotalParcelas(novoTotalParcelas);
            transacao.setDescricao(formatarDescricaoParcela(novaDescricaoBase, numeroParcela, novoTotalParcelas));

            aplicarEfeitoSaldo(novaConta, novoTipo, novoValorParcela);

            contaRepository.save(novaConta);
            transacoesAtualizadas.add(transacaoRepository.save(transacao));
        }

        return transacoesAtualizadas.stream()
                .sorted(Comparator.comparing(Transacao::getData))
                .map(this::toResponseDTO)
                .toList();

        // 🔥 1. DESFAZ saldo antigo

        // 🔥 2. REMOVE transação antiga

        // 🔥 3. RECRIA usando mesma lógica do criar
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
        BigDecimal saldoAtual = conta.getSaldoAtual() != null ? conta.getSaldoAtual() : BigDecimal.ZERO;

        if (tipo == TipoTransacao.DESPESA) {
            conta.setSaldoAtual(saldoAtual.subtract(valor));
        } else {
            conta.setSaldoAtual(saldoAtual.add(valor));
        }
    }

    private void desfazerEfeitoSaldo(Conta conta, TipoTransacao tipo, BigDecimal valor) {
        // desfazer é o inverso
        BigDecimal saldoAtual = conta.getSaldoAtual() != null ? conta.getSaldoAtual() : BigDecimal.ZERO;

        if (tipo == TipoTransacao.DESPESA) {
            conta.setSaldoAtual(saldoAtual.add(valor));
        } else {
            conta.setSaldoAtual(saldoAtual.subtract(valor));
        }
    }

    @Transactional
    public void atualizarPago(Long id, boolean pago) {
        Transacao t = transacaoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transação não encontrada"));

        t.setPago(pago);
    }

    private TransacaoResponseDTO toResponseDTO(Transacao t) {
        ParcelaInfo parcela = obterParcelaInfo(t);

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
                t.getPago() != null ? t.getPago() : false,
                parcela.numero(),
                parcela.total()
        );
    }

    private List<Transacao> buscarParcelasParaAtualizar(String email, Transacao atual, ParcelaInfo parcelaAtual) {
        if (parcelaAtual.total() <= 1) {
            return List.of(atual);
        }

        return transacaoRepository.findAllByUsuarioEmail(email)
                .stream()
                .filter(transacao -> {
                    if (transacao.getId().equals(atual.getId())) {
                        return true;
                    }

                    ParcelaInfo parcela = obterParcelaInfo(transacao);

                    return parcela.descricaoBase().equalsIgnoreCase(parcelaAtual.descricaoBase())
                            && parcela.total() == parcelaAtual.total()
                            && parcela.numero() <= parcelaAtual.total();
                })
                .sorted(Comparator.comparingInt(transacao -> obterParcelaInfo(transacao).numero()))
                .toList();
    }

    private int inferirTotalParcelas(String email, String descricaoBase) {
        return transacaoRepository.findAllByUsuarioEmail(email)
                .stream()
                .map(this::obterParcelaInfo)
                .filter(parcela -> parcela.descricaoBase().equalsIgnoreCase(descricaoBase))
                .mapToInt(ParcelaInfo::total)
                .max()
                .orElse(1);
    }

    private ParcelaInfo obterParcelaInfo(Transacao transacao) {
        String descricao = transacao.getDescricao() != null ? transacao.getDescricao() : "";
        Matcher matcher = PARCELA_PATTERN.matcher(descricao);

        if (matcher.matches()) {
            return new ParcelaInfo(
                    matcher.group(1).trim(),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );
        }

        int numeroParcela = transacao.getNumeroParcela() != null ? transacao.getNumeroParcela() : 1;
        int totalParcelas = transacao.getTotalParcelas() != null ? transacao.getTotalParcelas() : 1;

        return new ParcelaInfo(descricao.trim(), numeroParcela, totalParcelas);
    }

    private String descricaoBase(String descricao) {
        if (descricao == null) {
            return "";
        }

        Matcher matcher = PARCELA_PATTERN.matcher(descricao);

        return matcher.matches() ? matcher.group(1).trim() : descricao.trim();
    }

    private String formatarDescricaoParcela(String descricaoBase, int parcela, int totalParcelas) {
        String base = descricaoBase(descricaoBase);

        if (totalParcelas > 1 && parcela > 1) {
            return base + " (" + parcela + "/" + totalParcelas + ")";
        }

        return base;
    }

    private record ParcelaInfo(String descricaoBase, int numero, int total) {
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
