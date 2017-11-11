/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.client;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.teris.rpc.Deserializer;
import io.teris.rpc.Serializer;
import io.teris.rpc.Transporter;
import io.teris.rpc.context.CallerContext;
import io.teris.rpc.context.ContextAware;
import io.teris.rpc.internal.ServiceArgumentFunc;
import io.teris.rpc.internal.ServiceReturnTypeFunc;
import io.teris.rpc.internal.ServiceRouteFunc;


class RemoteInvocation implements InvocationHandler {

	private final Serializer serializer;

	private final Transporter transporter;

	private final Map<String, Deserializer> deserializerMap;

	private final Function<Method, String> serviceRouteFunc = new ServiceRouteFunc();

	private final BiFunction<Method, Object[], LinkedHashMap<String, Serializable>> serviceArgumentFunc = new ServiceArgumentFunc();

	private final Function<Method, Type> serviceReturnTypeFunc = new ServiceReturnTypeFunc();


	RemoteInvocation(Serializer serializer, Transporter transporter, Map<String, Deserializer> deserializerMap) {
		this.serializer = serializer;
		this.transporter = transporter;
		this.deserializerMap = deserializerMap;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

		CompletableFuture<? extends Serializable> promise = doInvoke(proxy, method, args);
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

	private <RS extends Serializable> CompletableFuture<RS> doInvoke(Object proxy, Method method, Object[] args) {
		try {
			Type type = serviceReturnTypeFunc.apply(method);

			LinkedHashMap<String, Serializable> payload = serviceArgumentFunc.apply(method, args);
			byte[] data = payload != null ? serializer.serialize(payload) : null;

			String routingKey = serviceRouteFunc.apply(method);
			CallerContext callerContext = getCallerContext(proxy);

			CompletableFuture<byte[]> promise =	transporter.transport(routingKey, callerContext, data);
			return promise.thenApply(res -> res == null ? null : serializer.deserializer().deserialize(res, type));
		}
		catch (Exception ex) {
			CompletableFuture<RS> res = new CompletableFuture<>();
			res.completeExceptionally(ex);
			return res;
		}
	}

	CallerContext getCallerContext(Object proxy) {
		// FIXME: add request Id and correlationId
		// FIXME: add getting this out out field of a getter
		if (proxy instanceof ContextAware) {
			return ((ContextAware) proxy).callerContext();
		}
		return new CallerContext();
	}
}


