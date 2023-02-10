package com.opentelemetry.grpc;

import com.opentelemetry.common.OtelSdkConfiguration;
import com.opentelemetry.proto.Book;
import com.opentelemetry.proto.BookSearch;
import com.opentelemetry.proto.BookStoreGrpc;
//import io.grpc.*;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

public class BookStoreClientUnaryBlockingMetadata {
    private static final Logger logger = Logger.getLogger(BookStoreClientUnaryBlockingMetadata.class.getName());
    private final BookStoreGrpc.BookStoreBlockingStub blockingStub;

    public BookStoreClientUnaryBlockingMetadata(Channel channel) {
        blockingStub = BookStoreGrpc.newBlockingStub(channel);
    }

    private static final OpenTelemetry openTelemetry = OtelSdkConfiguration.initOpenTelemetry();

    // OTel Tracing API
    private static Tracer tracer =
            openTelemetry.getTracer("com.opentelemetry.client.GetBook");
    // Share context via text headers
    private static TextMapPropagator textFormat =
            openTelemetry.getPropagators().getTextMapPropagator();
    // Inject context into the gRPC request metadata
    private static TextMapSetter<Metadata> setter =
            (carrier, key, value) ->
                    carrier.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);

    static class BookClientInterceptor implements ClientInterceptor {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT>
        interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next
        ) {
            return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
                        @Override
                        public void start(Listener<RespT> responseListener, Metadata headers) {
                            logger.info("Added metadata");
//                            headers.put(Metadata.Key.of("HOSTNAME", ASCII_STRING_MARSHALLER), "MY_HOST");
                            textFormat.inject(Context.current(), headers, setter);
                            super.start(responseListener, headers);
                        }
                    };
        }
    }
    public void getBook(String bookName) {
        logger.info("Querying for book with title: " + bookName);
        BookSearch request = BookSearch.newBuilder().setName(bookName).build();

        Book response;
        CallOptions.Key<String> metaDataKey = CallOptions.Key.create("my_key");
        try {
            response = blockingStub.withOption(metaDataKey, "bar").first(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Got following book from server: " + response);
    }
    public static void main(String[] args) throws Exception {
        String bookName = args[0];
        String serverAddress = "localhost:50051";

        ManagedChannel channel =  ManagedChannelBuilder.forTarget(serverAddress).usePlaintext().intercept(new BookClientInterceptor()).build();


        Span span = tracer.spanBuilder("/GetBook").setSpanKind(SpanKind.CLIENT).startSpan();
        span.setAttribute("component", "grpc");
        span.setAttribute("marvel", "thor");

        // Set the context with the current span
        try (Scope scope = span.makeCurrent()) {
            logger.log(Level.INFO, "Context is " + Context.current().toString());
            BookStoreClientUnaryBlockingMetadata client = new BookStoreClientUnaryBlockingMetadata(channel);
            client.getBook(bookName);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            span.setStatus(StatusCode.ERROR, "gRPC status: " + e.getStatus());
        }finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            span.end();
        }
    }
}