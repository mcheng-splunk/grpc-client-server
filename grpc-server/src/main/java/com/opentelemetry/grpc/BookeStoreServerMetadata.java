package com.opentelemetry.grpc;

import com.opentelemetry.common.OtelSdkConfiguration;
import com.opentelemetry.proto.Book;
import com.opentelemetry.proto.BookSearch;
import com.opentelemetry.proto.BookStoreGrpc;

//import io.grpc.*;
//import io.grpc.stub.StreamObserver;
//import io.grpc.ServerCall.Listener;
import io.grpc.Contexts;
import io.grpc.*;
import io.grpc.ServerCall.Listener;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class BookeStoreServerMetadata {
    private static final Logger logger = Logger.getLogger(BookeStoreServerMetadata.class.getName());


    private static  OpenTelemetry openTelemetry = OtelSdkConfiguration.initOpenTelemetry();

    // Extract the Distributed Context from the gRPC metadata
    private static TextMapGetter<Metadata> getter =
            new TextMapGetter<Metadata>() {
                @Override
                public Iterable<String> keys(Metadata carrier) {
                    return carrier.keys();
                }

                @Override
                public String get(Metadata carrier, String key) {
                    Metadata.Key<String> k = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
                    if (carrier.containsKey(k)) {
                        return carrier.get(k);
                    }
                    return "";
                }
            };


    static Map<String, Book> bookMap = new HashMap<>();
    static {
        bookMap.put("Great Gatsby", Book.newBuilder().setName("Great Gatsby")
                .setAuthor("Scott Fitzgerald")
                .setPrice(300).build());
        bookMap.put("To Kill MockingBird", Book.newBuilder().setName("To Kill MockingBird")
                .setAuthor("Harper Lee")
                .setPrice(400).build());
        bookMap.put("Passage to India", Book.newBuilder().setName("Passage to India")
                .setAuthor("E.M.Forster")
                .setPrice(500).build());
        bookMap.put("The Side of Paradise", Book.newBuilder().setName("The Side of Paradise")
                .setAuthor("Scott Fitzgerald")
                .setPrice(600).build());
        bookMap.put("Go Set a Watchman", Book.newBuilder().setName("Go Set a Watchman")
                .setAuthor("Harper Lee")
                .setPrice(700).build());
    }
    private Server server;
    private static Tracer tracer =
            openTelemetry.getTracer("com.opentelemetry.grpc.server.ReturnBook");
    private static TextMapPropagator textFormat =
            openTelemetry.getPropagators().getTextMapPropagator();
    class BookServerInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> Listener<ReqT>
        interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

            logger.info("Received following metadata: " + headers);
//            return next.startCall(call, headers);
            Context extractedContext = textFormat.extract(Context.current(), headers, getter);
            InetSocketAddress clientInfo =
                    (InetSocketAddress) call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            // Build a span based on the received context
            Span span =
                    tracer
                            .spanBuilder("/ReturnBook")
                            .setParent(extractedContext)
                            .setSpanKind(SpanKind.SERVER)
                            .startSpan();
            logger.info("Extracted context is : " + extractedContext);
            try (Scope innerScope = span.makeCurrent()) {
                span.setAttribute("component", "grpc");
                // Process the gRPC call normally
                return Contexts.interceptCall(io.grpc.Context.current(), call, headers, next);
            } finally {
                span.end();
            }
        }
    }

    private void start() throws IOException {
        int port = 50051;
        server = ServerBuilder.forPort(port).addService(new BookStoreImpl()).intercept(new BookServerInterceptor()).build().start();
        logger.info("Server started, listening on " + port);


        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("Shutting down gRPC server");
                try {
                    server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
            }
        });
    }
    public static void main(String[] args) throws IOException, InterruptedException {
        final BookeStoreServerMetadata greetServer = new BookeStoreServerMetadata();
        greetServer.start();
        greetServer.server.awaitTermination();
    }
    static class BookStoreImpl extends BookStoreGrpc.BookStoreImplBase {
        @Override
        public void first(BookSearch searchQuery, StreamObserver<Book> responseObserver) {
            logger.info("Searching for book with title: " + searchQuery.getName());
            List<String> matchingBookTitles = bookMap.keySet().stream().filter(title ->
                    title.startsWith(searchQuery.getName().trim())).collect(Collectors.toList());

            Book foundBook = null;
            if(matchingBookTitles.size() > 0) {
                foundBook = bookMap.get(matchingBookTitles.get(0));
            }
            responseObserver.onNext(foundBook);
            responseObserver.onCompleted();
        }
    }
}