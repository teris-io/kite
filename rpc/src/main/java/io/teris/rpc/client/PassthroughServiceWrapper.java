/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.client;

import java.lang.reflect.Proxy;
import javax.annotation.Nonnull;


class PassthroughServiceWrapper implements ServiceFactory {

	private final Object service;

	PassthroughServiceWrapper(Object service) {
		this.service = service;
	}

	@Nonnull
	@Override
	public <S> S get(@Nonnull Class<S> serviceClass) throws InstantiationException {
		if (!serviceClass.isAssignableFrom(service.getClass())) {
			throw new InstantiationException(String.format("Cannot create a pass-through proxy for %s, " +
				"expecting %s",	serviceClass.getSimpleName(), service.getClass().getSimpleName()));
		}
		try {
			@SuppressWarnings("unchecked")
			S res = (S) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ serviceClass },
				new PassthroughInvocation(service));
			return res;
		}
		catch (RuntimeException ex) {
			throw new InstantiationException(String.format("Failed to create a service proxy for %s: %s",
				serviceClass.getSimpleName(), ex.getMessage()));
		}
	}
}
