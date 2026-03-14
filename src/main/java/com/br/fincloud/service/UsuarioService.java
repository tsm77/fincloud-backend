package com.br.fincloud.service;

import com.br.fincloud.domain.Usuario;
import com.br.fincloud.repository.UsuarioRepository;
import com.br.fincloud.service.dto.UsuarioRequestDTO;
import com.br.fincloud.service.dto.UsuarioResponseDTO;
import com.br.fincloud.service.dto.UsuarioUpdateDTO;
import com.br.fincloud.service.exception.ConflictException;
import com.br.fincloud.service.exception.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }


    public UsuarioResponseDTO criar(UsuarioRequestDTO dto) {
        if (repository.existsByEmail(dto.email())) {
            throw new ConflictException("Email já cadastrado");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(dto.nome());
        usuario.setEmail(dto.email());
        usuario.setSenha(passwordEncoder.encode(dto.senha()));

        Usuario salvo = repository.save(usuario);
        return toResponseDTO(salvo);
    }

    public List<UsuarioResponseDTO> listar() {
        return repository.findAll().stream().map(this::toResponseDTO).toList();
    }

    public UsuarioResponseDTO editar(Long id, UsuarioUpdateDTO dto) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        if (repository.existsByEmailAndIdNot(dto.email(), id)) {
            throw new ConflictException("Email já cadastrado");
        }

        usuario.setNome(dto.nome());
        usuario.setEmail(dto.email());

        if (dto.senha() != null && !dto.senha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(dto.senha()));
        }

        Usuario atualizado = repository.save(usuario);
        return toResponseDTO(atualizado);
    }


    public void remover(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Usuário não encontrado");
        }
        repository.deleteById(id);
    }

    private UsuarioResponseDTO toResponseDTO(Usuario u) {
        return new UsuarioResponseDTO(u.getId(), u.getNome(), u.getEmail(), u.getDataCriacao());
    }
}