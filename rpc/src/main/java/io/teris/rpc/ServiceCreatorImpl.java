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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;

import io.teris.rpc.internal.ProxyMethodUtil;
import io.teris.rpc.internal.ResponseFields;


/**
 * Provides the default implementation of the ServiceCreator that implements the
 * creation of service proxies based on parametrizable serializer and
 */
class ServiceCreatorImpl implements ServiceCreator {

	private final InvocationHandler invocationHandler;

	ServiceCreatorImpl(ServiceInvoker serviceInvoker, Serializer serializer, Map<String, Deserializer> deserializerMap) {
		this.invocationHandler = new ClientServiceInvocationHandler(serviceInvoker, serializer, deserializerMap);
	}

	@Nonnull
	@Override
	public <S> S newInstance(@Nonnull Class<S> serviceClass) throws InstantiationException {
		try {
			@SuppressWarnings("unchecked")
			S res = (S) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ serviceClass }, invocationHandler);
			return res;
		}
		catch (RuntimeException ex) {
			throw new InstantiationException(serviceClass, ex);
		}
	}

	static class BuilderImpl implements ServiceCreator.Builder {

		private ServiceInvoker serviceInvoker = null;

		private Serializer serializer = null;

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
		public ServiceCreator build() {
			Objects.requireNonNull(serviceInvoker, "Missing remote caller for an instance of the client service factory");
			Objects.requireNonNull(serializer, "Missing serializer for an instance of the client service factory");
			return new ServiceCreatorImpl(serviceInvoker, serializer, deserializerMap);
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
	 *   delivered in the response context
	 *
	 * In case the service method returns a future, the execution is wrapped into a
	 * completable future, exceptional if required, and no exceptions will be thrown directly.
	 */
	static class ClientServiceInvocationHandler implements InvocationHandler {

		private final ServiceInvoker serviceInvoker;

		private final Serializer serializer;

		private final Map<String, Deserializer> deserializerMap = new HashMap<>();

		ClientServiceInvocationHandler(ServiceInvoker serviceInvoker, Serializer serializer, Map<String, Deserializer> deserializerMap) {
			this.serviceInvoker = serviceInvoker;
			this.serializer = serializer;
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
				if (cause instanceof RuntimeException || cause instanceof ServiceException) {
					throw cause;
				}
				for (Class<?> declaredExceptionClass: method.getExceptionTypes()) {
					if (declaredExceptionClass.isAssignableFrom(cause.getClass())) {
						throw cause;
					}
				}
				throw new InvocationException(method, cause);
			}
		}

		private static class Typedef extends HashMap<String, Serializable> {}

		private <RS extends Serializable> CompletableFuture<RS> callRemote(Method method, Object[] args) {
			try {
				Type type = ProxyMethodUtil.returnType(method);
				String routingKey = ProxyMethodUtil.route(method);
				Entry<Context, LinkedHashMap<String, Serializable>> parsedArgs = ProxyMethodUtil.arguments(method, args);
				Context context = parsedArgs.getKey();
				LinkedHashMap<String, Serializable> payload = parsedArgs.getValue();
				byte[] data = payload != null ? serializer.serialize(payload) : null;

				CompletableFuture<Entry<Context, byte[]>> responsePromise =	serviceInvoker.call(routingKey, context, data);
				return responsePromise.thenApply(entry -> {
					Context responseContext = entry.getKey();
					if (responseContext != null) {
						context.putAll(responseContext);
					}
					byte[] responseData = entry.getValue();
					if (responseData == null) {
						return null;
					}

					Deserializer deserializer = deserializerMap.getOrDefault(context.get(Context.CONTENT_TYPE_KEY), serializer.deserializer());
					HashMap<String, Serializable> response = deserializer.deserialize(responseData, Typedef.class.getGenericSuperclass());

					byte[] responseException = (byte[]) response.get(ResponseFields.EXCEPTION);
					if (responseException != null) {
						throw deserializer.deserialize(responseException, ExceptionDataHolder.class).exception();
					}

					byte[] responsePayload = (byte[]) response.get(ResponseFields.PAYLOAD);
					return responsePayload != null ? deserializer.deserialize(responsePayload, type) : null;
				});
			}
			catch (RuntimeException ex) {
				CompletableFuture<RS> res = new CompletableFuture<>();
				res.completeExceptionally(new InvocationException(method, ex));
				return res;
			}
		}
	}
}
