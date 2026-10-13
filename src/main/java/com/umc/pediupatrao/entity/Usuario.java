package com.umc.pediupatrao.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

@Document(collection = "usuarios") // Define a coleção no MongoDB
public class Usuario {

    public static final String ADMIN = "ADMIN";
    public static final String GERENTE = "GERENTE";
    public static final String ATENDENTE = "ATENDENTE";
    public static final Set<String> PERFIS_VALIDOS = Set.of(ADMIN, GERENTE, ATENDENTE);


    public static boolean perfilValido(String role) {
        return role != null && PERFIS_VALIDOS.contains(role);
    }

    @Id
    private String id;
    private String username; // Nome de usuário para login
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password; // Senha criptografada
    private String role;     // Papel do usuário, como ADMIN ou USER

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
    
    
}
