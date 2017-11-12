/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nonnull;

import io.teris.rpc.internal.ServiceArgumentFunc;
import io.teris.rpc.internal.ServiceContextPropagator;
import io.teris.rpc.internal.ServiceReturnTypeFunc;
import io.teris.rpc.internal.ServiceRouteFunc;


/**
 * Defines an InvocationHandler that proxies service calls to a remote implementation
 * serializing the request using the serializer, transporting the data using the transport
 * and deserializing the resposnse using a deserializer from the deserializerMap.
 */
class PassthroughProxy<S> extends ContextAwareInvocationHandler<S, PassthroughProxy> {

	private final Function<Method, String> serviceRouteFunc = new ServiceRouteFunc();

	private final BiFunction<Method, Object[], LinkedHashMap<String, Serializable>> serviceArgumentFunc = new ServiceArgumentFunc();

	private final Function<Method, Type> serviceReturnTypeFunc = new ServiceReturnTypeFunc();

	private final BiFunction<S, Context, S> serviceContextPropagator = new ServiceContextPropagator<>();

	private final S service;

	private final boolean withValidation;

	PassthroughProxy(Class<S> serviceClass, S service, boolean withValidation, Context context) {
		super(serviceClass, context);
		this.service = service;
		this.withValidation = withValidation;
	}

	@Nonnull
	@Override
	public PassthroughProxy newInContext(@Nonnull Context context) {
		S newService = serviceContextPropagator.apply(service, context);
		return new PassthroughProxy<>(serviceClass, newService, withValidation, context);
	}

	@Override
	protected Object serviceInvoke(Object proxy, Method method, Object[] args) throws Throwable {
		try {
			if (withValidation) {
				serviceReturnTypeFunc.apply(method);
				serviceArgumentFunc.apply(method, args);
				serviceRouteFunc.apply(method);
			}
			return method.invoke(service, args);
		}
		catch (Exception ex) {
			if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
				CompletableFuture<? extends Serializable> promise = new CompletableFuture<>();
				promise.completeExceptionally(ex);
				return promise;
			}
			throw ex;
		}
	}
}


