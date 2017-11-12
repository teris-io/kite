/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import java.lang.reflect.Method;
import javax.annotation.Nonnull;


public final class ServiceValidator {

	private ServiceValidator() {}

	public static <S> void validate(@Nonnull Class<S> serviceClass) throws IllegalArgumentException {
		if (!serviceClass.isInterface()) {
			throw new IllegalArgumentException(String.format("Service definition '%s' must be an interface",
				serviceClass.getSimpleName()));
		}
		Method[] methods = serviceClass.getDeclaredMethods();
		if (methods.length < 1) {
			throw new IllegalArgumentException(String.format("Service definition '%s' must define at least one method",
				serviceClass.getSimpleName()));
		}
		for (Method method : methods) {
			ProxyMethodUtil.route(method);
			ProxyMethodUtil.validateArgumentTypes(method);
			ProxyMethodUtil.returnType(method);
		}
	}
}
