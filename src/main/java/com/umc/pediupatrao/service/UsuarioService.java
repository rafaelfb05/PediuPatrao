package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Busca um usuário por ID
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    // Busca um usuário por username
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void salvarUsuario(Usuario usuario) {
        validarDados(usuario, null);
        if (usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            throw new IllegalArgumentException("A senha é obrigatória.");
        }
        usuario.setId(null);
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuarioRepository.save(usuario);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deletarUsuario(String id) {
        usuarioRepository.findById(id).ifPresent(alvo -> {
            if (alvo.getUsername().equals(usuarioLogado())) {
                throw new IllegalArgumentException("Você não pode excluir a sua própria conta.");
            }
            usuarioRepository.deleteById(id);
        });
    }

    // Lista todos os usuários
    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    // Atualiza o usuário usuários
    @PreAuthorize("hasRole('ADMIN')")
    public void atualizarUsuario(String id, Usuario usuarioAtualizado) {
        usuarioRepository.findById(id).ifPresent(usuarioExistente -> {
            validarDados(usuarioAtualizado, id);

            boolean mudouPerfil = !usuarioAtualizado.getRole().equals(usuarioExistente.getRole());
            if (mudouPerfil && usuarioExistente.getUsername().equals(usuarioLogado())) {
                throw new IllegalArgumentException("Você não pode alterar o seu próprio perfil.");
            }

            usuarioExistente.setUsername(usuarioAtualizado.getUsername());
            usuarioExistente.setRole(usuarioAtualizado.getRole());

            if (usuarioAtualizado.getPassword() != null && !usuarioAtualizado.getPassword().isBlank()) {
                usuarioExistente.setPassword(passwordEncoder.encode(usuarioAtualizado.getPassword()));
            }

            usuarioRepository.save(usuarioExistente);
        });
    }

    private void validarDados(Usuario usuario, String idAtual) {
        if (usuario.getUsername() == null || usuario.getUsername().isBlank()) {
            throw new IllegalArgumentException("O nome de usuário é obrigatório.");
        }
        if (!Usuario.perfilValido(usuario.getRole())) {
            throw new IllegalArgumentException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE.");
        }
        usuarioRepository.findByUsername(usuario.getUsername()).ifPresent(outro -> {
            if (idAtual == null || !outro.getId().equals(idAtual)) {
                throw new IllegalArgumentException("Já existe um usuário com esse nome.");
            }
        });
    }

    private String usuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
