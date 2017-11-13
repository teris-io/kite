/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import java.lang.reflect.Method;
import javax.annotation.Nonnull;

import io.teris.rpc.InstantiationException;
import io.teris.rpc.ServiceException;


public final class ServiceValidator {

	private ServiceValidator() {}

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
}
