/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import io.teris.rpc.internal.ProxyMethodUtil;
import io.teris.rpc.internal.ResponseFields;


/**
 * Provides the default implementation of the ServiceCreator that implements the
 * creation of service proxies based on parametrizable serializer and
 */
class ServiceCreatorImpl implements ServiceCreator {

	private final InvocationHandler invocationHandler;

	ServiceCreatorImpl(ServiceInvoker serviceInvoker, Serializer serializer, Map<String, Deserializer> deserializerMap, Supplier<String> uidGenerator) {
		this.invocationHandler = new ClientServiceInvocationHandler(serviceInvoker, serializer, deserializerMap, uidGenerator);
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
			Objects.requireNonNull(serviceInvoker, "Missing remote caller for an instance of the client service factory");
			Objects.requireNonNull(serializer, "Missing serializer for an instance of the client service factory");
			Objects.requireNonNull(uidGenerator, "Missing unique Id generator");
			return new ServiceCreatorImpl(serviceInvoker, serializer, deserializerMap, uidGenerator);
		}
	}

	/**
	 * This handler proxies service method calls to remotes by performing the following
	 * operations:
	 * - validate method definition (arguments, return type, route)
	 * - extract context from the first method argument and serialize other arguments
	 * - extract method remote route
	 * - perform a remote invocation using the serviceInvoker
	 * - if successful deserialize the response using a deserializer for the content type
	 * delivered in the response context
	 * <p>
	 * In case the service method returns a future, the execution is wrapped into a
	 * completable future, exceptional if required, and no exceptions will be thrown directly.
	 */
	static class ClientServiceInvocationHandler implements InvocationHandler {

		private final ServiceInvoker serviceInvoker;

		private final Serializer serializer;

		private final Map<String, Deserializer> deserializerMap = new HashMap<>();

		private final Supplier<String> uidGenerator;

		ClientServiceInvocationHandler(ServiceInvoker serviceInvoker, Serializer serializer, Map<String, Deserializer> deserializerMap, Supplier<String> uidGenerator) {
			this.serviceInvoker = serviceInvoker;
			this.serializer = serializer;
			this.uidGenerator = uidGenerator;
			this.deserializerMap.putAll(deserializerMap);
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			CompletableFuture<? extends Serializable> promise = callRemote(method, args);
			if (Future.class.isAssignableFrom(method.getReturnType())) {
				return promise;
			}

			try {
				return promise.get();
			}
			catch (ExecutionException ex) {
				Throwable cause = ex.getCause();
				if (cause instanceof CompletionException && cause.getCause() != null) {
					throw cause.getCause();
				}
				else if (cause instanceof RuntimeException) {
					throw cause;
				}
				String message = String.format("Unexpected exception when invoking invoke %s.%s",
					method.getDeclaringClass().getSimpleName(), method.getName());
				throw new InvocationException(message, cause);
			}
		}

		private static class Typedef extends HashMap<String, Serializable> {}

		private <RS extends Serializable> CompletableFuture<RS> callRemote(Method method, Object[] args) {
			CompletableFuture<RS> result = new CompletableFuture<>();
			Type type;
			String routingKey;
			Entry<Context, LinkedHashMap<String, Serializable>> parsedArgs;
			try {
				type = ProxyMethodUtil.returnType(method);
				routingKey = ProxyMethodUtil.route(method);
				parsedArgs = ProxyMethodUtil.arguments(method, args);
			}
			catch (RuntimeException ex) {
				result.completeExceptionally(ex);
				return result;
			}

			Context context = parsedArgs.getKey();
			LinkedHashMap<String, Serializable> payload = parsedArgs.getValue();

			CompletableFuture<byte[]> serializationPromise;
			if (payload != null) {
				serializationPromise = serializer.serialize(payload);
			}
			else {
				serializationPromise = CompletableFuture.completedFuture(null);
			}
			Context requestContext = new Context(context);
			requestContext.put(Context.X_REQUEST_ID_KEY, uidGenerator.get());
			requestContext.put(Context.CONTENT_TYPE_KEY, serializer.contentType());

			serializationPromise
				.thenCompose((data) -> serviceInvoker.call(routingKey, requestContext, data))
				.thenCompose((entry) -> {
					byte[] responseData = entry.getValue();
					Context responseContext = entry.getKey();
					if (responseContext != null) {
						requestContext.putAll(responseContext);
					}
					if (responseData != null) {
						Deserializer deserializer =
							deserializerMap.getOrDefault(requestContext.get(Context.CONTENT_TYPE_KEY), serializer.deserializer());
						return deserializer.<HashMap<String, Serializable>>deserialize(responseData, Typedef.class
							.getGenericSuperclass());
					}
					else {
						return CompletableFuture.completedFuture(null);
					}
				})
				.thenCompose((response) -> {
					if (response == null) {
						return CompletableFuture.completedFuture(null);
					}
					Deserializer deserializer =
						deserializerMap.getOrDefault(requestContext.get(Context.CONTENT_TYPE_KEY), serializer.deserializer());
					byte[] responseException = (byte[]) response.get(ResponseFields.EXCEPTION);
					byte[] responsePayload = (byte[]) response.get(ResponseFields.PAYLOAD);
					if (responseException != null) {
						return deserializer.<Serializable>deserialize(responseException, ExceptionDataHolder.class);
					}
					else if (responsePayload != null) {
						return deserializer.deserialize(responsePayload, type);
					}
					else {
						return CompletableFuture.completedFuture(null);
					}
				})
				.whenComplete((obj, t) -> {
					// make sure original context now gets all the information
					// (can potentially be overwritten by a concurrent request)
					context.putAll(requestContext);
					if (t != null && t.getCause() != null) {
						t = t.getCause();
					}
					if (t instanceof RuntimeException) {
						result.completeExceptionally(t);
					}
					else if (t != null) {
						result.completeExceptionally(new InvocationException(String.format("Failed to invoke %s.%s",
							method.getDeclaringClass().getSimpleName(), method.getName()), t));
					}
					else if (obj instanceof ExceptionDataHolder) {
						result.completeExceptionally(((ExceptionDataHolder) obj).exception());
					}
					else {
						@SuppressWarnings("unchecked")
						RS res = (RS) obj;
						result.complete(res);
					}
				});
			return result;
		}
	}
}
