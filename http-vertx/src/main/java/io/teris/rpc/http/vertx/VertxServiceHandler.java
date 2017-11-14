/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.http.vertx;


import io.teris.rpc.ServiceDispatcher;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;


// FIXME need a router instead, with two-steps: configuration, then binding (routing)

public interface VertxServiceHandler extends Handler<RoutingContext> {

	static Builder builder(Router router) {
		return new VertxServiceHandlerImpl.BuilderImpl(router);
	}

	interface Builder {

		Builder uriPrefix(String uriPrefix);

		Builder method(HttpMethod method);

		Builder preconditioner(Handler<RoutingContext> preconditioner);

		Builder bind(ServiceDispatcher serviceDispatcher);

		VertxServiceHandler build();
	}
}
