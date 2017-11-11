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
		validate(returnType);
		return returnType;
	}

	private void validate(Type type) throws IllegalArgumentException {
		if (type instanceof WildcardType) {
			throw new IllegalArgumentException("Service return value types must contain no wildcards");
		}
		if (type instanceof Class) {
			Class<?> clazz = (Class<?>) type;
			if (!void.class.isAssignableFrom(clazz) && !Void.class.isAssignableFrom(clazz) && !Serializable.class.isAssignableFrom(clazz)) {
				throw new IllegalArgumentException("Service return value types must implement serializable or be void");
			}
		}
		if (type instanceof ParameterizedType) {
			ParameterizedType parametrizedType = (ParameterizedType) type;
			validate(parametrizedType.getRawType());
			for (Type subtype: parametrizedType.getActualTypeArguments()) {
				validate(subtype);
			}
		}
	}
}
