/**
 * Copyright (c) Profidata AG 2017
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


class ClientServiceFactoryImpl implements ClientServiceFactory {

	private final InvocationHandler invocationHandler;

	ClientServiceFactoryImpl(RemoteInvoker remoteInvoker, Serializer serializer, Map<String, Deserializer> deserializerMap) {
		this.invocationHandler = new ClientServiceInvocationHandler(remoteInvoker, serializer, deserializerMap);
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

	static class BuilderImpl implements ClientServiceFactory.Builder {

		private RemoteInvoker remoteInvoker = null;

		private Serializer serializer = null;

		private final Map<String, Deserializer> deserializerMap = new HashMap<>();

		@Nonnull
		@Override
		public Builder remoteInvoker(@Nonnull RemoteInvoker remoteInvoker) {
			this.remoteInvoker = remoteInvoker;
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
		public ClientServiceFactory build() {
			Objects.requireNonNull(remoteInvoker, "Missing remote caller for an instance of the client service factory");
			Objects.requireNonNull(serializer, "Missing serializer for an instance of the client service factory");
			return new ClientServiceFactoryImpl(remoteInvoker, serializer, deserializerMap);
		}
	}

	/**
	 * This handler proxies service method calls to remotes by performing the following
	 * operations:
	 * - validate method definition (arguments, return type, route)
	 * - extract context from the first method argument and serialize other arguments
	 * - extract method remote route
	 * - perform a remote invocation using the remoteInvoker
	 * - if successful deserialize the response using a deserializer for the content type
	 *   delivered in the response context
	 *
	 * In case the service method returns a future, the execution is wrapped into a
	 * completable future, exceptional if required, and no exceptions will be thrown directly.
	 */
	static class ClientServiceInvocationHandler implements InvocationHandler {

		private final Serializer serializer;

		private final RemoteInvoker remoteInvoker;

		private final Map<String, Deserializer> deserializerMap;

		ClientServiceInvocationHandler(RemoteInvoker remoteInvoker, Serializer serializer, Map<String, Deserializer> deserializerMap) {
			this.serializer = serializer;
			this.remoteInvoker = remoteInvoker;
			this.deserializerMap = deserializerMap;
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

		private <RS extends Serializable> CompletableFuture<RS> callRemote(Method method, Object[] args) {
			try {
				Type type = ProxyMethodUtil.returnType(method);
				String routingKey = ProxyMethodUtil.route(method);
				Entry<Context, LinkedHashMap<String, Serializable>> parsedArgs = ProxyMethodUtil.arguments(method, args);
				Context context = parsedArgs.getKey();
				LinkedHashMap<String, Serializable> payload = parsedArgs.getValue();
				byte[] data = payload != null ? serializer.serialize(payload) : null;

				CompletableFuture<Entry<Context, byte[]>> responsePromise =	remoteInvoker.call(routingKey, context, data);
				return responsePromise.thenApply(entry -> {
					Context responseContext = entry.getKey();
					Deserializer deserializer = deserializerMap.getOrDefault(responseContext.get(Context.CONTENT_TYPE_KEY), serializer.deserializer());
					byte[] response = entry.getValue();
					return response == null ? null : deserializer.deserialize(response, type);
				});
			}
			catch (RuntimeException ex) {
				CompletableFuture<RS> res = new CompletableFuture<>();
				res.completeExceptionally(new InvocationException(method, ex));
				return res;
			}
			catch (ServiceException ex) {
				CompletableFuture<RS> res = new CompletableFuture<>();
				res.completeExceptionally(ex);
				return res;
			}
		}
	}
}
