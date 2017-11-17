/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.http.vertx;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import io.teris.rpc.ServiceInvoker;
import io.vertx.core.http.HttpClient;


public interface VertxServiceInvoker extends ServiceInvoker {

	@Nonnull
	CompletableFuture<Void> close();

	@Nonnull
	static Builder builder(@Nonnull HttpClient httpClient) {
		return new VertxServiceInvokerImpl.BuilderImpl(httpClient);
	}

	interface Builder {

		@Nonnull
		Builder uriPrefix(@Nonnull String uriPrefix);

		@Nonnull
		VertxServiceInvoker build();
	}
}
