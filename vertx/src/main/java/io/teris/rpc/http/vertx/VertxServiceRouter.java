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


/**
 * Provides the mechanism to register HTTP endpoints and relay the incoming requests to
 * matching service dispatchers, for which endpoints are registered.
 */
public interface VertxServiceRouter  {

	/**
	 * Registers HTTP endpoints for every service method bound to the dispatcher using
	 * all the preconditions of the router.
	 */
	@Nonnull
	VertxServiceRouter route(@Nonnull ServiceDispatcher serviceDispatcher);

	@Nonnull
	static Builder builder(@Nonnull Router router) {
		return new VertxServiceRouterImpl.BuilderImpl(router);
	}

	interface Builder {

		/**
		 * Adds a URI prefix to all routes generated automatically from @Service annotations.
		 * The prefix may contain slashes, e.g. `api`, `/api` and `/api/v2` are all accepted values.
		 */
		@Nonnull
		Builder uriPrefix(@Nonnull String uriPrefix);

		/**
		 * Registers an HTTP routing context middleware to be executed for any matching endpoint
		 * before the actual bound service. Multiple middleware handlers are executed in order
		 * of registration. Handlers are expected to either complete the request with an error
		 * (e.g. authentication) or call `next` to pass over to the next handler, and, eventually
		 * to the service dispatcher.
		 */
		@Nonnull
		Builder preconditioner(@Nonnull Handler<RoutingContext> preconditioner);

		@Nonnull
		Builder preconditioners(@Nonnull List<Handler<RoutingContext>> preconditioners);

		/**
		 * Should URIs be case sensitive (the library provides routes in lower case) and
		 * match exactly (default: false). This leads to faster route matching due to lack of
		 * regex operations at the cost of flexibility.
		 */
		@Nonnull
		Builder caseSensitive();

		/**
		 * Builds a router that can take Service Dispatchers and register corresponding
		 * endpoints.
		 */
		@Nonnull
		VertxServiceRouter build();
	}
}
