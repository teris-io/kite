/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.http.vertx;

import javax.annotation.Nonnull;

import io.teris.rpc.ServiceInvoker;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;


public interface VertxServiceInvoker extends ServiceInvoker {

	@Nonnull
	static Builder builder(@Nonnull Vertx vertx) {
		return new VertxServiceInvokerImpl.BuilderImpl(vertx);
	}

	interface Builder {

		@Nonnull
		Builder httpClientOptions(@Nonnull HttpClientOptions httpClientOptions);

		@Nonnull
		Builder uriPrefix(@Nonnull String uriPrefix);

		@Nonnull
		VertxServiceInvoker build();
	}
}
