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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.teris.rpc.internal.ProxyMethodUtil;


/**
 * Defines an InvocationHandler that proxies service calls to a remote implementation
 * serializing the request using the serializer, transporting the data using the transport
 * and deserializing the resposnse using a deserializer from the deserializerMap.
 */
class RemoteProxy<S> implements InvocationHandler {

	private final Class<S> serviceClass;

	private final Context context;

	private final Serializer serializer;

	private final Transporter transporter;

	private final Map<String, Deserializer> deserializerMap;

	RemoteProxy(Class<S> serviceClass, Context context, Serializer serializer, Transporter transporter, Map<String, Deserializer> deserializerMap) {
		this.serviceClass = serviceClass;
		this.context = context;
		this.serializer = serializer;
		this.transporter = transporter;
		this.deserializerMap = deserializerMap;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		CompletableFuture<? extends Serializable> promise = doInvoke(method, args);
		if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
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

			LinkedHashMap<String, Serializable> payload = ProxyMethodUtil.arguments(method, args);
			byte[] data = payload != null ? serializer.serialize(payload) : null;

			String routingKey = ProxyMethodUtil.route(method);

			CompletableFuture<byte[]> promise =	transporter.transport(routingKey, context, data);
			return promise.thenApply(res -> res == null ? null : serializer.deserializer().deserialize(res, type));
		}
		catch (Exception ex) {
			CompletableFuture<RS> res = new CompletableFuture<>();
			res.completeExceptionally(ex);
			return res;
		}
	}
}


