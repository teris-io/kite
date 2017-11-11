/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.context;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import javax.annotation.Nonnull;


class GenericServiceContextFactory implements ServiceContextFactory {

	static class Holder {
		static final ServiceContextFactory instance = new GenericServiceContextFactory();
	}

	static ServiceContextFactory instance() {
		return Holder.instance;
	}

	@Nonnull
	@Override
	public <S> S copy(@Nonnull S instance, @Nonnull CallerContext context) throws InstantiationException {
		try {
			@SuppressWarnings("unchecked")
			Constructor<S> constr =
				(Constructor<S>) instance.getClass().getDeclaredConstructor(instance.getClass(), CallerContext.class);
			return constr.newInstance(instance, context);
		}
		catch (IllegalAccessException | InvocationTargetException | ClassCastException | SecurityException ex) {
			throw new InstantiationException(String.format("Failed to copy get of class %s", instance.getClass().getSimpleName()));
		}
		catch (NoSuchMethodException ex) {
			return instance;
		}
	}
}
