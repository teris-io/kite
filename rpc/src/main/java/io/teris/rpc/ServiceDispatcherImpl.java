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
import io.teris.rpc.internal.ResponseFields;


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
			Objects.requireNonNull(serializer, "Missing serializer");
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
	public CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] incomingData) {
		CompletableFuture<Object> invocation = new CompletableFuture<>();
		Entry<Object, Method> endpoint = endpoints.get(route);
		if (endpoint != null) {

			Method method = endpoint.getValue();
			Object service = endpoint.getKey();

			deserialize(context, method, incomingData)
				.thenAccept(args -> {
					if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
						try {
							((CompletableFuture<?>) method.invoke(service, args)).whenComplete((obj, t) -> {
								if (t != null) {
									t = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
									invocation.completeExceptionally(new BusinessException(t));
								}
								else {
									invocation.complete(obj);
								}
							});
						}
						catch (InvocationTargetException ex) {
							invocation.completeExceptionally(new BusinessException(ex.getCause()));
						}
						catch (Exception ex) {
							String message = String.format("Failed to invoke %s.%s", method.getDeclaringClass().getSimpleName(), method.getName());
							invocation.completeExceptionally(new InvocationException(message, ex));
						}
					}
					else {
						CompletableFuture.runAsync(() -> {
							try {
								invocation.complete(method.invoke(service, args));
							}
							catch (IllegalAccessException ex) {
								String message = String.format("Failed to invoke %s.%s", method.getDeclaringClass().getSimpleName(), method.getName());
								invocation.completeExceptionally(new InvocationException(message, ex));
							}
							catch (InvocationTargetException ex) {
								invocation.completeExceptionally(new BusinessException(ex.getCause()));
							}
							catch (Exception ex) {
								invocation.completeExceptionally(new BusinessException(ex));
							}
						});
					}
				});
		}
		else {
			invocation.completeExceptionally(new InvocationException(String.format("No route to %s", route)));
		}
		CompletableFuture<Entry<Context, byte[]>> result = new CompletableFuture<>();
		invocation
			.handle((obj, t) -> {
				HashMap<String, Serializable> res = new HashMap<>();
				if (t instanceof InvocationException) {
					res.put(ResponseFields.EXCEPTION,new ExceptionDataHolder((InvocationException) t));
					res.put(ResponseFields.ERROR_MESSAGE, t.getMessage() != null ? t.getMessage() : t.toString());
				}
				else if (t instanceof BusinessException) {
					res.put(ResponseFields.EXCEPTION,new ExceptionDataHolder((BusinessException) t));
					res.put(ResponseFields.ERROR_MESSAGE, t.getMessage() != null ? t.getMessage() : t.toString());
				}
				else if (t != null && t.getCause() != null) {
					Throwable cause = t.getCause();
					res.put(ResponseFields.EXCEPTION, new ExceptionDataHolder(cause));
					res.put(ResponseFields.ERROR_MESSAGE, cause.getMessage() != null ? cause.getMessage() : cause.toString());
				}
				else if (t != null) {
					res.put(ResponseFields.EXCEPTION, new ExceptionDataHolder(t));
					res.put(ResponseFields.ERROR_MESSAGE, t.getMessage() != null ? t.getMessage() : t.toString());
				}
				else if (obj == null || void.class.isAssignableFrom(obj.getClass())
					|| Void.class.isAssignableFrom(obj.getClass())) {
					res.put(ResponseFields.PAYLOAD, null);
				}
				else if (obj instanceof Serializable) {
					res.put(ResponseFields.PAYLOAD, (Serializable) obj);
				}
				else {
					String message = "Returned value is neither Serializable nor void";
					res.put(ResponseFields.EXCEPTION, new ExceptionDataHolder(new InvocationException(message)));
					res.put(ResponseFields.ERROR_MESSAGE, message);
				}
				return res;
			})
			.whenComplete((ser, t) -> {
				if (t != null) {
					result.completeExceptionally(t);
				}
				else {
					serializer.serialize(ser).whenComplete((bt, tn) -> {
						if (tn != null) {
							result.completeExceptionally(tn);
						}
						else {
							result.complete(new SimpleEntry<>(context, bt));
						}
					});
				}
			});
		return result;
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

		CompletableFuture<Object[]> result = new CompletableFuture<>();
		deserializer.<HashMap<String, Serializable>>deserialize(data, Typedef.class.getGenericSuperclass())
			.thenAccept((rawArgs) -> {

				ConcurrentHashMap<String, Object> argMap = new ConcurrentHashMap<>();
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
						Integer.valueOf(rawArgs.size() + method.getParameterCount() + 1), Integer.valueOf(method.getParameterCount()),
						method.getDeclaringClass().getSimpleName(), method.getName());
					result.completeExceptionally(new InvocationException(message));
					return;

				}

				CompletableFuture
					.allOf(argPromises.toArray(new CompletableFuture[]{}))
					.whenComplete((v, t) -> {
						if (t != null) {
							result.completeExceptionally(t);
						}
						else {
							for (int i = 1; i < method.getParameterCount(); i++) {
								Parameter param = method.getParameters()[i];
								Name nameAnnot = param.getAnnotation(Name.class); // validated on binding
								if (nameAnnot != null) {
									initial.set(i, argMap.get(nameAnnot.value()));
								}
							}
							result.complete(initial.toArray());
						}
					});
			});
		return result;
	}

}
