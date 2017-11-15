/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */
package io.teris.rpc;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.teris.rpc.internal.ProxyMethodUtil;
import io.teris.rpc.internal.ResponseFields;
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
		public <S> Builder bind(@Nonnull Class<S> serviceClass, @Nonnull S service) throws InstantiationException {
			ServiceValidator.validate(serviceClass);
			for (Method method : serviceClass.getDeclaredMethods()) {
				try {
					String route = ProxyMethodUtil.route(method);
					this.endpoints.put(route, new SimpleEntry<>(service, method));
				}
				catch (InvocationException ex) {
					throw new InstantiationException(serviceClass, ex);
				}
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
	public Set<String> dispatchRoutes() {
		return Collections.unmodifiableSet(new TreeSet<>(endpoints.keySet()));
	}

	@Nonnull
	@Override
	public CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] incoming) {
		Entry<Object, Method> endpoint = endpoints.get(route);
		if (endpoint == null) {
			return complete(context, new TechnicalException(String.format("No route to %s", route)));
		}

		Method method = endpoint.getValue();
		Object service = endpoint.getKey();

		ServiceArgDeserializer argDeserializer = new ServiceArgDeserializer();

		Object[] callArgs;
		try {
			// FIXME supplyAsync
			Deserializer deserializer = deserializerMap.getOrDefault(context.get(Context.CONTENT_TYPE_KEY), serializer.deserializer());
			callArgs = argDeserializer.deserialize(deserializer, context, method, incoming);
		}
		catch (Exception ex) {
			return complete(context, new InvocationException(method, ex));
		}

		Object callResult;
		try {
			// FIXME supplyAsync if result non-future
			callResult = method.invoke(service, callArgs);
		}
		catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
			return complete(context, new InvocationException(method, ex));
		}
		catch (Exception ex) {
			return complete(context, new BusinessException(ex));
		}

		if (callResult instanceof Serializable) {
			return complete(context, (Serializable) callResult);
		}
		else if (callResult instanceof CompletableFuture) {
			return complete(context, (CompletableFuture<?>) callResult);
		}
		else if (callResult instanceof Future) {
			return complete(context, (Future) callResult);
		}
		else if (callResult == null && !Future.class.isAssignableFrom(method.getReturnType())) {
			return complete(context);
		}
		else if (void.class.isAssignableFrom(method.getReturnType()) || Void.class.isAssignableFrom(method.getReturnType())) {
			return complete(context);
		}
		else if (callResult == null) {
			return complete(context, new InvocationException(method, "received null for a future"));
		}
		else {
			return complete(context, new InvocationException(method, "return type is neither Serializable nor void"));
		}
	}


	public CompletableFuture<Entry<Context, byte[]>> complete(Context context) {
		return complete(context, (Serializable) null);
	}

	public CompletableFuture<Entry<Context, byte[]>> complete(Context context, Serializable returnValue) {
		return CompletableFuture.supplyAsync(() -> {
			HashMap<String, Serializable> res = new HashMap<>();
			res.put(ResponseFields.PAYLOAD, returnValue);
			// throw here is the only reason for the future to complete exceptionally
			return new SimpleEntry<>(context, serializer.serialize(res));
		});
	}

	public CompletableFuture<Entry<Context, byte[]>> complete(Context context, Throwable t) {
		return CompletableFuture.supplyAsync(() -> {
			HashMap<String, Serializable> res = new HashMap<>();
			res.put(ResponseFields.EXCEPTION, t);
			// throw here is the only reason for the future to complete exceptionally
			return new SimpleEntry<>(context, serializer.serialize(res));
		});
	}

	public CompletableFuture<Entry<Context, byte[]>> complete(Context context, CompletableFuture<?> returnValue) {
		CompletableFuture<Entry<Context, byte[]>> promise = new CompletableFuture<>();
		returnValue.handleAsync((obj, t) -> {
			HashMap<String, Serializable> res = new HashMap<>();
			if (t != null) {
				res.put(ResponseFields.EXCEPTION, new BusinessException(t));
				res.put(ResponseFields.ERROR_MESSAGE, t.getMessage() != null ? t.getMessage() : t.toString());
			}
			else if (obj == null || void.class.isAssignableFrom(obj.getClass()) || Void.class.isAssignableFrom(obj.getClass())) {
				res.put(ResponseFields.PAYLOAD, null);
			}
			else if (obj instanceof Serializable) {
				res.put(ResponseFields.PAYLOAD, (Serializable) obj);
			}
			else {
				String message = "Returned value is neither Serializable nor void";
				res.put(ResponseFields.EXCEPTION, new TechnicalException(message));
				res.put(ResponseFields.ERROR_MESSAGE, message);
			}
			try {
				// throw here is the only reason for the future to complete exceptionally
				promise.complete(new SimpleEntry<>(context, serializer.serialize(res)));
			}
			catch (Exception ex) {
				promise.completeExceptionally(ex);
			}
			return null;
		});
		return promise;
	}

	public CompletableFuture<Entry<Context, byte[]>> complete(Context context, Future returnValue) {
		return CompletableFuture.supplyAsync(() -> {
			HashMap<String, Serializable> res = new HashMap<>();
			try {
				Object obj = returnValue.get();
				if (obj == null || void.class.isAssignableFrom(obj.getClass()) || Void.class.isAssignableFrom(obj.getClass())) {
					res.put(ResponseFields.PAYLOAD, null);
				}
				else if (obj instanceof Serializable) {
					res.put(ResponseFields.PAYLOAD, (Serializable) obj);
				}
				else {
					String message = "Returned value is neither Serializable nor void";
					res.put(ResponseFields.EXCEPTION, new TechnicalException(message));
					res.put(ResponseFields.ERROR_MESSAGE, message);
				}
			}
			catch (ExecutionException ex) {
				res.put(ResponseFields.EXCEPTION, new BusinessException(ex.getCause()));
				res.put(ResponseFields.ERROR_MESSAGE, ex.getCause().getMessage() != null ? ex.getCause().getMessage() : ex.getCause().toString());
			}
			catch (InterruptedException ex) {
				res.put(ResponseFields.EXCEPTION, new BusinessException(ex));
				res.put(ResponseFields.ERROR_MESSAGE, ex.getMessage() != null ? ex.getMessage() : ex.toString());
			}
			// throw here is the only reason for the future to complete exceptionally
			return new SimpleEntry<>(context, serializer.serialize(res));
		});
	}

}
