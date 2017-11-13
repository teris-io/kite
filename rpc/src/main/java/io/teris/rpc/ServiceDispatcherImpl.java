/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */
package io.teris.rpc;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.teris.rpc.internal.InvocationCompleter;
import io.teris.rpc.internal.ProxyMethodUtil;
import io.teris.rpc.internal.ServiceArgDeserializer;
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

		final Map<String, Entry<Object, Method>> endpoints = new HashMap<>();

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
			ServiceValidator.validate(serviceClass);
			for (Method method : serviceClass.getDeclaredMethods()) {
				String route = ProxyMethodUtil.route(method);
				this.endpoints.put(route, new SimpleEntry<>(service, method));
			}
			return this;
		}

		@Nonnull
		@Override
		public ServiceDispatcher build() {
			Objects.requireNonNull(serializer, "Missing serializer for an instance of the client service factory");
			if (endpoints.isEmpty()) {
				throw new IllegalStateException("Service endpoints are empty, no service has been registered");
			}
			return new ServiceDispatcherImpl(endpoints, serializer, deserializerMap);
		}
	}

	@Nonnull
	@Override
	public CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] incoming) {
		Entry<Object, Method> endpoint = endpoints.get(route);
		if (endpoint == null) {
			CompletableFuture<Entry<Context, byte[]>> promise = new CompletableFuture<>();
			promise.completeExceptionally(new TechnicalException(String.format("No route to %s", route)));
			return promise;
		}

		Method method = endpoint.getValue();
		Object service = endpoint.getKey();

		InvocationCompleter completer = new InvocationCompleter(method, serializer);
		ServiceArgDeserializer argDeserializer = new ServiceArgDeserializer();

		Object[] callArgs;
		try {
			Deserializer deserializer = deserializerMap.getOrDefault(context.get(Context.CONTENT_TYPE_KEY), serializer.deserializer());
			callArgs = argDeserializer.deserialize(deserializer, context, method, incoming);
		}
		catch (Exception ex) {
			return completer.complete(method, new InvocationException(method, ex));
		}

		Object callResult;
		try {
			callResult = method.invoke(service, callArgs);
		}
		catch (Exception ex) {
			return completer.complete(method, new BusinessException(ex));
		}

		if (callResult instanceof Serializable) {
			return completer.complete(context, (Serializable) callResult);
		}
		else if (callResult instanceof CompletableFuture) {
			return completer.complete(context, (CompletableFuture<?>) callResult);
		}
		else if (callResult instanceof Future) {
			return completer.complete(context, (Future) callResult);
		}
		else if (callResult == null && !Future.class.isAssignableFrom(method.getReturnType())) {
			return completer.complete(context);
		}
		else if (void.class.isAssignableFrom(method.getReturnType()) || Void.class.isAssignableFrom(method.getReturnType())) {
			return completer.complete(context);
		}
		else if (callResult == null) {
			return completer.complete(new InvocationException(method, "received null for a future"));
		}
		else {
			return completer.complete(new InvocationException(method, "return type is neither Serializable nor void"));
		}
	}
}
