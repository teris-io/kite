/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.rpc.vertx;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

import io.teris.kite.rpc.ServiceExporter;
import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;


class HttpServiceExporterImpl implements HttpServiceExporter {

	private final Router router;

	private final String uriPrefix;

	private final List<Handler<RoutingContext>> preprocessors = new ArrayList<>();

	private final Handler<RoutingContext> bodyHandler;

	private final boolean caseSensitive;

	HttpServiceExporterImpl(Router router, String uriPrefix, Handler<RoutingContext> bodyHandler, List<Handler<RoutingContext>> preprocessors, boolean caseSensitive) {
		this.router = router;
		this.uriPrefix = uriPrefix;
		this.bodyHandler = bodyHandler;
		this.caseSensitive = caseSensitive;
		this.preprocessors.addAll(preprocessors);
	}

	static class ServiceRouterImpl implements ServiceRouter {

		private final Router router;

		private String uriPrefix = null;

		private Handler<RoutingContext> bodyHandler = BodyHandler.create();

		private final List<Handler<RoutingContext>> preprocessors = new ArrayList<>();

		private boolean caseSensitive = false;

		ServiceRouterImpl(Router router) {
			this.router = router;
		}

		@Nonnull
		@Override
		public ServiceRouter uriPrefix(@Nonnull String uriPrefix) {
			this.uriPrefix = uriPrefix.startsWith("/") ? uriPrefix : "/" + uriPrefix;
			return this;
		}

		@Nonnull
		@Override
		public ServiceRouter bodyHandler(@Nonnull Handler<RoutingContext> bodyHandler) {
			this.bodyHandler = bodyHandler;
			return this;
		}

		@Nonnull
		@Override
		public ServiceRouter preprocessor(@Nonnull Handler<RoutingContext> preprocessor) {
			this.preprocessors.add(preprocessor);
			return this;
		}

		@Nonnull
		@Override
		public ServiceRouter caseSensitive() {
			this.caseSensitive = true;
			return this;
		}

		@Nonnull
		@Override
		public HttpServiceExporter export(@Nonnull ServiceExporter serviceExporter) {
			return new HttpServiceExporterImpl(router, uriPrefix, bodyHandler, preprocessors, caseSensitive)
				.export(serviceExporter);
		}
	}

	@Nonnull
	@Override
	public HttpServiceExporter export(@Nonnull ServiceExporter serviceExporter) {
		ExportedServiceHandler dispatchingHandler = new ExportedServiceHandler(uriPrefix, serviceExporter);

		for (String uri: dispatchingHandler.dispatchUris()) {
			Route route;
			if (caseSensitive) {
				route = router.post(uri);
			}
			else {
				route = router.postWithRegex("(?i)" + uri);
			}
			route = route.handler(bodyHandler);
			for (Handler<RoutingContext> preprocessor: preprocessors) {
				route = route.handler(preprocessor);
			}
			route.handler(dispatchingHandler);
		}
		return this;
	}

	@Nonnull
	@Override
	public Router router() {
		return router;
	}
}
