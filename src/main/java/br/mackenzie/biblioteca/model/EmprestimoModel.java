package br.mackenzie.biblioteca.model;

import java.time.Instant;

public record EmprestimoModel(String usuario, long livroId, Instant data) {
}
