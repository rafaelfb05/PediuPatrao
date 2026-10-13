package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, ProdutoRepository produtoRepo) {
        return args -> {
                BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

            garantirUsuario(repo, encoder, "admin", "teste123", Usuario.ADMIN);
            garantirUsuario(repo, encoder, "gerente", "teste123", Usuario.GERENTE);
            garantirUsuario(repo, encoder, "atendente", "teste123", Usuario.ATENDENTE);

            if (produtoRepo.count() == 0) {
                criarProduto(produtoRepo, "Pizza Calabresa", "Pizza", 45.90);
                criarProduto(produtoRepo, "Pizza Mussarela", "Pizza", 42.00);
                criarProduto(produtoRepo, "Esfiha de Carne", "Lanche", 6.50);
                criarProduto(produtoRepo, "Refrigerante 2L", "Bebida", 12.00);
                criarProduto(produtoRepo, "Batata Frita", "Porção", 22.00);
                System.out.println("Produtos de exemplo criados.");
            }
        };
    }
    private void garantirUsuario(UsuarioRepository repo, BCryptPasswordEncoder encoder,
                                 String username, String senha, String perfil) {
        repo.findByUsername(username).ifPresentOrElse(existente -> {
            if (!Usuario.perfilValido(existente.getRole())) {
                existente.setRole(perfil);
                repo.save(existente);
                System.out.println("Perfil do usuário " + username + " corrigido para " + perfil);
            }
        }, () -> {
            Usuario user = new Usuario();
            user.setUsername(username);
            user.setPassword(encoder.encode(senha));
            user.setRole(perfil);
            repo.save(user);
            System.out.println("Usuário " + username + " criado com perfil " + perfil);
        });
    }

    private void criarProduto(ProdutoRepository produtoRepo, String nome, String tipo, double preco) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(preco);
        produto.setAtivo(true);
        produtoRepo.save(produto);
    }
}