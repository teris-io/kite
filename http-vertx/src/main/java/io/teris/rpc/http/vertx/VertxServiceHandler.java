/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.http.vertx;

import java.util.Collections;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import io.teris.rpc.Context;
import io.teris.rpc.ServiceDispatcher;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;


class VertxServiceHandler extends RoutingBase implements Handler<RoutingContext> {

	private final ServiceDispatcher serviceDispatcher;

	VertxServiceHandler(String uriPrefix, ServiceDispatcher serviceDispatcher) {
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
		String route = uriToRoute(httpContext.request().uri());
		Context incomingContext = new Context();
		for (String headerKey : httpContext.request().headers().names()) {
			incomingContext.put(headerKey, httpContext.request().getHeader(headerKey));
		}
		byte[] incomingData = httpContext.getBody().getBytes();

		serviceDispatcher
			.call(route, incomingContext, incomingData)
			.handleAsync((entry, t) -> {
				HttpServerResponse httpResponse = httpContext.response();
				// it is expected that all exceptions are serialized as normal response (unless exactly that failed)
				if (t instanceof Exception || entry == null) {
					httpResponse
						.setStatusCode(500)
						.setStatusMessage(t != null ? t.getMessage() : "Server error: null response in the service future")
						.end();
					return null;
				}
				Context outgoingContext = entry.getKey() != null ? entry.getKey() : incomingContext;
				for (Entry<String, String> headerEntry : outgoingContext.entrySet()) {
					httpResponse.putHeader(headerEntry.getKey(), headerEntry.getValue());
				}
				httpResponse.setChunked(true).setStatusCode(200);
				if (entry.getValue() != null) {
					httpResponse.end(Buffer.buffer(entry.getValue()));
				}
				else {
					httpResponse.end();
				}
				return null;
			});
	}
}
