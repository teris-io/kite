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

	private final List<Handler<RoutingContext>> preconditioners = new ArrayList<>();

	private final boolean caseSensitive;

	HttpServiceExporterImpl(Router router, String uriPrefix, List<Handler<RoutingContext>> preconditioners, boolean caseSensitive) {
		this.router = router;
		this.uriPrefix = uriPrefix;
		this.caseSensitive = caseSensitive;
		this.preconditioners.add(BodyHandler.create());
		this.preconditioners.addAll(preconditioners);
	}

	static class ServiceRouterImpl implements ServiceRouter {

		private final Router router;

		private String uriPrefix = null;

		private final List<Handler<RoutingContext>> preconditioners = new ArrayList<>();

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
		public ServiceRouter caseSensitive() {
			this.caseSensitive = true;
			return this;
		}

		@Nonnull
		@Override
		public HttpServiceExporter export(@Nonnull ServiceExporter serviceExporter) {
			return new HttpServiceExporterImpl(router, uriPrefix, preconditioners, caseSensitive)
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
			for (Handler<RoutingContext> preconditioner: preconditioners) {
				route = route.handler(preconditioner);
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
