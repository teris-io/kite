/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.rpc;

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

import io.teris.kite.Context;
import io.teris.kite.Name;
import io.teris.kite.Service;


final class ServiceProxyUtil {

	private ServiceProxyUtil() {}

	@Nonnull
	static String route(@Nonnull Method method) throws InvocationException {
		String serviceRoute = serviceRoute(method);
		String methodName = methodName(method);
		String res = sanitizeRoute(serviceRoute + "." + methodName);
		if ("".equals(res)) {
			String message = String.format("Empty route for %s.%s", method.getDeclaringClass().getSimpleName(), method.getName());
			throw new InvocationException(message);
		}
		return res;
	}

	private static String serviceRoute(Method method) throws InvocationException {
		Class<?> methodClass = method.getDeclaringClass();

		Service serviceAnnot = methodClass.getAnnotation(Service.class);
		if (serviceAnnot == null) {
			String message = String.format("Missing @%s annotation on %s", Service.class.getSimpleName(),
				method.getDeclaringClass().getSimpleName());
			throw new InvocationException(message);
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
	static Entry<Context, LinkedHashMap<String, Serializable>> arguments(@Nonnull Method method, @Nullable Object[] args) throws InvocationException {
		validateArgumentTypes(method);
		if (args == null || args.length < 1 || args[0] == null) {
			String message = String.format("First argument to %s.%s must be a (non-null) instance of %s",
				method.getDeclaringClass().getSimpleName(), method.getName(), Context.class.getSimpleName());
			throw new InvocationException(message);
		}
		Context context = (Context) args[0];
		if (method.getParameterCount() == 1) {
			return new SimpleEntry<>(context, null);
		}
		LinkedHashMap<String, Serializable> payload = new LinkedHashMap<>();
		for (int i = 1; i < method.getParameterCount(); i++) {
			Name nameAnnot = method.getParameters()[i].getAnnotation(Name.class);
			if (nameAnnot == null) {
				String message = String.format("After %s all parameters in %s.%s must be annotated with @%s",
					Context.class.getSimpleName(), method.getDeclaringClass().getSimpleName(), method.getName(), Name.class.getSimpleName());
				throw new InvocationException(message);
			}
			String name = nameAnnot.value();
			if ("".equals(name.trim())) {
				String message = String.format("Empty @%s annotation in %s.%s",
					Name.class.getSimpleName(), method.getDeclaringClass().getSimpleName(), method.getName());
				throw new InvocationException(message);
			}
			payload.put(name, (Serializable) args[i]);
		}
		return new SimpleEntry<>(context, payload);
	}

	static void validateArgumentTypes(Method method) throws InvocationException {
		if (method.getParameterCount() == 0 || !Context.class.isAssignableFrom(method.getParameters()[0].getType())) {
			String message = String.format("First parameters to %s.%s must be %s",
				method.getDeclaringClass().getSimpleName(), method.getName(), Context.class.getSimpleName());
			throw new InvocationException(message);
		}
		for (int i = 1; i < method.getParameterCount(); i++) {
			Parameter param = method.getParameters()[i];
			String paramName = param.getAnnotation(Name.class) != null ? param.getAnnotation(Name.class).value() : "unnamed";
			validateArgumentType(method, paramName, param.getParameterizedType());
		}
	}

	private static void validateArgumentType(Method method, String paramName, Type type) throws InvocationException {
		if (type instanceof WildcardType) {
			String message = String.format("Parameter types in %s.%s must contain no wildcards",
				method.getDeclaringClass().getSimpleName(), method.getName());
			throw new InvocationException(message);
		}
		if (type instanceof Class) {
			Class<?> clazz = (Class<?>) type;
			if (clazz.isArray()) {
				validateArgumentType(method, paramName, clazz.getComponentType());
			}
			else if (!Serializable.class.isAssignableFrom(clazz) && !clazz.isPrimitive() && !clazz.isEnum()) {
				String message = String.format("After %s all parameter types in %s.%s must implement Serializable: found parameter %s with type %s",
					Context.class.getSimpleName(), method.getDeclaringClass().getSimpleName(), method.getName(), paramName, clazz.getSimpleName());
				throw new InvocationException(message);
			}
		}
		if (type instanceof ParameterizedType) {
			ParameterizedType parametrizedType = (ParameterizedType) type;
			validateArgumentType(method, paramName, parametrizedType.getRawType());
			for (Type subtype : parametrizedType.getActualTypeArguments()) {
				validateArgumentType(method, paramName, subtype);
			}
		}
	}

	@Nonnull
	static Type returnType(@Nonnull Method method) throws InvocationException {
		Type returnType = method.getGenericReturnType();
		if (returnType instanceof ParameterizedType && CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
			returnType = ((ParameterizedType) returnType).getActualTypeArguments()[0];
		}
		validateReturnType(method, returnType);
		return returnType;
	}

	private static void validateReturnType(Method method, Type type) throws InvocationException {
		if (type instanceof WildcardType) {
			String message = String.format("Return type of %s.%s must contain no wildcards",
				method.getDeclaringClass().getSimpleName(), method.getName());
			throw new InvocationException(message);
		}
		if (type instanceof Class) {
			Class<?> clazz = (Class<?>) type;
			if (clazz.isArray()) {
				validateReturnType(method, clazz.getComponentType());
			}
			else {
				boolean isVoid = void.class.isAssignableFrom(clazz) || Void.class.isAssignableFrom(clazz);
				boolean isSerializable = Serializable.class.isAssignableFrom(clazz);
				boolean isPrimitive = clazz.isPrimitive() || clazz.isEnum();
				if (!isVoid && !isSerializable && !isPrimitive) {
					String message = String.format("Return type of %s.%s must implement Serializable or be void/Void (or a CompletableFuture thereof)",
						method.getDeclaringClass().getSimpleName(), method.getName());
					throw new InvocationException(message);
				}
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
