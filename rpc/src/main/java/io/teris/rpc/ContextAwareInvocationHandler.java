/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import javax.annotation.Nonnull;


abstract class ContextAwareInvocationHandler<S, P extends ContextAwareInvocationHandler> implements InvocationHandler, ContextAware<P> {

	final Class<S> serviceClass;

	Context context;

	ContextAwareInvocationHandler(@Nonnull Class<S> serviceClass, @Nonnull Context context) {
		this.serviceClass = serviceClass;
		this.context = context;
	}

	public S get() throws InstantiationException {
		try {
			@SuppressWarnings("unchecked")
			S res = (S) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ serviceClass, ContextAware.class }, this);
			return res;
		}
		catch (RuntimeException ex) {
			throw new InstantiationException(String.format("Failed to create a service proxy for %s: %s",
				serviceClass.getSimpleName(), ex.getMessage()));
		}
	}

	protected abstract Object serviceInvoke(Object proxy, Method method, Object[] args) throws Throwable;

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if (ContextAware.class.isAssignableFrom(method.getDeclaringClass())) {
			if (Context.class.isAssignableFrom(method.getReturnType())
				&& method.getParameterCount() == 0) {
				return getContext();
			}
			else if (method.getParameterCount() == 1
				&& args[0] instanceof Context
				&& void.class.isAssignableFrom(method.getReturnType())) {
				setContext((Context) args[0]);
				return null;
			}
			else if (method.getParameterCount() == 1
				&& args[0] instanceof Context
				&& !void.class.isAssignableFrom(method.getReturnType())) {
				return newInContext((Context) args[0]).get();
			}
			else {
				throw new IllegalStateException(String.format("Method %s is attributed to %s, but no matching signature found",
					method.getName(), ContextAware.class.getSimpleName()));
			}
		}
		return serviceInvoke(proxy, method, args);
	}

	@Nonnull
	@Override
	public Context getContext() {
		return context;
	}

	@Override
	public void setContext(@Nonnull Context context) {
		this.context = context;
	}
}


