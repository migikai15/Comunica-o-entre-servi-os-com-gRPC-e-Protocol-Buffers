package br.mackenzie.biblioteca.model;

import io.grpc.Metadata;

public final class Seguranca {

    public static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    public static final String TOKEN_VALIDO = "Bearer biblioteca-secret-123";

    private Seguranca() {
    }
}
