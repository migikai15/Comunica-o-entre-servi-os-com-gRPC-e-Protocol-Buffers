package br.mackenzie.biblioteca.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class Acervo {

    private final Map<Long, LivroModel> livros = new HashMap<>();
    private final Map<String, Long> indicePorIsbn = new HashMap<>();
    private final List<EmprestimoModel> emprestimos = new ArrayList<>();
    private long proximoId = 1;

    public synchronized Optional<LivroModel> cadastrar(String titulo, String autor, int ano, String isbn) {
        String chaveIsbn = normalizarIsbn(isbn);
        if (indicePorIsbn.containsKey(chaveIsbn)) {
            return Optional.empty();
        }
        LivroModel livro = new LivroModel(proximoId++, titulo.trim(), autor.trim(), ano, isbn.trim());
        livros.put(livro.id(), livro);
        indicePorIsbn.put(chaveIsbn, livro.id());
        return Optional.of(livro);
    }

    public synchronized List<LivroModel> buscarPorAutor(String autor) {
        String alvo = TextoUtil.normalizar(autor);
        List<LivroModel> resultado = new ArrayList<>();
        for (LivroModel livro : livros.values()) {
            if (TextoUtil.normalizar(livro.autor()).equals(alvo)) {
                resultado.add(livro);
            }
        }
        resultado.sort((a, b) -> Long.compare(a.id(), b.id()));
        return resultado;
    }

    public synchronized boolean existeLivro(long id) {
        return livros.containsKey(id);
    }

    public synchronized void registrarEmprestimo(String usuario, long livroId) {
        emprestimos.add(new EmprestimoModel(usuario.trim(), livroId, Instant.now()));
    }

    public synchronized int totalLivros() {
        return livros.size();
    }

    public synchronized int totalEmprestimos() {
        return emprestimos.size();
    }

    private static String normalizarIsbn(String isbn) {
        return isbn.replaceAll("[\\s-]", "").toUpperCase();
    }
}
