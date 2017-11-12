/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.teris.rpc.internal.ProxyMethodUtil;


/**
 * Defines an InvocationHandler that proxies service calls to a remote implementation
 * serializing the request using the serializer, transporting the data using the requester
 * and deserializing the resposnse using a deserializer from the deserializerMap.
 */
class RemoteProxy implements InvocationHandler {

	private final Serializer serializer;

	private final Requester requester;

	private final Map<String, Deserializer> deserializerMap;

	RemoteProxy(Serializer serializer, Requester requester, Map<String, Deserializer> deserializerMap) {
		this.serializer = serializer;
		this.requester = requester;
		this.deserializerMap = deserializerMap;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		CompletableFuture<? extends Serializable> promise = doInvoke(method, args);
		if (Future.class.isAssignableFrom(method.getReturnType())) {
			return promise;
		}

		try {
			return promise.get();
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof RuntimeException) {
				throw cause;
			}
			for (Class<?> eclazz: method.getExceptionTypes()) {
				if (eclazz.isAssignableFrom(cause.getClass())) {
					throw cause;
				}
			}
			throw new RuntimeException(cause);
		}
	}

	private <RS extends Serializable> CompletableFuture<RS> doInvoke(Method method, Object[] args) {
		try {
			Type type = ProxyMethodUtil.returnType(method);
			String routingKey = ProxyMethodUtil.route(method);
			Entry<Context, LinkedHashMap<String, Serializable>> parsedArgs = ProxyMethodUtil.arguments(method, args);
			Context context = parsedArgs.getKey();
			LinkedHashMap<String, Serializable> payload = parsedArgs.getValue();
			byte[] data = payload != null ? serializer.serialize(payload) : null;

			CompletableFuture<Entry<Context, byte[]>> responsePromise =	requester.execute(routingKey, context, data);
			return responsePromise.thenApply(entry -> {
				Context responseContext = entry.getKey();
				Deserializer deserializer = deserializerMap.getOrDefault(responseContext.get(Context.CONTENT_TYPE_KEY), serializer.deserializer());
				byte[] response = entry.getValue();
				return response == null ? null : deserializer.deserialize(response, type);
			});
		}
		catch (Exception ex) {
			CompletableFuture<RS> res = new CompletableFuture<>();
			res.completeExceptionally(ex);
			return res;
		}
	}
}


