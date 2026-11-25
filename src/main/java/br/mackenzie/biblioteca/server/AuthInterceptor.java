package br.mackenzie.biblioteca.server;

import br.mackenzie.biblioteca.model.Seguranca;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class AuthInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String token = headers.get(Seguranca.AUTHORIZATION);
        if (!Seguranca.TOKEN_VALIDO.equals(token)) {
            Log.info("[auth] Token ausente ou inválido -> UNAUTHENTICATED");
            call.close(Status.UNAUTHENTICATED.withDescription("Token ausente ou inválido."), new Metadata());
            return new ServerCall.Listener<ReqT>() {
            };
        }
        return next.startCall(call, headers);
    }
}
