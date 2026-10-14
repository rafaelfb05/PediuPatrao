package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UsuarioService usuarioService;

    @BeforeEach
    void preparar() {
        usuarioService = new UsuarioService(usuarioRepository, passwordEncoder);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void limparSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Criar usuário: senha é criptografada e o id enviado é ignorado")
    void criarUsuarioCriptografaSenhaEIgnoraId() {
        Usuario novo = usuario("id-de-outro-usuario", "joao", "senha123", "ATENDENTE");

        usuarioService.salvarUsuario(novo);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertNull(captor.getValue().getId());
        assertEquals("hash", captor.getValue().getPassword());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"USER", "SUPERADMIN", ""})
    @DisplayName("Criar usuário: perfil que não existe é recusado")
    void criarUsuarioRecusaPerfilInvalido(String perfil) {
        Usuario novo = usuario(null, "joao", "senha123", perfil);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> usuarioService.salvarUsuario(novo));

        assertTrue(erro.getMessage().contains("Perfil inválido"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Criar usuário: nome de usuário repetido é recusado")
    void criarUsuarioRecusaUsernameDuplicado() {
        when(usuarioRepository.findByUsername("joao"))
                .thenReturn(Optional.of(usuario("u9", "joao", "x", "ATENDENTE")));

        assertThrows(IllegalArgumentException.class,
                () -> usuarioService.salvarUsuario(usuario(null, "joao", "senha123", "ATENDENTE")));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Criar usuário: senha é obrigatória")
    void criarUsuarioExigeSenha() {
        assertThrows(IllegalArgumentException.class,
                () -> usuarioService.salvarUsuario(usuario(null, "joao", "", "ATENDENTE")));
    }

    @Test
    @DisplayName("Autoelevação: o usuário não consegue alterar o próprio perfil")
    void naoAlteraProprioPerfil() {
        Usuario existente = usuario("u1", "admin", "hash", "ADMIN");
        when(usuarioRepository.findById("u1")).thenReturn(Optional.of(existente));
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(existente));

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> usuarioService.atualizarUsuario("u1", usuario(null, "admin", null, "GERENTE")));

        assertTrue(erro.getMessage().contains("próprio perfil"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Admin consegue alterar o perfil de outro usuário")
    void alteraPerfilDeOutroUsuario() {
        Usuario existente = usuario("u2", "maria", "hash", "ATENDENTE");
        when(usuarioRepository.findById("u2")).thenReturn(Optional.of(existente));
        when(usuarioRepository.findByUsername("maria")).thenReturn(Optional.of(existente));

        usuarioService.atualizarUsuario("u2", usuario(null, "maria", null, "GERENTE"));

        assertEquals("GERENTE", existente.getRole());
        verify(usuarioRepository).save(existente);
    }

    @Test
    @DisplayName("Editar usuário: senha em branco mantém a senha atual")
    void senhaEmBrancoMantemSenhaAtual() {
        Usuario existente = usuario("u2", "maria", "hash-antigo", "ATENDENTE");
        when(usuarioRepository.findById("u2")).thenReturn(Optional.of(existente));
        when(usuarioRepository.findByUsername("maria")).thenReturn(Optional.of(existente));

        usuarioService.atualizarUsuario("u2", usuario(null, "maria", "", "ATENDENTE"));

        assertEquals("hash-antigo", existente.getPassword());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("Excluir usuário: ninguém exclui a própria conta")
    void naoExcluiPropriaConta() {
        when(usuarioRepository.findById("u1")).thenReturn(Optional.of(usuario("u1", "admin", "hash", "ADMIN")));

        assertThrows(IllegalArgumentException.class, () -> usuarioService.deletarUsuario("u1"));
        verify(usuarioRepository, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("Excluir usuário: o admin exclui outro usuário")
    void excluiOutroUsuario() {
        when(usuarioRepository.findById("u2")).thenReturn(Optional.of(usuario("u2", "maria", "hash", "ATENDENTE")));

        usuarioService.deletarUsuario("u2");

        verify(usuarioRepository).deleteById("u2");
    }

    private Usuario usuario(String id, String username, String senha, String perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setUsername(username);
        usuario.setPassword(senha);
        usuario.setRole(perfil);
        return usuario;
    }
}
