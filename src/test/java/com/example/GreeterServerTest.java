package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.greeter.GreeterGrpcClient;
import com.example.greeter.HelloReply;
import com.example.greeter.HelloRequest;
import com.google.protobuf.InvalidProtocolBufferException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GreeterServerTest {
    private static final HelloRequest REQUEST = HelloRequest.newBuilder().setName("World").build();
    private static final String EXPECTED_MESSAGE = "Hello, World";

    private static Vertx vertx;
    private static SocketAddress serverAddress;
    private static HttpClient http1Client;
    private static HttpClient http2Client;

    @BeforeAll
    static void startServer() {
        vertx = Vertx.vertx();
        int port = GreeterServer.start(vertx, 0).await().actualPort();
        serverAddress = SocketAddress.inetSocketAddress(port, "localhost");
        http1Client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1));
        http2Client = vertx.createHttpClient(new HttpClientOptions()
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setHttp2ClearTextUpgrade(false));
    }

    @AfterAll
    static void stopVertx() {
        vertx.close().await();
    }

    @Test
    void grpcPostRequestSucceeds() {
        assertEquals(EXPECTED_MESSAGE, sayHelloOverGrpc(HttpMethod.POST).await().getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "PATCH", "DELETE"})
    void grpcNonPostRequestIsRejected(String method) {
        Future<HelloReply> reply = sayHelloOverGrpc(HttpMethod.valueOf(method));
        assertThrows(Exception.class, reply::await, "server replied to gRPC " + method + " request");
    }

    @Test
    void grpcWebPostRequestSucceeds() {
        assertEquals(EXPECTED_MESSAGE, sayHelloOverGrpcWeb(HttpMethod.POST).await().getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "PATCH", "DELETE"})
    void grpcWebNonPostRequestIsRejected(String method) {
        Future<HelloReply> reply = sayHelloOverGrpcWeb(HttpMethod.valueOf(method));
        assertThrows(Exception.class, reply::await, "server replied to gRPC-Web " + method + " request");
    }

    private static Future<HelloReply> sayHelloOverGrpc(HttpMethod method) {
        GrpcClient grpcClient = GrpcClient.client(vertx, new MethodOverridingHttpClient(http2Client, method));
        return GreeterGrpcClient.create(grpcClient, serverAddress).sayHello(REQUEST);
    }

    private static Future<HelloReply> sayHelloOverGrpcWeb(HttpMethod method) {
        RequestOptions options = new RequestOptions()
                .setMethod(method)
                .setServer(serverAddress)
                .setURI("/greeter.Greeter/SayHello")
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc-web+proto")
                .putHeader("x-grpc-web", "1");
        Buffer body = Buffer.buffer()
                .appendByte((byte) 0)
                .appendInt(REQUEST.getSerializedSize())
                .appendBytes(REQUEST.toByteArray());
        return http1Client.request(options)
                .compose(request -> request.send(body))
                .compose(HttpClientResponse::body)
                .compose(GreeterServerTest::decodeGrpcWebReply);
    }

    private static Future<HelloReply> decodeGrpcWebReply(Buffer body) {
        if (body.length() < 5 || body.getByte(0) != 0) {
            return Future.failedFuture("response contains no reply message");
        }
        try {
            return Future.succeededFuture(HelloReply.parseFrom(body.getBytes(5, 5 + body.getInt(1))));
        } catch (InvalidProtocolBufferException e) {
            return Future.failedFuture(e);
        }
    }

    private record MethodOverridingHttpClient(HttpClient delegate, HttpMethod method) implements HttpClient {
        @Override
        public Future<HttpClientRequest> request(RequestOptions options) {
            return delegate.request(new RequestOptions(options).setMethod(method));
        }

        @Override
        public Future<Void> shutdown(Duration timeout) {
            return delegate.shutdown(timeout);
        }
    }
}
