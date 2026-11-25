package br.mackenzie.biblioteca.server;

import br.mackenzie.biblioteca.model.Acervo;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.util.concurrent.TimeUnit;

public class ServidorBiblioteca {

    public static void main(String[] args) throws Exception {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 50051;

        Server server = ServerBuilder.forPort(porta)
                .addService(new BibliotecaServiceImpl(new Acervo()))
                .intercept(new AuthInterceptor())
                .intercept(new LogInterceptor())
                .build()
                .start();

        Log.info("Servidor da Biblioteca Digital iniciado na porta %d", porta);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.info("Encerrando servidor...");
            try {
                server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                server.shutdownNow();
            }
        }));

        server.awaitTermination();
    }
}
