/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.http.vertx;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.teris.rpc.Context;
import io.teris.rpc.InvocationException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;


class VertxServiceInvokerImpl extends RoutingBase implements VertxServiceInvoker {

	private static final Logger log = LoggerFactory.getLogger(VertxServiceInvoker.class);

	private final HttpClient httpClient;

	VertxServiceInvokerImpl(HttpClient httpClient, String uriPrefix) {
		super(uriPrefix);
		this.httpClient = httpClient;
	}

	static class BuilderImpl implements VertxServiceInvoker.Builder {

		private final HttpClient httpClient;

		private String uriPrefix = null;

		BuilderImpl(HttpClient httpClient) {
			this.httpClient = httpClient;
		}

		@Nonnull
		@Override
		public Builder uriPrefix(@Nonnull String uriPrefix) {
			this.uriPrefix = uriPrefix.startsWith("/") ? uriPrefix : "/" + uriPrefix;
			return this;
		}

		@Nonnull
		@Override
		public VertxServiceInvoker build() {
			return new VertxServiceInvokerImpl(httpClient, uriPrefix);
		}
	}

	@Nonnull
	@Override
	public CompletableFuture<Void> close() {
		return CompletableFuture.runAsync(httpClient::close);
	}

	@Nonnull
	@Override
	public CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] outgoing) {
		String uri = routeToUri(route);
		String corrId = context.get(Context.X_REQUEST_ID_KEY);
		log.trace("status=CLIENT-SENDING, corrId={}, target={}", corrId, uri);
		CompletableFuture<Entry<Context, byte[]>> promise = new CompletableFuture<>();
		HttpClientRequest httpRequest = httpClient.post(uri, httpResponse -> {
			if (httpResponse.statusCode() >= 400) {
				promise.completeExceptionally(new InvocationException(httpResponse.statusMessage()));
				log.error("status=CLIENT-ERROR, corrId={}, target={}, httpcode={}, message={}", corrId, uri,
					Integer.valueOf(httpResponse.statusCode()), httpResponse.statusMessage());
				return;
			}
			log.trace("status=CLIENT-RECEIVING, corrId={}, target={}", corrId, uri);
			Context incomingContext = new Context(context);
			for (String headerKey: httpResponse.headers().names()) {
				incomingContext.put(headerKey, httpResponse.getHeader(headerKey));
			}
			httpResponse.bodyHandler(buffer -> {
				promise.complete(new SimpleEntry<>(incomingContext, buffer != null ? buffer.getBytes() : null));
				log.debug("status=CLIENT-COMPLETED, corrId={}, target={}", corrId, uri);
			});
		});
		for (Entry<String, String> entry : context.entrySet()) {
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
