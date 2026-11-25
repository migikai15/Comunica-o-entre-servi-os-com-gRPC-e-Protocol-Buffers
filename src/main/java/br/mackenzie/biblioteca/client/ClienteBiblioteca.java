package br.mackenzie.biblioteca.client;

import br.mackenzie.biblioteca.grpc.AutorRequest;
import br.mackenzie.biblioteca.grpc.BibliotecaServiceGrpc;
import br.mackenzie.biblioteca.grpc.CadastroResponse;
import br.mackenzie.biblioteca.grpc.Emprestimo;
import br.mackenzie.biblioteca.grpc.Livro;
import br.mackenzie.biblioteca.grpc.MensagemUsuario;
import br.mackenzie.biblioteca.grpc.ResumoEmprestimos;
import br.mackenzie.biblioteca.grpc.SugestaoLivro;
import br.mackenzie.biblioteca.model.Seguranca;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ClienteBiblioteca {

    private static final long DEADLINE_SEGUNDOS = 5;

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 50051;

        Metadata md = new Metadata();
        md.put(Seguranca.AUTHORIZATION, Seguranca.TOKEN_VALIDO);
        ManagedChannel canal = ManagedChannelBuilder.forAddress(host, porta)
                .usePlaintext()
                .intercept(MetadataUtils.newAttachHeadersInterceptor(md))
                .build();

        BibliotecaServiceGrpc.BibliotecaServiceBlockingStub bloqueante = BibliotecaServiceGrpc.newBlockingStub(canal);
        BibliotecaServiceGrpc.BibliotecaServiceStub assincrono = BibliotecaServiceGrpc.newStub(canal);

        try {
            List<Long> ids = testeCadastro(bloqueante);
            testeListarAutorExistente(bloqueante);
            testeListarAutorInexistente(bloqueante);
            testeEmprestimos(assincrono, ids);
            testeChat(assincrono);
            testeIsbnDuplicado(bloqueante);
            testeArgumentoInvalido(bloqueante);
            testeSemToken(host, porta);
        } finally {
            canal.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
        System.out.println("\n=== Fim da demonstração ===");
    }

    private static List<Long> testeCadastro(BibliotecaServiceGrpc.BibliotecaServiceBlockingStub stub) {
        titulo("1) Unary RPC — cadastrarLivro (3 livros)");
        List<Long> ids = new ArrayList<>();
        Livro[] livros = {
                livro("Effective Java", "Joshua Bloch", 2018, "978-0134685991"),
                livro("Java Puzzlers", "Joshua Bloch", 2005, "978-0321336781"),
                livro("Clean Code", "Robert C. Martin", 2008, "978-0132350884")
        };
        for (Livro l : livros) {
            CadastroResponse r = stub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS).cadastrarLivro(l);
            System.out.printf("  [%s] id=%d | %s%n", r.getStatus(), r.getId(), r.getMensagem());
            ids.add(r.getId());
        }
        return ids;
    }

    private static void testeListarAutorExistente(BibliotecaServiceGrpc.BibliotecaServiceBlockingStub stub) {
        titulo("2) Server Streaming — listarLivrosPorAutor (autor cadastrado)");
        Iterator<Livro> it = stub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS)
                .listarLivrosPorAutor(AutorRequest.newBuilder().setAutor("Joshua Bloch").build());
        while (it.hasNext()) {
            Livro l = it.next();
            System.out.printf("  id=%d | %s (%d) | ISBN %s%n", l.getId(), l.getTitulo(), l.getAno(), l.getIsbn());
        }
    }

    private static void testeListarAutorInexistente(BibliotecaServiceGrpc.BibliotecaServiceBlockingStub stub) {
        titulo("3) Server Streaming — autor inexistente (esperado: NOT_FOUND)");
        try {
            Iterator<Livro> it = stub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS)
                    .listarLivrosPorAutor(AutorRequest.newBuilder().setAutor("Autor Inexistente").build());
            while (it.hasNext()) {
                System.out.println("  " + it.next().getTitulo());
            }
            System.out.println("  Stream vazia.");
        } catch (StatusRuntimeException e) {
            mostrarErro(e);
        }
    }

    private static void testeEmprestimos(BibliotecaServiceGrpc.BibliotecaServiceStub stub, List<Long> ids)
            throws InterruptedException {
        titulo("4) Client Streaming — registrarEmprestimos (5 empréstimos)");
        CountDownLatch fim = new CountDownLatch(1);

        StreamObserver<ResumoEmprestimos> respostas = new StreamObserver<ResumoEmprestimos>() {
            @Override
            public void onNext(ResumoEmprestimos r) {
                System.out.printf("  Resumo: registrados=%d | rejeitados=%d | tempo=%d ms%n",
                        r.getTotalRegistrados(), r.getTotalRejeitados(), r.getTempoTotalMs());
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("  Erro: " + Status.fromThrowable(t));
                fim.countDown();
            }

            @Override
            public void onCompleted() {
                fim.countDown();
            }
        };

        StreamObserver<Emprestimo> envio = stub.withDeadlineAfter(10, TimeUnit.SECONDS).registrarEmprestimos(respostas);
        String[] usuarios = {"Ana", "Bruno", "Carla", "Diego", "Elisa"};
        try {
            for (int i = 0; i < usuarios.length; i++) {
                long livroId = ids.get(i % ids.size());
                System.out.printf("  -> enviando empréstimo: usuario=%s, livro_id=%d%n", usuarios[i], livroId);
                envio.onNext(Emprestimo.newBuilder().setUsuario(usuarios[i]).setLivroId(livroId).build());
                Thread.sleep(150);
            }
        } catch (RuntimeException e) {
            envio.onError(e);
            throw e;
        }
        envio.onCompleted();
        fim.await(15, TimeUnit.SECONDS);
    }

    private static void testeChat(BibliotecaServiceGrpc.BibliotecaServiceStub stub) throws InterruptedException {
        titulo("5) Bidirectional Streaming — chatBibliotecario (4 mensagens)");
        CountDownLatch fim = new CountDownLatch(1);

        StreamObserver<SugestaoLivro> respostas = new StreamObserver<SugestaoLivro>() {
            @Override
            public void onNext(SugestaoLivro s) {
                System.out.println("  Bibliotecário: " + s.getMensagem());
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("  Erro: " + Status.fromThrowable(t));
                fim.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("  (chat encerrado pelo servidor)");
                fim.countDown();
            }
        };

        StreamObserver<MensagemUsuario> envio = stub.withDeadlineAfter(10, TimeUnit.SECONDS).chatBibliotecario(respostas);
        String[] mensagens = {
                "Quero aprender Java",
                "Preciso de algo sobre algoritmos",
                "Gosto de romance brasileiro",
                "Tem algo sobre sistemas distribuídos?"
        };
        try {
            for (String m : mensagens) {
                System.out.println("  Usuário: " + m);
                envio.onNext(MensagemUsuario.newBuilder().setUsuario("Gabriel").setTexto(m).build());
                Thread.sleep(300);
            }
        } catch (RuntimeException e) {
            envio.onError(e);
            throw e;
        }
        envio.onCompleted();
        fim.await(15, TimeUnit.SECONDS);
    }

    private static void testeIsbnDuplicado(BibliotecaServiceGrpc.BibliotecaServiceBlockingStub stub) {
        titulo("6) Unary — ISBN duplicado (esperado: ALREADY_EXISTS)");
        try {
            stub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS)
                    .cadastrarLivro(livro("Effective Java (cópia)", "Joshua Bloch", 2018, "978-0134685991"));
            System.out.println("  ERRO: o servidor aceitou um ISBN duplicado!");
        } catch (StatusRuntimeException e) {
            mostrarErro(e);
        }
    }

    private static void testeArgumentoInvalido(BibliotecaServiceGrpc.BibliotecaServiceBlockingStub stub) {
        titulo("Extra) Unary — livro sem título (esperado: INVALID_ARGUMENT)");
        try {
            stub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS)
                    .cadastrarLivro(livro("", "Alguém", 2020, "978-0000000001"));
        } catch (StatusRuntimeException e) {
            mostrarErro(e);
        }
    }

    private static void testeSemToken(String host, int porta) throws InterruptedException {
        titulo("Bônus) Autenticação — chamada sem token (esperado: UNAUTHENTICATED)");
        ManagedChannel semToken = ManagedChannelBuilder.forAddress(host, porta).usePlaintext().build();
        try {
            BibliotecaServiceGrpc.newBlockingStub(semToken)
                    .withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS)
                    .cadastrarLivro(livro("Livro Sem Token", "Fulano", 2020, "978-0000000002"));
        } catch (StatusRuntimeException e) {
            mostrarErro(e);
        } finally {
            semToken.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Livro livro(String titulo, String autor, int ano, String isbn) {
        return Livro.newBuilder().setTitulo(titulo).setAutor(autor).setAno(ano).setIsbn(isbn).build();
    }

    private static void mostrarErro(StatusRuntimeException e) {
        System.out.printf("  Erro gRPC recebido: %s - %s%n",
                e.getStatus().getCode(), e.getStatus().getDescription());
    }

    private static void titulo(String texto) {
        System.out.println("\n=== " + texto + " ===");
    }
}
