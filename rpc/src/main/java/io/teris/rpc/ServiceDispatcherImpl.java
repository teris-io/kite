/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */
package io.teris.rpc;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.teris.rpc.internal.ServiceValidator;


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
			Map<String, Entry<Object, Method>> entries = mapServiceMethods(serviceClass, service);
			this.endpoints.putAll(entries);
			return this;
		}

		@Nonnull
		@Override
		public ServiceDispatcher build() {
			return new ServiceDispatcherImpl(endpoints, serializer, deserializerMap);
		}

		@Nonnull
		static <S> Map<String, Entry<Object, Method>> mapServiceMethods(@Nonnull Class<S> serviceClass, @Nonnull S service) throws ServiceException {
			ServiceValidator.validate(serviceClass);

			// FIXME
			return Collections.emptyMap();
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


		return null;
	}


	private static class Typedef extends LinkedHashMap<String, Serializable> {}

	@Nullable
	Object[] deserialize(@Nonnull Context context, @Nonnull Method method, @Nullable byte[] data) throws InvocationException {
		List<Object> res = Arrays.stream(method.getParameters())
			.map(it -> null)
			.collect(Collectors.toList());
		res.set(0, context);

		if (data == null || data.length == 0) {
			return res.toArray();
		}

		Deserializer deserializer = deserializerMap.getOrDefault(context.get(Context.CONTENT_TYPE_KEY), serializer.deserializer());

		LinkedHashMap<String, Serializable> rawArgMap = deserializer.deserialize(data, Typedef.class.getGenericSuperclass());
		for (int i = 1; i < method.getParameterCount(); i++) {
			Parameter param = method.getParameters()[i];
			Name nameAnnot = param.getAnnotation(Name.class); // validated on binding
			Object arg = null;
			if (nameAnnot != null) {
				byte[] paramData = (byte[]) rawArgMap.remove(nameAnnot.value()); // requirement on deserializer: Serializable -> byte[]
				if (paramData != null) {
					arg = deserializer.deserialize(paramData, param.getParameterizedType());
				}
			}
			res.set(i, arg);
		}
		if (!rawArgMap.isEmpty()) {
			throw new InvocationException(method, String.format("payload fields %s do not match service signature",
				rawArgMap.keySet()));
		}
		return res.toArray();
	}
}
