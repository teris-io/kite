/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.rpc.vertx;

import java.util.Collections;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.teris.kite.Context;
import io.teris.kite.rpc.AuthenticationException;
import io.teris.kite.rpc.ServiceExporter;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;


class ExportedServiceHandler extends RoutingBase implements Handler<RoutingContext> {

	private static final Logger log = LoggerFactory.getLogger(ExportedServiceHandler.class);

	private final ServiceExporter serviceExporter;

	ExportedServiceHandler(String uriPrefix, ServiceExporter serviceExporter) {
		super(uriPrefix);
		this.serviceExporter = serviceExporter;
	}

	Set<String> dispatchUris() {
		return Collections.unmodifiableSet(serviceExporter.routes()
			.stream()
			.map(this::routeToUri)
			.collect(Collectors.toSet()));
	}

	@Override
	public void handle(RoutingContext httpContext) {
		String uri = httpContext.request().uri();
		String route = uriToRoute(uri);

		Context incomingContext = new Context();
		for (String headerKey : httpContext.request().headers().names()) {
			incomingContext.put(headerKey, httpContext.request().getHeader(headerKey));
		}
		for (Entry<String, Object> entry : httpContext.data().entrySet()) {
			incomingContext.put(entry.getKey(), String.valueOf(entry.getValue()));
		}

		byte[] incomingData = httpContext.getBody() != null ? httpContext.getBody().getBytes() : null;
		String corrId = incomingContext.get(Context.X_REQUEST_ID_KEY);

		log.trace("status=SERVER-EXECUTING, corrId={}, target={}", corrId, uri);
		serviceExporter
			.call(route, incomingContext, incomingData)
			.handleAsync((entry, t) -> {
				HttpServerResponse httpResponse = httpContext.response();
				// it is expected that all exceptions are serialized as normal response (unless exactly that failed)
				if (t instanceof Exception || entry == null) {
					t = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
					String message = t != null ? t.getMessage() : null;
					if (message == null || message.trim().length() == 0) {
						message = "Server error: null response";
					}
					int statusCode = t instanceof AuthenticationException ? 403 : 500;
					log.trace("status=SERVER-ERROR, corrId={}, target={}, message={}", corrId, uri, message);
					httpResponse
						.setStatusCode(statusCode)
						.setStatusMessage(message)
						.end();
					return null;
				}
				Context outgoingContext = entry.getKey() != null ? entry.getKey() : incomingContext;
				for (Entry<String, String> headerEntry : outgoingContext.entrySet()) {
					httpResponse.putHeader(headerEntry.getKey(), headerEntry.getValue());
				}
				httpResponse.setChunked(true).setStatusCode(200);
				httpResponse.endHandler((v) -> log.debug("status=SERVER-COMPLETED, corrId={}, target={}", corrId, uri));

				if (entry.getValue() != null) {
					httpResponse.end(Buffer.buffer(entry.getValue()));
				}
				else {
					httpResponse.end();
				}
				log.trace("status=SERVER-RESPONDING, corrId={}, target={}", corrId, uri);
				return null;
			});
	}
}
