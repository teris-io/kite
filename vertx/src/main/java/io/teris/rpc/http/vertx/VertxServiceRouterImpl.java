/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.http.vertx;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

import io.teris.rpc.ServiceDispatcher;
import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;


class VertxServiceRouterImpl implements VertxServiceRouter {

	private final Router router;

	private final String uriPrefix;

	private final List<Handler<RoutingContext>> preconditioners = new ArrayList<>();

	private final boolean caseSensitive;

	VertxServiceRouterImpl(Router router, String uriPrefix, List<Handler<RoutingContext>> preconditioners, boolean caseSensitive) {
		this.router = router;
		this.uriPrefix = uriPrefix;
		this.caseSensitive = caseSensitive;
		this.preconditioners.add(BodyHandler.create());
		this.preconditioners.addAll(preconditioners);
	}

	static class BuilderImpl implements Builder {

		private final Router router;

		private String uriPrefix = null;

		private final List<Handler<RoutingContext>> preconditioners = new ArrayList<>();

		private boolean caseSensitive = false;


		BuilderImpl(Router router) {
			this.router = router;
		}

		@Nonnull
		@Override
		public Builder uriPrefix(@Nonnull String uriPrefix) {
			this.uriPrefix = uriPrefix.startsWith("/") ? uriPrefix : "/" + uriPrefix;
			return this;
		}

		@Nonnull
		@Override
		public Builder preconditioner(@Nonnull Handler<RoutingContext> preconditioner) {
			this.preconditioners.add(preconditioner);
			return this;
		}

		@Nonnull
		@Override
		public Builder preconditioners(@Nonnull List<Handler<RoutingContext>> preconditioners) {
			this.preconditioners.addAll(preconditioners);
			return this;
		}

		@Nonnull
		@Override
		public Builder caseSensitive() {
			this.caseSensitive = true;
			return this;
		}

		@Nonnull
		@Override
		public VertxServiceRouter build() {
			return new VertxServiceRouterImpl(router, uriPrefix, preconditioners, caseSensitive);
		}
	}

	@Nonnull
	@Override
	public VertxServiceRouter route(@Nonnull ServiceDispatcher serviceDispatcher) {
		VertxDispatchingHandler dispatchingHandler = new VertxDispatchingHandler(uriPrefix, serviceDispatcher);

		for (String uri: dispatchingHandler.dispatchUris()) {
			Route route;
			if (caseSensitive) {
				route = router.post(uri);
			}
			else {
				route = router.postWithRegex("(?i)" + uri);
			}
			for (Handler<RoutingContext> preconditioner: preconditioners) {
				route = route.handler(preconditioner);
			}
			route.handler(dispatchingHandler);
		}
		return this;
	}
}
