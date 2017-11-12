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
import java.util.AbstractMap.SimpleEntry;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.teris.rpc.Context;
import io.teris.rpc.Name;
import io.teris.rpc.Service;


public final class ProxyMethodUtil {

	private ProxyMethodUtil() {}

	@Nonnull
	public static String route(@Nonnull Method method) {
		String serviceRoute = serviceRoute(method);
		String methodName = methodName(method);
		String res = sanitizeRoute(serviceRoute + "." + methodName);
		if ("".equals(res)) {
			throw new IllegalArgumentException(String.format("Empty root for %s.%s",
				method.getDeclaringClass().getSimpleName(), method.getName()));
		}
		return res;
	}

	private static String serviceRoute(Method method) {
		Class<?> methodClass = method.getDeclaringClass();

		Service serviceAnnot = methodClass.getAnnotation(Service.class);
		if (serviceAnnot == null) {
			throw new IllegalStateException(String.format("Service '%s' must be annotation with @%s",
				methodClass.getSimpleName(), Service.class.getSimpleName()));
		}

		String serviceRoute = methodClass.getCanonicalName();
		if (serviceRoute.endsWith(Service.class.getSimpleName())) {
			serviceRoute = serviceRoute.substring(0, serviceRoute.lastIndexOf(Service.class.getSimpleName()));
		}

		String value = serviceAnnot.value().trim();
		String replace = serviceAnnot.replace().trim();

		if (!"".equals(replace)) {
			serviceRoute = serviceRoute.replace(replace, value);
		}
		else if (!"".equals(value)) {
			serviceRoute = value;
		}
		return serviceRoute;
	}

	private static String methodName(Method method) {
		Name nameAnnot = method.getAnnotation(Name.class);
		if (nameAnnot != null) {
			return nameAnnot.value().trim();
		}
		return method.getName();
	}

	private static String sanitizeRoute(String route) {
		StringBuilder sb = new StringBuilder();
		AtomicBoolean initialized = new AtomicBoolean(false);
		String[] parts = route.replaceAll("\\$", ".").split("\\.");
		for (String part : parts) {
			String elm = part.trim();
			if (!"".equals(elm)) {
				if (initialized.getAndSet(true)) {
					sb.append(".");
				}
				sb.append(elm);
			}
		}
		return sb.toString().toLowerCase();
	}

	@Nonnull
	public static Entry<Context, LinkedHashMap<String, Serializable>> arguments(@Nonnull Method method, @Nullable Object[] args) {
		validateArgumentTypes(method);
		if (args == null || args.length < 1 || args[0] == null) {
			throw new IllegalArgumentException(String.format("First argument of the service method '%s' must be %s",
				method.getName(), Context.class.getSimpleName()));
		}
		Context context = (Context) args[0];
		LinkedHashMap<String, Serializable> payload = new LinkedHashMap<>();
		for (int i = 1; i < method.getParameterCount(); i++) {
			Name nameAnnot = method.getParameters()[i].getAnnotation(Name.class);
			if (nameAnnot == null) {
				throw new IllegalArgumentException(String.format("Arguments of the service method '%s' must be annotated " +
					"with Name or compiler must preserve parameter names (-parameter)", method.getName()));
			}
			String name = nameAnnot.value();
			if ("".equals(name.trim())) {
				throw new IllegalArgumentException(String.format("Argument names of the service method '%s' must not be empty",
					method.getName()));
			}
			payload.put(name, (Serializable) args[i]);
		}
		return new SimpleEntry<>(context, payload);
	}

	static void validateArgumentTypes(Method method) throws IllegalArgumentException {
		if (method.getParameterCount() == 0 || !Context.class.isAssignableFrom(method.getParameters()[0].getType())) {
			throw new IllegalArgumentException(String.format("First argument of the service method '%s' must be %s",
				method.getName(), Context.class.getSimpleName()));
		}
		for (int i = 1; i < method.getParameterCount(); i++) {
			Parameter param = method.getParameters()[i];
			validateArgumentType(method, param.getParameterizedType());
		}
	}

	private static void validateArgumentType(Method method, Type type) throws IllegalArgumentException {
		if (type instanceof WildcardType) {
			throw new IllegalArgumentException(String.format("Argument types of the service method '%s' must contain no " +
				"wildcards", method.getName()));
		}
		if (type instanceof Class) {
			if (!Serializable.class.isAssignableFrom((Class<?>) type)) {
				throw new IllegalArgumentException(String.format("Argument types of the service method '%s' must implement " +
					"Serializable or be void", method.getName()));
			}
		}
		if (type instanceof ParameterizedType) {
			ParameterizedType parametrizedType = (ParameterizedType) type;
			validateArgumentType(method, parametrizedType.getRawType());
			for (Type subtype : parametrizedType.getActualTypeArguments()) {
				validateArgumentType(method, subtype);
			}
		}
	}

	@Nonnull
	public static Type returnType(@Nonnull Method method) {
		Type returnType = method.getGenericReturnType();
		if (returnType instanceof ParameterizedType && CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
			returnType = ((ParameterizedType) returnType).getActualTypeArguments()[0];
		}
		validateReturnType(method, returnType);
		return returnType;
	}

	private static void validateReturnType(Method method, Type type) throws IllegalArgumentException {
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
			validateReturnType(method, parametrizedType.getRawType());
			for (Type subtype : parametrizedType.getActualTypeArguments()) {
				validateReturnType(method, subtype);
			}
		}
	}
}
