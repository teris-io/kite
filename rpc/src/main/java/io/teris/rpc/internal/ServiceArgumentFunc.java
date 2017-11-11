/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.LinkedHashMap;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.teris.rpc.ExportName;


/**
 * Defines the function to extract and validate service method arguments used e.g.
 * by the client-side invocation handler to prepare arguments for transport.
 */
public class ServiceArgumentFunc
	implements BiFunction<Method, Object[], LinkedHashMap<String, Serializable>> {

	@Nullable
	@Override
	public LinkedHashMap<String, Serializable> apply(@Nonnull Method method, @Nullable Object[] args) {
		int paramCount = method.getParameterCount();
		if (args == null) {
			if (paramCount != 0) {
				throw new IllegalStateException(String.format("Internal error: Missing arguments for the service method '%s'",
					method.getName()));
			}
			return null;
		}
		if (paramCount != args.length) {
			throw new IllegalStateException(String.format("Internal error: Incorrect number of arguments for the service " +
				"method '%s', expected %d, found %d", method.getName(), Integer.valueOf(paramCount), Integer.valueOf(args.length)));
		}

		LinkedHashMap<String, Serializable> payload = new LinkedHashMap<>();
		for (int i = 0; i < paramCount; i++) {
			Parameter param = method.getParameters()[i];
			validate(method, param.getParameterizedType());
			String name = param.getName();
			if (!param.isNamePresent()) {
				ExportName exportName = param.getAnnotation(ExportName.class);
				if (exportName == null) {
					throw new IllegalArgumentException(String.format("Arguments of the service method '%s' must be annotated " +
						"with ExportName or compiler must preserve parameter names (-parameter)", method.getName()));
				}
				name = exportName.value();
			}
			if (name == null || "".equals(name.trim())) {
				throw new IllegalArgumentException("Service method argument names must not be empty");
			}
			payload.put(name, (Serializable) args[i]);
		}
		return payload;
	}

	private void validate(Method method, Type type) throws IllegalArgumentException {
		if (type instanceof WildcardType) {
			throw new IllegalArgumentException(String.format("Argument types of the service method '%s' must contain no " +
				"wildcards", method.getName()));
		}
		if (type instanceof Class) {
			Class<?> clazz = (Class<?>) type;
			if (!Serializable.class.isAssignableFrom(clazz)) {
				throw new IllegalArgumentException(String.format("Argument types of the service method '%s' must implement " +
					"Serializable or be void", method.getName()));
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
