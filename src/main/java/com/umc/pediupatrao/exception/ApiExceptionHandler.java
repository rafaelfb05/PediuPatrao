package com.umc.pediupatrao.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> dadosInvalidos(IllegalArgumentException ex) {
        return resposta(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> naoEncontrado(RecursoNaoEncontradoException ex) {
        return resposta(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(EstadoInvalidoException.class)
    public ResponseEntity<Map<String, String>> estadoInvalido(EstadoInvalidoException ex) {
        return resposta(HttpStatus.CONFLICT, ex);
    }

    private ResponseEntity<Map<String, String>> resposta(HttpStatus status, Exception ex) {
        return ResponseEntity.status(status).body(Map.of("erro", String.valueOf(ex.getMessage())));
    }
}