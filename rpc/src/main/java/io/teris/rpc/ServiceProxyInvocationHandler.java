/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import io.teris.rpc.internal.ProxyMethodUtil;


/**
 * This handler proxies service method calls to remotes by performing the following
 * operations:
 * - validate method definition (arguments, return type, route)
 * - extract context from the first method argument and serialize other arguments
 * - extract method route
 * - perform a remote invocation using the serviceInvoker
 * - if successful deserialize the response using a deserializer for the content type
 * delivered in the response context
 * <p>
 * In case the service method returns a future, the execution is wrapped into a
 * completable future, exceptional if required, and no exceptions will be thrown directly.
 */
class ServiceProxyInvocationHandler implements InvocationHandler {

	private final ServiceInvoker serviceInvoker;

	private final Serializer serializer;

	private final Map<String, Deserializer> deserializerMap = new HashMap<>();

	private final Supplier<String> uidGenerator;

	ServiceProxyInvocationHandler(ServiceInvoker serviceInvoker, Serializer serializer, Map<String, Deserializer> deserializerMap, Supplier<String> uidGenerator) {
		this.serviceInvoker = Objects.requireNonNull(serviceInvoker, "Service invoker is required");
		this.serializer = Objects.requireNonNull(serializer, "Serializer is required");
		this.uidGenerator = Objects.requireNonNull(uidGenerator, "Unique Id generator is required");
		if (deserializerMap != null) {
			this.deserializerMap.putAll(deserializerMap);
		}
	}

	@Override
	public Object invoke(Object $, Method method, Object[] args) throws Throwable {
		CompletableFuture<? extends Serializable> promise = callRemote(method, args);
		if (Future.class.isAssignableFrom(method.getReturnType())) {
			return promise;
		}
		try {
			return promise.get();
		}
		catch (ExecutionException ex) {
			throw ex.getCause();
		}
	}

	private static class Typedef extends HashMap<String, Serializable> {}

	<RS extends Serializable> CompletableFuture<RS> callRemote(Method method, Object[] args) {
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

		Context requestContext = new Context(context);
		requestContext.put(Context.X_REQUEST_ID_KEY, uidGenerator.get());
		requestContext.put(Context.CONTENT_TYPE_KEY, serializer.contentType());

		(payload != null ? serializer.serialize(payload) : CompletableFuture.<byte[]>completedFuture(null))
			.thenCompose((data) -> serviceInvoker.call(routingKey, requestContext, data))
			.thenCompose((entry) -> {
				Context responseContext = entry.getKey();
				if (responseContext != null) {
					requestContext.putAll(responseContext);
				}
				byte[] responseData = entry.getValue();
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
				// do not change this logic to thenApply as it would wrap exceptions
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
