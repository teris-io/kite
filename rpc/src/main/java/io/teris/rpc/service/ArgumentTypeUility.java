/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.service;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.LinkedHashMap;


class ArgumentTypeUility {

	LinkedHashMap<String, Serializable> extractArguments(Method method, Object[] args) {
		LinkedHashMap<String, Serializable> payload = new LinkedHashMap<>();
		for (int i = 0; i < method.getParameterCount(); i++) {
			validate(args[i].getClass());
			String name = method.getParameters()[i].getName();
			payload.put(name, (Serializable) args[i]);
		}
		return payload;
	}

	private void validate(Type type) throws IllegalArgumentException {
		if (type instanceof WildcardType) {
			throw new IllegalArgumentException("Service argument types must contain no wildcards");
		}
		if (type instanceof Class) {
			Class<?> clazz = (Class<?>) type;
			if (!Serializable.class.isAssignableFrom(clazz)) {
				throw new IllegalArgumentException("Service argument types must implement serializable or be void");
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
