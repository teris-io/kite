/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.http.vertx;

import java.util.Collections;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.teris.rpc.Context;
import io.teris.rpc.ServiceDispatcher;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;


class VertxDispatchingHandler extends RoutingBase implements Handler<RoutingContext> {

	private static final Logger log = LoggerFactory.getLogger(VertxDispatchingHandler.class);

	private final ServiceDispatcher serviceDispatcher;

	VertxDispatchingHandler(String uriPrefix, ServiceDispatcher serviceDispatcher) {
		super(uriPrefix);
		this.serviceDispatcher = serviceDispatcher;
	}

	Set<String> dispatchUris() {
		return Collections.unmodifiableSet(serviceDispatcher.dispatchRoutes()
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

		byte[] incomingData = httpContext.getBody() != null ? httpContext.getBody().getBytes() : null;
		String corrId = incomingContext.get(Context.X_REQUEST_ID_KEY);

		log.trace("status=SERVER-EXECUTING, corrId={}, target={}", corrId, uri);
		serviceDispatcher
			.call(route, incomingContext, incomingData)
			.handleAsync((entry, t) -> {
				HttpServerResponse httpResponse = httpContext.response();
				// it is expected that all exceptions are serialized as normal response (unless exactly that failed)
				if (t instanceof Exception || entry == null) {
					String message = t != null ? t.getMessage() : "Server error: null response in the service future";
					log.trace("status=SERVER-ERROR, corrId={}, target={}, message={}", corrId, uri, message);
					httpResponse
						.setStatusCode(500)
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
