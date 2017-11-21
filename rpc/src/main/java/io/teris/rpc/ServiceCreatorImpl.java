/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nonnull;


/**
 * Implements the ServiceCreator: the client side proxy factory.
 */
class ServiceCreatorImpl implements ServiceCreator {

	static class BuilderImpl implements ServiceCreator.Builder {

		private ServiceInvoker serviceInvoker = null;

		private Serializer serializer = null;

		private Supplier<String> uidGenerator = () -> UUID.randomUUID().toString();

		private final Map<String, Deserializer> deserializerMap = new HashMap<>();

		@Nonnull
		@Override
		public Builder serviceInvoker(@Nonnull ServiceInvoker serviceInvoker) {
			this.serviceInvoker = serviceInvoker;
			return this;
		}

		@Nonnull
		@Override
		public Builder serializer(@Nonnull Serializer serializer) {
			this.serializer = serializer;
			return this;
		}

		@Nonnull
		@Override
		public Builder deserializer(@Nonnull String contentType, @Nonnull Deserializer deserializer) {
			this.deserializerMap.put(contentType, deserializer);
			return this;
		}

		@Nonnull
		@Override
		public Builder deserializers(@Nonnull Map<String, Deserializer> deserializerMap) {
			this.deserializerMap.putAll(deserializerMap);
			return this;
		}

		@Nonnull
		@Override
		public Builder uidGenerator(@Nonnull Supplier<String> uidGenerator) {
			this.uidGenerator = uidGenerator;
			return this;
		}

		@Nonnull
		@Override
		public ServiceCreator build() {
			InvocationHandler invocationHandler = new ServiceInvocationHandlerImpl(serviceInvoker, serializer, deserializerMap, uidGenerator);
			return new ServiceCreatorImpl(invocationHandler);
		}
	}

	private final InvocationHandler invocationHandler;

	ServiceCreatorImpl(InvocationHandler invocationHandler) {
		this.invocationHandler = invocationHandler;
	}

	@Nonnull
	@Override
	public <S> S newInstance(@Nonnull Class<S> serviceClass) throws InvocationException {
		try {
			@SuppressWarnings("unchecked")
			S res = (S) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{serviceClass}, invocationHandler);
			return res;
		}
		catch (RuntimeException ex) {
			throw new InvocationException(String
				.format("Failed to create a proxy instance for server %s", serviceClass.getSimpleName()), ex);
		}
	}
}
