/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.service;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;


public class PassthroughServiceFactory implements ServiceFactory {

	private final Map<Class<?>, ServiceFactory> factoryMap = new HashMap<>();

	private final Map<Class<?>, Object> instanceMap = new HashMap<>();

	public <S> PassthroughServiceFactory bind(Class<S> serviceClass, ServiceFactory serviceFactory) {
		factoryMap.put(serviceClass, serviceFactory);
		return this;
	}

	public <S> PassthroughServiceFactory bind(Class<S> serviceClass, S service) {
		instanceMap.put(serviceClass, service);
		return this;
	}

	@Nonnull
	@Override
	public <S> S get(@Nonnull Class<S> serviceClass) throws InstantiationException {
		ServiceFactory serviceFactory = factoryMap.get(serviceClass);
		if (serviceFactory != null) {
			return serviceFactory.get(serviceClass);
		}
		@SuppressWarnings("unchecked")
		S service = (S) instanceMap.get(serviceClass);
		if (service != null) {
			return service;
		}
		throw new InstantiationException(String.format("No factory or get binding found for class %s", serviceClass.getSimpleName()));
	}
}
