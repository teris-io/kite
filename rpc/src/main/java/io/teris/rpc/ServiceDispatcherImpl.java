/**
 * Copyright (c) Profidata AG 2017
 */
package io.teris.rpc;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.teris.rpc.internal.ServiceUtil;


class ServiceDispatcherImpl implements ServiceDispatcher {

	private final Map<String, Entry<Object, Method>> endpoints = new HashMap<>();

	private final Serializer serializer;

	private final Map<String, Deserializer> deserializerMap = new HashMap<>();

	ServiceDispatcherImpl(Map<String, Entry<Object, Method>> endpoints, Serializer serializer, Map<String, Deserializer> deserializerMap) {
		this.endpoints.putAll(endpoints);
		this.serializer = serializer;
		this.deserializerMap.putAll(deserializerMap);
	}

	static class BuilderImpl implements ServiceDispatcher.Builder {

		private final Map<String, Entry<Object, Method>> endpoints = new HashMap<>();

		private Serializer serializer = null;

		private final Map<String, Deserializer> deserializerMap = new HashMap<>();

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
		public <S> Builder bind(@Nonnull Class<S> serviceClass, @Nonnull S service) throws ServiceException {
			Map<String, Entry<Object, Method>> entries = ServiceUtil.mapServiceMethods(serviceClass, service);
			this.endpoints.putAll(entries);
			return this;
		}

		@Nonnull
		@Override
		public ServiceDispatcher build() {
			return new ServiceDispatcherImpl(endpoints, serializer, deserializerMap);
		}
	}

	@Nonnull
	@Override
	public CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] data) {
		CompletableFuture<Entry<Context, byte[]>> res = new CompletableFuture<>();
		if (!endpoints.containsKey(route)) {
			res.completeExceptionally(new TechnicalException(String.format("No route to %s", route)));
			return res;
		}

		Deserializer deserializer = deserializerMap.getOrDefault(context.get(Context.CONTENT_TYPE_KEY), serializer.deserializer());

		// FIXME


		return null;
	}
}
