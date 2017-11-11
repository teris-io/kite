/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.client;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import javax.annotation.Nonnull;

import io.teris.rpc.Deserializer;
import io.teris.rpc.Serializer;
import io.teris.rpc.Transporter;


class RemoteServiceWrapper implements ServiceFactory {

	private final InvocationHandler invocationHandler;

	RemoteServiceWrapper(Serializer serializer, Transporter transporter, Map<String, Deserializer> deserializerMap) {
		invocationHandler = new RemoteInvocation(serializer, transporter, deserializerMap);
	}

	@Nonnull
	@Override
	public <S> S get(@Nonnull Class<S> serviceClass) throws InstantiationException {
		try {
			@SuppressWarnings("unchecked")
			S res = (S) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ serviceClass }, invocationHandler);
			return res;
		}
		catch (RuntimeException ex) {
			throw new InstantiationException(String.format("Failed to create a service proxy for %s: %s",
				serviceClass.getSimpleName(), ex.getMessage()));
		}
	}
}
