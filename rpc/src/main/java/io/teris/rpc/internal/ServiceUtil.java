/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nonnull;

import io.teris.rpc.InstantiationException;
import io.teris.rpc.ServiceException;


/**
 * Provides utilities to work with service proxies.
 */
public final class ServiceUtil {

	private ServiceUtil() {}

	/**
	 * Validates a service definition.
	 * @throws ServiceException on any invalid definition logic.
	 */
	public static <S> void validate(@Nonnull Class<S> serviceClass) throws ServiceException {
		if (!serviceClass.isInterface()) {
			throw new InstantiationException(serviceClass, "service definition must be an interface");
		}
		Method[] methods = serviceClass.getDeclaredMethods();
		if (methods.length < 1) {
			throw new InstantiationException(serviceClass, "service definition must declare at least one service method");
		}
		for (Method method : methods) {
			ProxyMethodUtil.route(method);
			ProxyMethodUtil.validateArgumentTypes(method);
			ProxyMethodUtil.returnType(method);
		}
	}

	@Nonnull
	public static <S> Map<String, Entry<Object, Method>> mapServiceMethods(@Nonnull Class<S> serviceClass, @Nonnull S service) throws ServiceException {
		validate(serviceClass);

		// FIXME
		return Collections.emptyMap();
	}
}
