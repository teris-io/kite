/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.service;

import java.lang.reflect.Proxy;
import java.util.Map;
import javax.annotation.Nonnull;

import io.teris.rpc.Deserializer;
import io.teris.rpc.Serializer;
import io.teris.rpc.Transporter;


public class RemoteServiceFactory implements ServiceFactory {

	private final RemoteInvocationHandler invocationHandler;

	public RemoteServiceFactory(Serializer serializer, Transporter transporter, Map<String, Deserializer> deserializerMap) {
		invocationHandler = new RemoteInvocationHandler(serializer, transporter, deserializerMap);
	}

	@Nonnull
	@Override
	public <S> S get(@Nonnull Class<S> serviceClass) {
		try {
			@SuppressWarnings("unchecked")
			S res = (S) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ serviceClass }, invocationHandler);
			return res;
		}
		catch (ClassCastException | SecurityException ex) {
			throw new IllegalArgumentException(String.format("Failed to create an get of %s", serviceClass.getSimpleName()), ex);
		}
	}
}
