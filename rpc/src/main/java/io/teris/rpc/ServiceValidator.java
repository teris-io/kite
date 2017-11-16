/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;

import io.teris.rpc.InvocationException;
import io.teris.rpc.internal.ProxyMethodUtil;


/**
 * Validates a service definition.
 */
public final class ServiceValidator {

	private ServiceValidator() {}

	/**
	 * Validates a service definition.
	 *
	 * @param serviceClass the interface class to validate.
	 * @throws InvocationException on any invalid definition logic.
	 */
	public static <S> void validate(@Nonnull Class<S> serviceClass) throws InvocationException {
		if (!serviceClass.isInterface()) {
			String message = String.format("Service definition %s must be an interface", serviceClass.getSimpleName());
			throw new InvocationException(message);
		}
		Method[] methods = serviceClass.getDeclaredMethods();
		if (methods.length < 1) {
			String message = String.format("Service definition %s must declare at least one method", serviceClass.getSimpleName());
			throw new InvocationException(message);
		}
		Set<String> foundMethodNames = new HashSet<>();
		for (Method method : methods) {
			if (foundMethodNames.contains(method.getName())) {
				String message = String.format("Service definition %s must not declare any overloaded methods (violation: %s)",
					serviceClass.getSimpleName(), method.getName());
				throw new InvocationException(message);
			}
			ProxyMethodUtil.route(method);
			ProxyMethodUtil.validateArgumentTypes(method);
			ProxyMethodUtil.returnType(method);
			foundMethodNames.add(method.getName());
		}
	}
}
