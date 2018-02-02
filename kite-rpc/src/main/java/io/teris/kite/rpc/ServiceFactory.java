/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc;

import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import io.teris.kite.Deserializer;
import io.teris.kite.Serializer;


/**
 * Defines a factory to construct service proxy instances that invoke service
 * calls remotely via the supplied RemoteRequestor.
 */
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
	static PreBuilder invoker(@Nonnull ServiceInvoker serviceInvoker) {
		return new ServiceFactoryImpl.BuilderImpl(serviceInvoker);
	}

	interface PreBuilder {

		/**
		 * Binds a serializer for serializing service method arguments before remote invocation.
		 */
		@Nonnull
		Builder serializer(@Nonnull Serializer serializer);
	}

	/**
	 * Defines a step-builder interface for the client service factory.
	 */
	interface Builder {

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
