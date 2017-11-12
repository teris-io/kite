/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nonnull;

import io.teris.rpc.internal.ServiceArgumentFunc;
import io.teris.rpc.internal.ServiceReturnTypeFunc;
import io.teris.rpc.internal.ServiceRouteFunc;


/**
 * Defines an InvocationHandler that proxies service calls to a remote implementation
 * serializing the request using the serializer, transporting the data using the transport
 * and deserializing the resposnse using a deserializer from the deserializerMap.
 */
class RemoteProxy<S> extends ContextAwareInvocationHandler<S, RemoteProxy> {

	private final Serializer serializer;

	private final Transporter transporter;

	private final Map<String, Deserializer> deserializerMap;

	private final Function<Method, String> serviceRouteFunc = new ServiceRouteFunc();

	private final BiFunction<Method, Object[], LinkedHashMap<String, Serializable>> serviceArgumentFunc = new ServiceArgumentFunc();

	private final Function<Method, Type> serviceReturnTypeFunc = new ServiceReturnTypeFunc();

	RemoteProxy(Class<S> serviceClass, Context context, Serializer serializer, Transporter transporter, Map<String, Deserializer> deserializerMap) {
		super(serviceClass, context);
		this.serializer = serializer;
		this.transporter = transporter;
		this.deserializerMap = deserializerMap;
	}

	@Nonnull
	@Override
	public RemoteProxy newInContext(@Nonnull Context context) {
		return new RemoteProxy<>(serviceClass, context, serializer, transporter, deserializerMap);
	}

	@Override
	protected Object serviceInvoke(Object proxy, Method method, Object[] args) throws Throwable {
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
			Type type = serviceReturnTypeFunc.apply(method);

			LinkedHashMap<String, Serializable> payload = serviceArgumentFunc.apply(method, args);
			byte[] data = payload != null ? serializer.serialize(payload) : null;

			String routingKey = serviceRouteFunc.apply(method);

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


