/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.http.vertx;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.teris.rpc.Context;
import io.teris.rpc.TechnicalException;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;


class VertxServiceInvokerImpl extends RoutingBase implements VertxServiceInvoker {

	private final HttpClient httpClient;

	VertxServiceInvokerImpl(Vertx vertx, HttpClientOptions httpClientOptions, String uriPrefix) {
		super(uriPrefix);
		this.httpClient = vertx.createHttpClient(httpClientOptions);
	}

	static class BuilderImpl implements VertxServiceInvoker.Builder {

		private final Vertx vertx;

		private HttpClientOptions httpClientOptions;

		private String uriPrefix;

		BuilderImpl(Vertx vertx) {
			this.vertx = vertx;
		}

		@Nonnull
		@Override
		public Builder httpClientOptions(@Nonnull HttpClientOptions httpClientOptions) {
			this.httpClientOptions = httpClientOptions;
			return this;
		}

		@Nonnull
		@Override
		public Builder uriPrefix(@Nonnull String uriPrefix) {
			this.uriPrefix = uriPrefix;
			return this;
		}

		@Nonnull
		@Override
		public VertxServiceInvoker build() {
			Objects.requireNonNull(httpClientOptions);
			Objects.requireNonNull(httpClientOptions.getDefaultHost());
			return new VertxServiceInvokerImpl(vertx, httpClientOptions, uriPrefix);
		}
	}

	@Nonnull
	@Override
	public CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context outgoingContext, @Nullable byte[] outgoing) {
		CompletableFuture<Entry<Context, byte[]>> promise = new CompletableFuture<>();
		HttpClientRequest httpRequest = httpClient.post(routeToUri(route), httpResponse -> {
			if (httpResponse.statusCode() < 400) {
				Context incomingContext = new Context(outgoingContext);
				for (String headerKey: httpResponse.headers().names()) {
					incomingContext.put(headerKey, httpResponse.getHeader(headerKey));
				}
				httpResponse.bodyHandler(buffer -> promise.complete(new SimpleEntry<>(incomingContext, buffer.getBytes())));
			}
			else {
				promise.completeExceptionally(new TechnicalException(httpResponse.statusMessage()));
			}
		});
		for (Entry<String, String> entry : outgoingContext.entrySet()) {
			httpRequest.putHeader(entry.getKey(), entry.getValue());
		}
		if (outgoing != null) {
			httpRequest.setChunked(true).end(Buffer.buffer(outgoing));
		}
		else {
			httpRequest.end();
		}
		return promise;
	}
}
