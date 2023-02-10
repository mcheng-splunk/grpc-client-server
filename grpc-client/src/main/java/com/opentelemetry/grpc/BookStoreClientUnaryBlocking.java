package com.opentelemetry.grpc;

import com.opentelemetry.common.OtelSdkConfiguration;
import com.opentelemetry.proto.Book;
import com.opentelemetry.proto.BookSearch;
import com.opentelemetry.proto.BookStoreGrpc;
import io.grpc.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BookStoreClientUnaryBlocking {
    private static final Logger logger = Logger.getLogger(BookStoreClientUnaryBlocking.class.getName());
    private final BookStoreGrpc.BookStoreBlockingStub blockingStub;
    public BookStoreClientUnaryBlocking(Channel channel) {
        blockingStub = BookStoreGrpc.newBlockingStub(channel);
    }

    public void getBook(String bookName) {
        logger.info("Querying for book with title: " + bookName);
        BookSearch request = BookSearch.newBuilder().setName(bookName).build();

        Book response;
        try {
            response = blockingStub.first(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Got following book from server: " + response);
    }
    public static void main(String[] args) throws Exception {
        String bookName = args[0];
        String serverAddress = "localhost:50051";

        ManagedChannel channel = ManagedChannelBuilder.forTarget(serverAddress)
                .usePlaintext()
                .build();

        try {
            BookStoreClientUnaryBlocking client = new BookStoreClientUnaryBlocking(channel);
            client.getBook(bookName);
        } finally {
            channel.shutdownNow().awaitTermination(5,
                    TimeUnit.SECONDS);
        }
    }
}