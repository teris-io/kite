/**
 * Copyright (c) Profidata AG 2017
 */
package io.teris.rpc;

import java.util.Map;
import javax.annotation.Nonnull;


public interface ClientServiceFactory {

	/**
	 * Creates a client service factory instance that can generate service proxies
	 * bound to calls using the provided serialization and caller.
	 *
	 * @param serviceClass the interface class to route.
	 * @throws InstantiationException when no instance can be constructes for any reason.
	 */
	@Nonnull
	<S> S newInstance(@Nonnull Class<S> serviceClass) throws InstantiationException;

	/**
	 * @return a new instance of the client service factory builder.
	 */
	@Nonnull
	static Builder builder() {
		return new ClientServiceFactoryImpl.BuilderImpl();
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
		Builder remoteInvoker(@Nonnull RemoteInvoker remoteInvoker);

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
		 * Builds an instance of the client service factory.
		 */
		@Nonnull
		ClientServiceFactory build();
	}
}
