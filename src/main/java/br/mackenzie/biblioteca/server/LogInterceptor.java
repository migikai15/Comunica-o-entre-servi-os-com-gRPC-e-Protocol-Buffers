package br.mackenzie.biblioteca.server;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class LogInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String metodo = call.getMethodDescriptor().getFullMethodName();
        String tipo = call.getMethodDescriptor().getType().name();
        long inicio = System.nanoTime();
        Log.info("[interceptor] Chamada recebida: %s (%s)", metodo, tipo);

        ServerCall<ReqT, RespT> chamadaComLog =
                new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                    @Override
                    public void close(Status status, Metadata trailers) {
                        long ms = (System.nanoTime() - inicio) / 1_000_000;
                        Log.info("[interceptor] Chamada finalizada: %s -> %s em %d ms", metodo, status.getCode(), ms);
                        super.close(status, trailers);
                    }
                };

        return next.startCall(chamadaComLog, headers);
    }
}
