/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import io.teris.kite.Deserializer;
import io.teris.kite.Serializer;


/**
 * Implements the ServiceFactory: the client side proxy factory.
 */
final class ServiceFactoryImpl implements ServiceFactory {

	static class BuilderImpl implements ServiceFactory.PreBuilder, ServiceFactory.Builder {

		private final ServiceInvoker serviceInvoker;

		private Serializer serializer = null;

		private Supplier<String> uidGenerator = () -> UUID.randomUUID().toString();

		private final Map<String, Deserializer> deserializerMap = new HashMap<>();

		BuilderImpl(ServiceInvoker serviceInvoker) {
			this.serviceInvoker = serviceInvoker;
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
		public ServiceFactory build() {
			InvocationHandler invocationHandler = new ServiceProxyInvocationHandler(serviceInvoker, serializer, deserializerMap, uidGenerator);
			return new ServiceFactoryImpl(invocationHandler);
		}
	}

	private final InvocationHandler invocationHandler;

	ServiceFactoryImpl(InvocationHandler invocationHandler) {
		this.invocationHandler = invocationHandler;
	}

	@Nonnull
	@Override
	public <S> S newInstance(@Nonnull Class<S> serviceClass) {
		@SuppressWarnings("unchecked")
		S res = (S) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{serviceClass}, invocationHandler);
		return res;
	}
}
