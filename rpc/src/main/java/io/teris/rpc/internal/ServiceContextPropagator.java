/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.BiFunction;

import io.teris.rpc.Context;
import io.teris.rpc.ContextAware;


public class ServiceContextPropagator<S> implements BiFunction<S, Context, S>  {

	@Override
	public S apply(S service, Context context) {
		return execute(service, context);
	}

	private <T> T execute(T service, Context context) {
		try {
			if (service instanceof ContextAware) {
				@SuppressWarnings("unchecked")
				T instance = (T) ((ContextAware) service).newInContext(context);
				return copyChildren(instance, context);
			}
			Constructor<?> constr = null;
			try {
				constr = service.getClass().getConstructor(service.getClass(), Context.class);
			}
			catch (NoSuchMethodException ex) {
				// ignore
			}
			if (constr != null) {
				@SuppressWarnings("unchecked")
				T instance = (T) constr.newInstance(service, context);
				return copyChildren(instance, context);
			}
			try {
				constr = service.getClass().getConstructor(service.getClass());
			}
			catch (NoSuchMethodException ex) {
				// ignore
			}
			if (constr != null) {
				for (Field field: service.getClass().getDeclaredFields()) {
					if (Context.class.isAssignableFrom(field.getType()) && isWritable(field)) {
						@SuppressWarnings("unchecked")
						T instance = (T) constr.newInstance(service);
						field.set(instance, context);
						return copyChildren(instance, context);
					}
				}
				for (Method method : service.getClass().getDeclaredMethods()) {
					if (method.getParameterCount() == 1
						&& isCallable(method)
						&& Context.class.isAssignableFrom(method.getParameters()[0].getType())
						&& void.class.isAssignableFrom(method.getReturnType())) {
						@SuppressWarnings("unchecked")
						T instance = (T) constr.newInstance(service);
						method.invoke(instance, context);
						return copyChildren(instance, context);
					}
				}
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to copy service and its children in new context", ex);
		}
		return service;
	}

	private <T> T copyChildren(T service, Context context) throws Exception {
		for (Field field: service.getClass().getDeclaredFields()) {
			if (!Context.class.isAssignableFrom(field.getType()) && isWritable(field)) {
				Object subService = field.get(service);
				Object newService = execute(subService, context);
				if (newService != subService) {
					field.set(service, newService);
				}
			}
		}
		return service;
	}

	private boolean isWritable(Field field) {
		int mod = field.getModifiers();
		return (Modifier.isPublic(mod) || Modifier.isProtected(mod)) && !Modifier.isFinal(mod) && !Modifier.isStatic(mod);
	}

	private boolean isCallable(Method method) {
		int mod = method.getModifiers();
		return (Modifier.isPublic(mod) || Modifier.isProtected(mod)) && !Modifier.isStatic(mod);
	}
}
