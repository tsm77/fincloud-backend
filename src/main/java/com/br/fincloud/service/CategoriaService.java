package com.br.fincloud.service;

import com.br.fincloud.domain.Categoria;
import com.br.fincloud.domain.Usuario;
import com.br.fincloud.repository.CategoriaRepository;
import com.br.fincloud.repository.UsuarioRepository;
import com.br.fincloud.service.dto.CategoriaCreateDTO;
import com.br.fincloud.service.dto.CategoriaResponseDTO;
import com.br.fincloud.service.dto.CategoriaUpdateDTO;
import com.br.fincloud.service.exception.ConflictException;
import com.br.fincloud.service.exception.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public CategoriaService(CategoriaRepository categoriaRepository,
                            UsuarioRepository usuarioRepository) {
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public CategoriaResponseDTO criar(CategoriaCreateDTO dto) {
        String email = emailLogado();

        if (categoriaRepository.existsByUsuarioEmailAndNomeIgnoreCaseAndTipo(email, dto.nome(), dto.tipo())) {
            throw new ConflictException("Categoria já existe para esse tipo");
        }

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuário logado não encontrado"));

        Categoria categoria = new Categoria();
        categoria.setUsuario(usuario);
        categoria.setNome(dto.nome());
        categoria.setTipo(dto.tipo());
        categoria.setCor(dto.cor());
        categoria.setAtiva(true);

        Categoria salva = categoriaRepository.save(categoria);
        return toResponseDTO(salva);
    }

    public List<CategoriaResponseDTO> listar() {
        String email = emailLogado();
        return categoriaRepository.findAllByUsuarioEmail(email)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public CategoriaResponseDTO buscarPorId(Long id) {
        String email = emailLogado();
        Categoria categoria = categoriaRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));
        return toResponseDTO(categoria);
    }

    public CategoriaResponseDTO editar(Long id, CategoriaUpdateDTO dto) {
        String email = emailLogado();

        Categoria categoria = categoriaRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));

        if (categoriaRepository.existsByUsuarioEmailAndNomeIgnoreCaseAndTipoAndIdNot(email, dto.nome(), dto.tipo(), id)) {
            throw new ConflictException("Categoria já existe para esse tipo");
        }

        categoria.setNome(dto.nome());
        categoria.setTipo(dto.tipo());
        categoria.setCor(dto.cor());

        if (dto.ativa() != null) {
            categoria.setAtiva(dto.ativa());
        }

        Categoria atualizada = categoriaRepository.save(categoria);
        return toResponseDTO(atualizada);
    }

    public void remover(Long id) {
        String email = emailLogado();
        Categoria categoria = categoriaRepository.findByIdAndUsuarioEmail(id, email)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));

        categoriaRepository.delete(categoria);
    }

    private CategoriaResponseDTO toResponseDTO(Categoria c) {
        return new CategoriaResponseDTO(
                c.getId(),
                c.getNome(),
                c.getTipo(),
                c.getCor(),
                c.getAtiva()
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