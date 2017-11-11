/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

import io.teris.rpc.Deserializer;
import io.teris.rpc.Serializer;
import io.teris.rpc.Transporter;
import io.teris.rpc.client.ServiceFactory.Router;


class ServiceFactoryImpl implements ServiceFactory, Router {

	private Map<Class<?>, ServiceFactory> factoryMap = new HashMap<>();

	@Override
	public <S> Router route(Class<S> serviceClass, S service) {
		factoryMap.put(serviceClass, new PassthroughServiceWrapper(service));
		return this;
	}

	@Override
	public <S> Router route(Class<S> serviceClass, ServiceFactory serviceFactory) {
		factoryMap.put(serviceClass, serviceFactory);
		return this;
	}

	@Override
	public <S> Router route(Class<S> serviceClass, Serializer serializer, Transporter transporter, Map<String, Deserializer> deserializerMap) {
		factoryMap.put(serviceClass, new RemoteServiceWrapper(serializer, transporter, deserializerMap));
		return this;
	}

	@Override
	public ServiceFactory instance() {
		factoryMap = Collections.unmodifiableMap(factoryMap);
		return this;
	}

	@Nonnull
	@Override
	public <S> S get(@Nonnull Class<S> serviceClass) throws InstantiationException {
		ServiceFactory serviceFactory = factoryMap.get(serviceClass);
		if (serviceFactory != null) {
			return serviceFactory.get(serviceClass);
		}
		throw new InstantiationException(String.format("No service factory bound for service %s",
			serviceClass.getSimpleName()));
	}
}
