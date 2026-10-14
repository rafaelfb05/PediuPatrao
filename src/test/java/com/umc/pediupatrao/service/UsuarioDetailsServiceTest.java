package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioDetailsServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    private List<String> perfisDe(String perfilNoBanco) {
        Usuario usuario = new Usuario();
        usuario.setUsername("ana");
        usuario.setPassword("hash");
        usuario.setRole(perfilNoBanco);
        when(usuarioRepository.findByUsername("ana")).thenReturn(Optional.of(usuario));

        UserDetails detalhes = new UsuarioDetailsService(usuarioRepository).loadUserByUsername("ana");
        return detalhes.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GERENTE", "ATENDENTE"})
    @DisplayName("Login: cada perfil do banco vira a authority correspondente")
    void perfilDoBancoViraAuthority(String perfil) {
        assertEquals(List.of("ROLE_" + perfil), perfisDe(perfil));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"USER", "SUPERADMIN"})
    @DisplayName("Login: perfil ausente ou inválido recebe o menor privilégio (ATENDENTE)")
    void perfilInvalidoRecebeMenorPrivilegio(String perfil) {
        assertEquals(List.of("ROLE_ATENDENTE"), perfisDe(perfil));
    }

    @Test
    @DisplayName("Login: usuário inexistente gera UsernameNotFoundException")
    void usuarioInexistente() {
        when(usuarioRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> new UsuarioDetailsService(usuarioRepository).loadUserByUsername("fantasma"));
    }
}