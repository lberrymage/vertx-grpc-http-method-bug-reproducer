package com.example;

import com.example.greeter.GreeterGrpcService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.grpc.server.GrpcServer;

public final class GreeterServer {
    private GreeterServer() {
    }

    public static Future<HttpServer> start(Vertx vertx, int port) {
        GrpcServer grpcServer = GrpcServer.server(vertx)
                .addService(GreeterGrpcService.of(new GreeterServiceImpl()));
        return vertx.createHttpServer()
                .requestHandler(grpcServer)
                .listen(port);
    }

    public static void main(String[] args) {
        HttpServer server = start(Vertx.vertx(), 50051).await();
        System.out.println("Greeter server listening on port " + server.actualPort());
    }
}
