/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.http.vertx;


import java.util.List;
import javax.annotation.Nonnull;

import io.teris.rpc.ServiceDispatcher;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;


public interface VertxServiceRouter  {

	@Nonnull
	VertxServiceRouter route(@Nonnull ServiceDispatcher serviceDispatcher);

	@Nonnull
	static Builder builder(@Nonnull Router router) {
		return new VertxServiceRouterImpl.BuilderImpl(router);
	}

	interface Builder {

		@Nonnull
		Builder uriPrefix(@Nonnull String uriPrefix);

		@Nonnull
		Builder preconditioner(@Nonnull Handler<RoutingContext> preconditioner);

		@Nonnull
		Builder preconditioners(@Nonnull List<Handler<RoutingContext>> preconditioners);

		@Nonnull
		VertxServiceRouter build();
	}
}
