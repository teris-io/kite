/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.client;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.teris.rpc.internal.ServiceArgumentFunc;
import io.teris.rpc.internal.ServiceReturnTypeFunc;
import io.teris.rpc.internal.ServiceRouteFunc;


class PassthroughInvocation implements InvocationHandler {

	private final Function<Method, String> serviceRouteFunc = new ServiceRouteFunc();

	private final BiFunction<Method, Object[], LinkedHashMap<String, Serializable>> serviceArgumentFunc = new ServiceArgumentFunc();

	private final Function<Method, Type> serviceReturnTypeFunc = new ServiceReturnTypeFunc();

	private final Object service;

	PassthroughInvocation(Object service) {
		this.service = service;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		try {
			// runtime validation of service declaration correctness
			serviceReturnTypeFunc.apply(method);
			serviceArgumentFunc.apply(method, args);
			serviceRouteFunc.apply(method);

			return method.invoke(service, args);
		}
		catch (Exception ex) {
			if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
				CompletableFuture<?> promise = new CompletableFuture<>();
				promise.completeExceptionally(ex);
				return promise;
			}
			throw ex;
		}
	}
}


