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
		CompletableFuture<Object> invocation = new CompletableFuture<>();
		Entry<Object, Method> endpoint = endpoints.get(route);
		if (endpoint != null) {
			Method method = endpoint.getValue();
			Object service = endpoint.getKey();

			Deserializer deserializer =
				deserializerMap.getOrDefault(context.get(Context.CONTENT_TYPE_KEY), serializer.deserializer());
			new ServiceArgDeserializer().deserialize(deserializer, context, method, incoming)
				.thenAccept(args -> {
					if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
						try {
							((CompletableFuture<?>) method.invoke(service, args)).whenComplete((obj, t) -> {
								if (t != null) {
									invocation.completeExceptionally(t);
								}
								else {
									invocation.complete(obj);
								}
							});
						}
						catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
							invocation.completeExceptionally(new InvocationException(method, ex));
						}
						catch (Exception ex) {
							invocation.completeExceptionally(new BusinessException(ex));
						}
					}
					else {
						CompletableFuture.runAsync(() -> {
							try {
								invocation.complete(method.invoke(service, args));
							}
							catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
								invocation.completeExceptionally(new InvocationException(method, ex));
							}
							catch (Exception ex) {
								invocation.completeExceptionally(new BusinessException(ex));
							}
						});
					}
				});
		}
		else {
			invocation.completeExceptionally(new TechnicalException(String.format("No route to %s", route)));
		}
		CompletableFuture<Entry<Context, byte[]>> result = new CompletableFuture<>();
		invocation
			.handle((obj, t) -> {
				HashMap<String, Serializable> res = new HashMap<>();
				if (t != null) {
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
					res.put(ResponseFields.EXCEPTION, new ExceptionDataHolder(new TechnicalException(message)));
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
}
