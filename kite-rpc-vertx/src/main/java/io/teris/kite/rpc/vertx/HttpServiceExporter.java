/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.rpc.vertx;


import javax.annotation.Nonnull;

import io.teris.kite.rpc.ServiceExporter;
import io.teris.kite.rpc.vertx.HttpServiceExporterImpl.ServiceRouterImpl;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;


/**
 * Provides the mechanism to register HTTP endpoints and relay the incoming requests to
 * matching service dispatchers, for which endpoints are registered.
 */
public interface HttpServiceExporter {

	/**
	 * Registers HTTP endpoints for every service method bound to the dispatcher using
	 * all the preconditions of the router.
	 */
	@Nonnull
	HttpServiceExporter export(@Nonnull ServiceExporter serviceExporter);

	@Nonnull
	Router router();

	@Nonnull
	static ServiceRouter router(@Nonnull Router router) {
		return new ServiceRouterImpl(router);
	}

	@Nonnull
	static ServiceRouter router(@Nonnull Vertx vertx) {
		return router(Router.router(vertx));
	}

	interface ServiceRouter {

		/**
		 * Adds a URI prefix to all routes generated automatically from @Service annotations.
		 * The prefix may contain slashes, e.g. `api`, `/api` and `/api/v2` are all accepted values.
		 */
		@Nonnull
		ServiceRouter uriPrefix(@Nonnull String uriPrefix);

		/**
		 * Defines a custom body handler, which is added as the very first handler.
		 */
		@Nonnull
		ServiceRouter bodyHandler(@Nonnull Handler<RoutingContext> bodyHandler);

		/**
		 * Adds an ordered routing context pre-processing handler after the body handler,
		 * but before registering the business logic handler.
		 */
		@Nonnull
		ServiceRouter preprocessor(@Nonnull Handler<RoutingContext> preprocessor);

		/**
		 * Should URIs be case sensitive (the library provides routes in lower case) and
		 * match exactly (default: false). This leads to faster route matching due to lack of
		 * regex operations at the cost of flexibility.
		 */
		@Nonnull
		ServiceRouter caseSensitive();

		/**
		 * Registers HTTP endpoints for every service method bound to the dispatcher using
		 * all the preconditions of the router.
		 */
		@Nonnull
		HttpServiceExporter export(@Nonnull ServiceExporter serviceExporter);
	}
}
