package com.example;

import com.example.greeter.GreeterService;
import com.example.greeter.HelloReply;
import com.example.greeter.HelloRequest;
import io.vertx.core.Future;

public class GreeterServiceImpl extends GreeterService {
    @Override
    public Future<HelloReply> sayHello(HelloRequest request) {
        HelloReply reply = HelloReply.newBuilder()
                .setMessage("Hello, " + request.getName())
                .build();
        return Future.succeededFuture(reply);
    }
}
