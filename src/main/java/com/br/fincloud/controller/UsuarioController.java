    package com.br.fincloud.controller;

    import com.br.fincloud.domain.Usuario;
    import com.br.fincloud.repository.UsuarioRepository;
    import com.br.fincloud.security.JwtService;
    import com.br.fincloud.service.UsuarioService;
    import com.br.fincloud.service.dto.*;
    import io.swagger.v3.oas.annotations.Operation;
    import io.swagger.v3.oas.annotations.tags.Tag;
    import jakarta.validation.Valid;
    import org.springframework.http.ResponseEntity;
    import org.springframework.security.crypto.password.PasswordEncoder;
    import org.springframework.web.bind.annotation.*;

    import java.util.List;

    @Tag(name = "Usuarios")
    @RestController
    @RequestMapping("/api/usuarios")
    @CrossOrigin("*")
    public class UsuarioController {


        private final UsuarioService service;

        private final UsuarioRepository repository;
        private final JwtService jwtService;
        private final PasswordEncoder passwordEncoder;

        public UsuarioController(UsuarioService service, UsuarioRepository repository, JwtService jwtService, PasswordEncoder passwordEncoder) {
            this.service = service;
            this.repository = repository;
            this.jwtService = jwtService;
            this.passwordEncoder = passwordEncoder;
        }

        @PostMapping
        public ResponseEntity<UsuarioResponseDTO> criar(@RequestBody @Valid UsuarioRequestDTO dto) {
            return ResponseEntity.ok(service.criar(dto));
        }

        @GetMapping
        public ResponseEntity<List<UsuarioResponseDTO>> listar() {
            return ResponseEntity.ok(service.listar());
        }

        @Operation(summary = "Login", description = "Retorna um token JWT")
        @PostMapping("/login")
        public ResponseEntity<?> login(@RequestBody @Valid LoginRequestDTO loginDTO) {

            String email = loginDTO.email().trim().toLowerCase();

            Usuario usuario = repository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

            if (!passwordEncoder.matches(
                    loginDTO.senha(),
                    usuario.getSenha()
            )) {
                throw new RuntimeException("Senha inválida");
            }

            String token = jwtService.gerarToken(usuario.getEmail());
            return ResponseEntity.ok(new LoginResponseDTO(token));
        }

        @PutMapping("/{id}")
        public ResponseEntity<UsuarioResponseDTO> editar(@PathVariable Long id,
                                                         @RequestBody @Valid UsuarioUpdateDTO dto) {
            return ResponseEntity.ok(service.editar(id, dto));
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Void> remover(@PathVariable Long id) {
            service.remover(id);
            return ResponseEntity.noContent().build();
        }
    }