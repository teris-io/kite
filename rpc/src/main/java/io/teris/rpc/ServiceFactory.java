/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nonnull;


public interface ServiceFactory {

	/**
	 * Creates a client service factory instance that can generate service proxies
	 * bound to calls using the provided serialization and caller.
	 *
	 * @param serviceClass the interface class to route.
	 */
	@Nonnull
	<S> S newInstance(@Nonnull Class<S> serviceClass);

	/**
	 * @return a new instance of the client service factory builder.
	 */
	static Builder builder() {
		return new ServiceFactoryImpl.BuilderImpl();
	}

	/**
	 * Defines a step-builder interface for the client service factory.
	 */
	interface Builder {

		/**
		 * Binds a remote caller used to perform data transport between the client and the
		 * server.
		 */
		@Nonnull
		Builder serviceInvoker(@Nonnull ServiceInvoker serviceInvoker);

		/**
		 * Binds a serializer for serializing service method arguments before remote invocation.
		 */
		@Nonnull
		Builder serializer(@Nonnull Serializer serializer);

		/**
		 * Binds a deserializer for a specific content type for deserializing data recevied
		 * in response to a remote invocation.
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
		Builder uidGenerator(@Nonnull Supplier<String> uidGenerator);

		/**
		 * Builds an instance of the client service factory.
		 */
		@Nonnull
		ServiceFactory build();
	}
}
