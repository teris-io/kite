/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;


/**
 * Provides a mechanism of dispatching incoming data onto concrete service and service method
 * implementations bound to this dispatcher.
 */
public interface ServiceDispatcher {

	/**
	 * The call method is called by the RPC invocation layer supplying route, context and
	 * data received from the client.
	 */
	@Nonnull
	CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] data);

	/**
	 * Lists all the routes registered for dispatching (flattening out every method of every
	 * service)
	 */
	@Nonnull
	Set<String> dispatchRoutes();

	/**
	 * Creates a new builder for the ServiceDispatcher.
	 */
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

		/**
		 * Binds an executor service to asynchronously execute long synchronous service calls.
		 */
		@Nonnull
		Builder executors(@Nonnull ExecutorService executors);

		@Nonnull
		Builder uidGenerator(@Nonnull Supplier<String> uidGenerator);

		/**
		 * Binds a preprocessor executed for each service method before dispatching to the
		 * concrete implementation. All registered preprpocessors are called in the order
		 * of their registration on the dispatcher supplying the result of any given iteration
		 * into the next one.
		 */
		@Nonnull
		Builder preprocessor(BiFunction<Context, Entry<String, byte[]>, CompletableFuture<Context>> preprocessor);

		/**
		 * Binds an implementation of a service and registers all dispatching routes.
		 */
		@Nonnull
		<S> Builder bind(@Nonnull Class<S> serviceClass, @Nonnull S service) throws InvocationException;

		@Nonnull
		ServiceDispatcher build();
	}
}
