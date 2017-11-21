/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;


public interface ServiceDispatcher {

	@Nonnull
	CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] data);

	@Nonnull
	Set<String> dispatchRoutes();

	@Nonnull
	static Builder builder() {
		return new ServiceDispatcherImpl.BuilderImpl();
	}

	interface Builder {

		/**
		 * Binds a serializer used to serialize service method arguments for the remote caller.
		 */
		@Nonnull
		Builder serializer(@Nonnull Serializer serializer);

		/**
		 * Binds a content type specific deserializer used to deserialize data received in
		 * response from the server based on the content type of the response.
		 */
		@Nonnull
		Builder deserializer(@Nonnull String contentType, @Nonnull Deserializer deserializer);

		/**
		 * Binds a collection of deserializers used to deserialize data received in response
		 * from the server based on the content type of the response.
		 */
		@Nonnull
		Builder deserializers(@Nonnull Map<String, Deserializer> deserializerMap);

		@Nonnull
		Builder executors(@Nonnull ExecutorService executors);

		@Nonnull
		<S> Builder bind(@Nonnull Class<S> serviceClass, @Nonnull S service) throws InvocationException;

		@Nonnull
		ServiceDispatcher build();
	}
}
