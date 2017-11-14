/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;

import io.teris.rpc.InstantiationException;
import io.teris.rpc.ServiceException;


public final class ServiceValidator {

	private ServiceValidator() {}

	/**
	 * Validates a service definition.
	 * @throws ServiceException on any invalid definition logic.
	 */
	public static <S> void validate(@Nonnull Class<S> serviceClass) throws InstantiationException {


		if (!serviceClass.isInterface()) {
			throw new InstantiationException(serviceClass, "service definition must be an interface");
		}
		Method[] methods = serviceClass.getDeclaredMethods();
		if (methods.length < 1) {
			throw new InstantiationException(serviceClass, "service definition must declare at least one service method");
		}
		Set<String> foundMethodNames = new HashSet<>();
		for (Method method : methods) {
			if (foundMethodNames.contains(method.getName())) {
				throw new InstantiationException(serviceClass, String.format("method overloading is not permitted (violation on method: %s",
					method.getName()));
			}
			try {
				ProxyMethodUtil.route(method);
				ProxyMethodUtil.validateArgumentTypes(method);
				ProxyMethodUtil.returnType(method);
			}
			catch (RuntimeException | ServiceException ex) {
				throw new InstantiationException(serviceClass, ex);
			}
			foundMethodNames.add(method.getName());
		}
	}
}
