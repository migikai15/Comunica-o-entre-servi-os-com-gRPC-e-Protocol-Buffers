package br.mackenzie.biblioteca.server;

import br.mackenzie.biblioteca.grpc.AutorRequest;
import br.mackenzie.biblioteca.grpc.BibliotecaServiceGrpc;
import br.mackenzie.biblioteca.grpc.CadastroResponse;
import br.mackenzie.biblioteca.grpc.Emprestimo;
import br.mackenzie.biblioteca.grpc.Livro;
import br.mackenzie.biblioteca.grpc.MensagemUsuario;
import br.mackenzie.biblioteca.grpc.ResumoEmprestimos;
import br.mackenzie.biblioteca.grpc.SugestaoLivro;
import br.mackenzie.biblioteca.model.Acervo;
import br.mackenzie.biblioteca.model.LivroModel;
import br.mackenzie.biblioteca.model.TextoUtil;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BibliotecaServiceImpl extends BibliotecaServiceGrpc.BibliotecaServiceImplBase {

    private static final Map<String, String[]> SUGESTOES = new LinkedHashMap<>();

    static {
        SUGESTOES.put("java", new String[]{"Effective Java", "Joshua Bloch"});
        SUGESTOES.put("python", new String[]{"Fluent Python", "Luciano Ramalho"});
        SUGESTOES.put("algoritmo", new String[]{"Algoritmos: Teoria e Prática", "Thomas H. Cormen"});
        SUGESTOES.put("grafo", new String[]{"Introduction to Graph Theory", "Douglas B. West"});
        SUGESTOES.put("distribuido", new String[]{"Designing Data-Intensive Applications", "Martin Kleppmann"});
        SUGESTOES.put("banco de dados", new String[]{"Sistemas de Banco de Dados", "Ramez Elmasri"});
        SUGESTOES.put("arquitetura", new String[]{"Arquitetura Limpa", "Robert C. Martin"});
        SUGESTOES.put("romance", new String[]{"Dom Casmurro", "Machado de Assis"});
        SUGESTOES.put("ficcao", new String[]{"1984", "George Orwell"});
        SUGESTOES.put("fantasia", new String[]{"O Senhor dos Anéis", "J. R. R. Tolkien"});
        SUGESTOES.put("historia", new String[]{"Sapiens", "Yuval Noah Harari"});
    }

    private static final String[] SUGESTAO_PADRAO = {"O Pequeno Príncipe", "Antoine de Saint-Exupéry"};

    private final Acervo acervo;

    public BibliotecaServiceImpl(Acervo acervo) {
        this.acervo = acervo;
    }

    @Override
    public void cadastrarLivro(Livro request, StreamObserver<CadastroResponse> responseObserver) {
        Log.info("cadastrarLivro(titulo='%s', autor='%s', ano=%d, isbn='%s')",
                request.getTitulo(), request.getAutor(), request.getAno(), request.getIsbn());

        if (request.getTitulo().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("O título é obrigatório.").asRuntimeException());
            return;
        }
        if (request.getAutor().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("O autor é obrigatório.").asRuntimeException());
            return;
        }
        if (request.getIsbn().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("O ISBN é obrigatório.").asRuntimeException());
            return;
        }
        if (request.getAno() <= 0 || request.getAno() > Year.now().getValue() + 1) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Ano inválido: " + request.getAno()).asRuntimeException());
            return;
        }

        Optional<LivroModel> salvo = acervo.cadastrar(
                request.getTitulo(), request.getAutor(), request.getAno(), request.getIsbn());

        if (salvo.isEmpty()) {
            responseObserver.onError(Status.ALREADY_EXISTS
                    .withDescription("Já existe um livro com o ISBN " + request.getIsbn()).asRuntimeException());
            return;
        }

        LivroModel livro = salvo.get();
        responseObserver.onNext(CadastroResponse.newBuilder()
                .setId(livro.id())
                .setStatus("CADASTRADO")
                .setMensagem("Livro '" + livro.titulo() + "' cadastrado com sucesso.")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void listarLivrosPorAutor(AutorRequest request, StreamObserver<Livro> responseObserver) {
        Log.info("listarLivrosPorAutor(autor='%s')", request.getAutor());

        if (request.getAutor().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("O nome do autor é obrigatório.").asRuntimeException());
            return;
        }

        List<LivroModel> encontrados = acervo.buscarPorAutor(request.getAutor());
        if (encontrados.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Nenhum livro encontrado para o autor '" + request.getAutor() + "'.")
                    .asRuntimeException());
            return;
        }

        for (LivroModel livro : encontrados) {
            responseObserver.onNext(paraProto(livro));
        }
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<Emprestimo> registrarEmprestimos(StreamObserver<ResumoEmprestimos> responseObserver) {
        final long inicio = System.nanoTime();
        Log.info("registrarEmprestimos(): stream aberto");

        return new StreamObserver<Emprestimo>() {
            private int registrados = 0;
            private int rejeitados = 0;

            @Override
            public void onNext(Emprestimo emprestimo) {
                Log.info("registrarEmprestimos -> onNext(usuario='%s', livro_id=%d)",
                        emprestimo.getUsuario(), emprestimo.getLivroId());

                if (emprestimo.getUsuario().isBlank() || !acervo.existeLivro(emprestimo.getLivroId())) {
                    rejeitados++;
                    Log.info("registrarEmprestimos -> REJEITADO (usuário vazio ou livro inexistente)");
                    return;
                }
                acervo.registrarEmprestimo(emprestimo.getUsuario(), emprestimo.getLivroId());
                registrados++;
            }

            @Override
            public void onError(Throwable t) {
                Log.info("registrarEmprestimos -> erro do cliente: %s", Status.fromThrowable(t));
            }

            @Override
            public void onCompleted() {
                long ms = (System.nanoTime() - inicio) / 1_000_000;
                Log.info("registrarEmprestimos -> concluído: %d registrados, %d rejeitados, %d ms",
                        registrados, rejeitados, ms);
                responseObserver.onNext(ResumoEmprestimos.newBuilder()
                        .setTotalRegistrados(registrados)
                        .setTotalRejeitados(rejeitados)
                        .setTempoTotalMs(ms)
                        .build());
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<MensagemUsuario> chatBibliotecario(StreamObserver<SugestaoLivro> responseObserver) {
        Log.info("chatBibliotecario(): chat aberto");

        return new StreamObserver<MensagemUsuario>() {
            @Override
            public void onNext(MensagemUsuario msg) {
                Log.info("chatBibliotecario -> onNext(usuario='%s', texto='%s')", msg.getUsuario(), msg.getTexto());

                if (msg.getTexto().isBlank()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("A mensagem não pode ser vazia.").asRuntimeException());
                    return;
                }

                String texto = TextoUtil.normalizar(msg.getTexto());
                String palavra = null;
                String[] livro = SUGESTAO_PADRAO;
                for (Map.Entry<String, String[]> e : SUGESTOES.entrySet()) {
                    if (texto.contains(e.getKey())) {
                        palavra = e.getKey();
                        livro = e.getValue();
                        break;
                    }
                }

                String resposta = (palavra != null)
                        ? "Para '" + palavra + "', eu recomendo: " + livro[0] + ", de " + livro[1] + "."
                        : "Não reconheci um tema, mas que tal: " + livro[0] + ", de " + livro[1] + "?";

                responseObserver.onNext(SugestaoLivro.newBuilder()
                        .setPalavraChave(palavra == null ? "" : palavra)
                        .setTitulo(livro[0])
                        .setAutor(livro[1])
                        .setMensagem(resposta)
                        .build());
            }

            @Override
            public void onError(Throwable t) {
                Log.info("chatBibliotecario -> erro do cliente: %s", Status.fromThrowable(t));
            }

            @Override
            public void onCompleted() {
                Log.info("chatBibliotecario -> chat encerrado pelo cliente");
                responseObserver.onCompleted();
            }
        };
    }

    private static Livro paraProto(LivroModel m) {
        return Livro.newBuilder()
                .setId(m.id())
                .setTitulo(m.titulo())
                .setAutor(m.autor())
                .setAno(m.ano())
                .setIsbn(m.isbn())
                .build();
    }
}
