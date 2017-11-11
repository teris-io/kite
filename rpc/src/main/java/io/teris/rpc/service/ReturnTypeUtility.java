/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.service;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.concurrent.CompletableFuture;


class ReturnTypeUtility {

	Type extractReturnType(Method method) {
		Type returnType = method.getGenericReturnType();
		if (returnType instanceof ParameterizedType && CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
			returnType = ((ParameterizedType) returnType).getActualTypeArguments()[0];
		}
		validate(method, returnType);
		return returnType;
	}

	private void validate(Method method, Type type) throws IllegalArgumentException {
		if (type instanceof WildcardType) {
			throw new IllegalArgumentException(String.format("Return type of the service method '%s' must contain no " +
				"wildcards", method.getName()));
		}
		if (type instanceof Class) {
			Class<?> clazz = (Class<?>) type;
			boolean isVoid = void.class.isAssignableFrom(clazz) || Void.class.isAssignableFrom(clazz);
			boolean isSerializable = Serializable.class.isAssignableFrom(clazz);
			if (!isVoid && !isSerializable) {
				throw new IllegalArgumentException(String.format("Return type of the service method '%s' must implement " +
					"Serializable or be void/Void", method.getName()));
			}
		}
		if (type instanceof ParameterizedType) {
			ParameterizedType parametrizedType = (ParameterizedType) type;
			validate(method, parametrizedType.getRawType());
			for (Type subtype: parametrizedType.getActualTypeArguments()) {
				validate(method, subtype);
			}
		}
	}
}
