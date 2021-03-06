/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.rpc.vertx;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.teris.kite.Context;
import io.teris.kite.rpc.AuthenticationException;
import io.teris.kite.rpc.NotFoundException;
import io.teris.kite.rpc.TechnicalException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;


class HttpServiceInvokerImpl extends RoutingBase implements HttpServiceInvoker {

	private static final Logger log = LoggerFactory.getLogger(HttpServiceInvoker.class);

	private final HttpClient httpClient;

	private final Map<String, String> cookieStore = new ConcurrentHashMap<>();

	HttpServiceInvokerImpl(HttpClient httpClient, String uriPrefix) {
		super(uriPrefix);
		this.httpClient = httpClient;
	}

	static class BuilderImpl implements HttpServiceInvoker.Builder {

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
		public HttpServiceInvoker build() {
			return new HttpServiceInvokerImpl(httpClient, uriPrefix);
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
			if (httpResponse.statusCode() == 403) {
				promise.completeExceptionally(new AuthenticationException(httpResponse.statusMessage()));
				log.error("status=CLIENT-ERROR AUTH, corrId={}, target={}, httpcode=403, message={}", corrId, uri,
					httpResponse.statusMessage());
				return;
			}
			else if (httpResponse.statusCode() == 404) {
				promise.completeExceptionally(new NotFoundException(httpResponse.statusMessage()));
				log.error("status=CLIENT-ERROR NOT-FOUND, corrId={}, target={}, httpcode=404, message={}", corrId, uri,
					httpResponse.statusMessage());
				return;
			}
			else if (httpResponse.statusCode() >= 400) {
				promise.completeExceptionally(new TechnicalException(httpResponse.statusMessage()));
				log.error("status=CLIENT-ERROR, corrId={}, target={}, httpcode={}, message={}", corrId, uri,
					httpResponse.statusCode(), httpResponse.statusMessage());
				return;
			}
			log.trace("status=CLIENT-RECEIVING, corrId={}, target={}", corrId, uri);
			Context incomingContext = new Context(context);
			for (String headerKey: httpResponse.headers().names()) {
				incomingContext.put(headerKey, httpResponse.getHeader(headerKey));
			}

			for (String cookieText : httpResponse.cookies()) {
				String cookieName = ClientCookieDecoder.STRICT.decode(cookieText).name();
				cookieStore.put(cookieName, cookieText);
			}

			httpResponse.bodyHandler(buffer -> {
				promise.complete(new SimpleEntry<>(incomingContext, buffer != null ? buffer.getBytes() : null));
				log.debug("status=CLIENT-COMPLETED, corrId={}, target={}", corrId, uri);
			});
		});
		for (Entry<String, String> entry : context.entrySet()) {
			httpRequest.putHeader(entry.getKey(), entry.getValue());
		}
		httpRequest.putHeader(HttpHeaders.COOKIE.toString(), cookieStore.values());

		httpRequest.exceptionHandler(t -> {
			promise.completeExceptionally(new TechnicalException("request exception", t));
			log.error(String.format("status=CLIENT-ERROR, corrId=%s, target=%s", corrId, uri), t);
		});

		if (outgoing != null) {
			httpRequest.setChunked(true).end(Buffer.buffer(outgoing));
		}
		else {
			httpRequest.end();
		}
		return promise;
	}
}
