/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */
package io.teris.rpc;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.teris.rpc.internal.ProxyMethodUtil;


class ServiceDispatcherImpl implements ServiceDispatcher {

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
		public <S> Builder bind(@Nonnull Class<S> serviceClass, @Nonnull S service) throws InvocationException {
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
			return new ServiceDispatcherImpl(endpoints, serializer, deserializerMap);
		}
	}

	private final Map<String, Entry<Object, Method>> endpoints = new HashMap<>();

	private final Serializer serializer;

	private final Map<String, Deserializer> deserializerMap = new HashMap<>();

	ServiceDispatcherImpl(Map<String, Entry<Object, Method>> endpoints, Serializer serializer, Map<String, Deserializer> deserializerMap) {
		this.endpoints.putAll(endpoints);
		this.serializer = Objects.requireNonNull(serializer, "Serializer is required");
		this.deserializerMap.putAll(deserializerMap);
	}

	@Nonnull
	@Override
	public Set<String> dispatchRoutes() {
		return Collections.unmodifiableSet(new TreeSet<>(endpoints.keySet()));
	}


	@Nonnull
	@Override
	public CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] incomingData) {
		CompletableFuture<Object[]> incoming;
		Entry<Object, Method> endpoint = endpoints.get(route);
		if (endpoint != null && endpoint.getKey() != null && endpoint.getValue() != null) {
			incoming = deserialize(context, endpoint.getValue(), incomingData);
		}
		else {
			incoming = CompletableFuture.supplyAsync(() -> {
				throw new InvocationException(String.format("No route to %s", route));
			});
		}

		return incoming
			.thenCompose((Object[] args) -> {
				Method method = Objects.requireNonNull(endpoint).getValue();
				Object service = endpoint.getKey();
				if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
					try {
						return ((CompletableFuture<Object>) method.invoke(service, args))
							.handle((obj, t) -> {
								if (t != null) {
									throw new BusinessException(t instanceof CompletionException ? t.getCause() : t);
								}
								return obj;
							});
					}
					catch (InvocationTargetException | IllegalAccessException ex) { // IAE unlikely by design
						return CompletableFuture.supplyAsync(() -> {
							throw new BusinessException(ex.getCause() != null ? ex.getCause() : ex);
						});
					}
				}
				return CompletableFuture.supplyAsync(() -> {
					try {
						return method.invoke(service, args);
					}
					catch (InvocationTargetException | IllegalAccessException ex) { // IAE unlikely by design
						throw new BusinessException(ex.getCause() != null ? ex.getCause() : ex);
					}
				});
			})
			.handle((obj, t) -> {
				HashMap<String, Serializable> res = new HashMap<>();
				if (t instanceof CompletionException) {
					t = t.getCause();
				}
				if (t instanceof InvocationException) {
					res.put(ResponseFields.EXCEPTION, new ExceptionDataHolder((InvocationException) t));
					res.put(ResponseFields.ERROR_MESSAGE, t.getMessage() != null ? t.getMessage() : t.toString());
				}
				else if (t instanceof BusinessException) {
					res.put(ResponseFields.EXCEPTION, new ExceptionDataHolder((BusinessException) t));
					res.put(ResponseFields.ERROR_MESSAGE, t.getMessage() != null ? t.getMessage() : t.toString());
				}
				else if (t != null) {
					throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
				}
				else if (obj == null
					|| void.class.isAssignableFrom(obj.getClass())
					|| Void.class.isAssignableFrom(obj.getClass())) {
					res.put(ResponseFields.PAYLOAD, null);
				}
				else {
					res.put(ResponseFields.PAYLOAD, (Serializable) obj);
				}
				return res;
			})
			.thenCompose(serializer::serialize)
			.thenApply((ser) -> {
				context.put(Context.CONTENT_TYPE_KEY, serializer.contentType());
				return new SimpleEntry<>(context, ser);
			});
	}

	private static class Typedef extends HashMap<String, Serializable> {}

	CompletableFuture<Object[]> deserialize(@Nonnull Context context, @Nonnull Method method, @Nullable byte[] data) throws InvocationException {
		List<Object> initial = Arrays.stream(method.getParameters())
			.map(it -> null)
			.collect(Collectors.toList());
		initial.set(0, context);

		if (data == null || data.length == 0) {
			return CompletableFuture.completedFuture(initial.toArray());
		}

		Deserializer deserializer = deserializerMap.getOrDefault(context.get(Context.CONTENT_TYPE_KEY),
			serializer.deserializer());

		ConcurrentHashMap<String, Object> argMap = new ConcurrentHashMap<>();
		return deserializer.<HashMap<String, Serializable>>deserialize(data, Typedef.class.getGenericSuperclass())
			.thenCompose((rawArgs) -> {

				List<CompletableFuture<Void>> argPromises = new ArrayList<>();
				for (int i = 1; i < method.getParameterCount(); i++) {
					Parameter param = method.getParameters()[i];
					Name nameAnnot = param.getAnnotation(Name.class); // validated non-null on binding
					if (rawArgs.containsKey(nameAnnot.value())) {
						byte[] paramData = (byte[]) rawArgs.remove(nameAnnot.value()); // requirement on deserializer: Serializable -> byte[]
						if (paramData != null) {
							CompletableFuture<Void> argPromise =
								deserializer.deserialize(paramData, param.getParameterizedType())
									.thenAccept((s) -> argMap.put(nameAnnot.value(), s));
							argPromises.add(argPromise);
						}
					}
				}
				if (rawArgs.size() > 0) {
					String message = String.format("Too many arguments (%d instead of %s) to %s.%s",
						Integer.valueOf(rawArgs.size() + method.getParameterCount() + 1), Integer
							.valueOf(method.getParameterCount()),
						method.getDeclaringClass().getSimpleName(), method.getName());
					throw new InvocationException(message);
				}

				return CompletableFuture.allOf(argPromises.toArray(new CompletableFuture[]{}));
			})
			.thenApply((vd) -> {
				for (int i = 1; i < method.getParameterCount(); i++) {
					Parameter param = method.getParameters()[i];
					Name nameAnnot = param.getAnnotation(Name.class); // validated on binding
					if (nameAnnot != null) {
						initial.set(i, argMap.get(nameAnnot.value()));
					}
				}
				return initial.toArray();
			});
	}
}
