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
import io.teris.rpc.InvocationException;
import io.teris.rpc.Name;
import io.teris.rpc.Service;


public final class ProxyMethodUtil {

	private ProxyMethodUtil() {}

	@Nonnull
	public static String route(@Nonnull Method method) throws InvocationException {
		String serviceRoute = serviceRoute(method);
		String methodName = methodName(method);
		String res = sanitizeRoute(serviceRoute + "." + methodName);
		if ("".equals(res)) {
			throw new InvocationException(method, "empty route");
		}
		return res;
	}

	private static String serviceRoute(Method method) throws InvocationException {
		Class<?> methodClass = method.getDeclaringClass();

		Service serviceAnnot = methodClass.getAnnotation(Service.class);
		if (serviceAnnot == null) {
			throw new InvocationException(method, String.format("missing @%s annotation", Service.class.getSimpleName()));
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
	public static Entry<Context, LinkedHashMap<String, Serializable>> arguments(@Nonnull Method method, @Nullable Object[] args) throws InvocationException {
		validateArgumentTypes(method);
		if (args == null || args.length < 1 || args[0] == null) {
			throw new InvocationException(method, String.format("first argument must be an instance of %s", Context.class.getSimpleName()));
		}
		Context context = (Context) args[0];
		LinkedHashMap<String, Serializable> payload = new LinkedHashMap<>();
		for (int i = 1; i < method.getParameterCount(); i++) {
			Name nameAnnot = method.getParameters()[i].getAnnotation(Name.class);
			if (nameAnnot == null) {
				throw new InvocationException(method, String.format("all arguments except for the first one must be annotated with @%s",
					Name.class.getSimpleName()));
			}
			String name = nameAnnot.value();
			if ("".equals(name.trim())) {
				throw new InvocationException(method, String.format("all arguments must have non-empty @%s annotation",
					Name.class.getSimpleName()));
			}
			payload.put(name, (Serializable) args[i]);
		}
		return new SimpleEntry<>(context, payload);
	}

	static void validateArgumentTypes(Method method) throws InvocationException {
		if (method.getParameterCount() == 0 || !Context.class.isAssignableFrom(method.getParameters()[0].getType())) {
			throw new InvocationException(method, String.format("first argument must be an instance of %s", Context.class.getSimpleName()));
		}
		for (int i = 1; i < method.getParameterCount(); i++) {
			Parameter param = method.getParameters()[i];
			validateArgumentType(method, param.getParameterizedType());
		}
	}

	private static void validateArgumentType(Method method, Type type) throws InvocationException {
		if (type instanceof WildcardType) {
			throw new InvocationException(method, "argument types must contain no wildcards");
		}
		if (type instanceof Class) {
			if (!Serializable.class.isAssignableFrom((Class<?>) type)) {
				throw new InvocationException(method, "arguments must implement Serializable");
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
	public static Type returnType(@Nonnull Method method) throws InvocationException {
		Type returnType = method.getGenericReturnType();
		if (returnType instanceof ParameterizedType && CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
			returnType = ((ParameterizedType) returnType).getActualTypeArguments()[0];
		}
		validateReturnType(method, returnType);
		return returnType;
	}

	private static void validateReturnType(Method method, Type type) throws InvocationException {
		if (type instanceof WildcardType) {
			throw new InvocationException(method, "return type must contain no wildcards");
		}
		if (type instanceof Class) {
			Class<?> clazz = (Class<?>) type;
			boolean isVoid = void.class.isAssignableFrom(clazz) || Void.class.isAssignableFrom(clazz);
			boolean isSerializable = Serializable.class.isAssignableFrom(clazz);
			if (!isVoid && !isSerializable) {
				throw new InvocationException(method, "return type must implement Serializable or be void/Void");
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
