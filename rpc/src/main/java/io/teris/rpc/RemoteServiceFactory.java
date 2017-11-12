/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.lang.reflect.Proxy;
import java.util.Map;
import javax.annotation.Nonnull;


/**
 * Defines a factory to generate service interface proxies to be executed remotely.
 */
public class RemoteServiceFactory implements ServiceFactory {

	private final Serializer serializer;

	private final Transporter transporter;

	private final Map<String, Deserializer> deserializerMap;

	public RemoteServiceFactory(Serializer serializer, Transporter transporter, Map<String, Deserializer> deserializerMap) {
		this.serializer = serializer;
		this.transporter = transporter;
		this.deserializerMap = deserializerMap;
	}

	@Nonnull
	@Override
	public <S> S get(@Nonnull Class<S> serviceClass) throws InstantiationException {
		try {
			@SuppressWarnings("unchecked")
			S res = (S) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ serviceClass, ContextAware.class },
				new RemoteProxy<>(serviceClass, new Context(), serializer, transporter, deserializerMap));
			return res;
		}
		catch (RuntimeException ex) {
			throw new InstantiationException(String.format("Failed to create a service proxy for %s: %s",
				serviceClass.getSimpleName(), ex.getMessage()));
		}
	}
}
