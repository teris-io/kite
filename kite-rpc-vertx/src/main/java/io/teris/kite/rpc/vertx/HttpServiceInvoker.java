/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.rpc.vertx;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import io.vertx.core.http.HttpClient;


public interface HttpServiceInvoker extends io.teris.kite.rpc.ServiceInvoker {

	@Nonnull
	CompletableFuture<Void> close();

	@Nonnull
	static Builder httpClient(@Nonnull HttpClient httpClient) {
		return new HttpServiceInvokerImpl.BuilderImpl(httpClient);
	}

	interface Builder {

		@Nonnull
		Builder uriPrefix(@Nonnull String uriPrefix);

		@Nonnull
		HttpServiceInvoker build();
	}
}
