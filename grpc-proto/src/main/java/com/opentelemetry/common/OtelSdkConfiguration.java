package com.opentelemetry.common;/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.concurrent.TimeUnit;

/**
 * All SDK management takes place here, away from the instrumentation code, which should only access
 * the OpenTelemetry APIs.
 */
public class OtelSdkConfiguration {

  /**
   * Initializes the OpenTelemetry SDK with a logging span exporter and the W3C Trace Context
   * propagator.
   *
   * @return A ready-to-use {@link OpenTelemetry} instance.
   */
  public static OpenTelemetry initOpenTelemetry() {

      // Export traces to Jaeger over OTLP (To be used if using OTEL collector)
      OtlpGrpcSpanExporter jaegerOtlpExporter =
              OtlpGrpcSpanExporter.builder()
                      .setEndpoint("http://localhost:4317")
                      .setTimeout(30, TimeUnit.SECONDS)
                      .build();

      JaegerGrpcSpanExporter jaegerExporter =
            JaegerGrpcSpanExporter.builder()
                    .setEndpoint("http://localhost:14250")
                    .build();

      Resource serviceNameResource =
              Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "otel-jaeger-grpc"));


      SdkTracerProvider sdkTracerProvider =
        SdkTracerProvider.builder()
            // The customSpanProcessor allows us to inject and extract baggage information into span attributes
//            .addSpanProcessor(CustomSpanProcessor.create(jaegerExporter))
//            .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
            .addSpanProcessor(SimpleSpanProcessor.create(jaegerExporter))
            .setResource(Resource.getDefault().merge(serviceNameResource))
            .build();


    // sdk propagator to propagate both the context and baggage
    OpenTelemetrySdk sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(sdkTracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
//            .setPropagators(ContextPropagators.create(W3CBaggagePropagator.getInstance()))
            .build();

    Runtime.getRuntime().addShutdownHook(new Thread(sdkTracerProvider::close));
    return sdk;
  }
}
