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

	VertxServiceRouterImpl(Router router, String uriPrefix, List<Handler<RoutingContext>> preconditioners) {
		this.router = router;
		this.uriPrefix = uriPrefix;
		this.preconditioners.add(BodyHandler.create());
		this.preconditioners.addAll(preconditioners);
	}

	static class BuilderImpl implements Builder {

		private final Router router;

		private String uriPrefix = null;

		private final List<Handler<RoutingContext>> preconditioners = new ArrayList<>();


		BuilderImpl(Router router) {
			this.router = router;
		}

		@Nonnull
		@Override
		public Builder uriPrefix(@Nonnull String uriPrefix) {
			this.uriPrefix = uriPrefix;
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
		public VertxServiceRouter build() {
			return new VertxServiceRouterImpl(router, uriPrefix, preconditioners);
		}
	}

	@Nonnull
	@Override
	public VertxServiceRouter route(@Nonnull ServiceDispatcher serviceDispatcher) {
		VertxServiceHandler businessHandler = new VertxServiceHandler(uriPrefix, serviceDispatcher);

		for (String uri: businessHandler.dispatchUris()) {
			Route x = router.post(uri);
			for (Handler<RoutingContext> preconditioner: preconditioners) {
				x = x.handler(preconditioner);
			}
			x.handler(businessHandler);
		}
		return this;
	}
}
