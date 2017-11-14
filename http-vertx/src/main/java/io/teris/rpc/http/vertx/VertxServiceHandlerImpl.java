/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.http.vertx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import io.teris.rpc.ServiceDispatcher;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;


class VertxServiceHandlerImpl implements VertxServiceHandler {

	private final Map<String, Entry<String, ServiceDispatcher>> endpoints = new HashMap<>();

	static class BuilderImpl implements Builder {

		private final Router router;

		private final Set<HttpMethod> methods = new HashSet<>();

		private final List<Handler<RoutingContext>> preconditioners = new ArrayList<>();

		private final List<ServiceDispatcher> dispatchers = new ArrayList<>();

		private String uriPrefix = null;


		BuilderImpl(Router router) {
			this.router = router;
		}

		@Override
		public Builder uriPrefix(String uriPrefix) {
			this.uriPrefix = uriPrefix;
			return this;
		}

		@Override
		public Builder method(HttpMethod method) {
			this.methods.add(method);
			return this;
		}

		@Override
		public Builder preconditioner(Handler<RoutingContext> preconditioner) {
			this.preconditioners.add(preconditioner);
			return this;
		}

		@Override
		public Builder bind(ServiceDispatcher serviceDispatcher) {
			this.dispatchers.add(serviceDispatcher);
			return null;
		}

		@Override
		public VertxServiceHandler build() {
			if (dispatchers.isEmpty()) {
				throw new IllegalStateException("FIXME");
			}
			if (methods.isEmpty()) {
				methods.add(HttpMethod.POST);
			}

			return new VertxServiceHandlerImpl(router, methods, uriPrefix, preconditioners, dispatchers);
		}
	}

	VertxServiceHandlerImpl(Router router, Set<HttpMethod> methods, String uriPrefix, List<Handler<RoutingContext>> preconditioners, List<ServiceDispatcher> dispatchers) {

		// FIXME populate endpoints and route



	}


	@Override
	public void handle(RoutingContext event) {

	}
}
