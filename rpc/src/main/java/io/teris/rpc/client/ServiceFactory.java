/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.client;


import java.util.Map;
import javax.annotation.Nonnull;

import io.teris.rpc.Deserializer;
import io.teris.rpc.Serializer;
import io.teris.rpc.Transporter;


public interface ServiceFactory {

	interface Router {

		<S> Router route(Class<S> serviceClass, S service);

		<S> Router route(Class<S> serviceClass, ServiceFactory serviceFactory);

		<S> Router route(Class<S> serviceClass, Serializer serializer, Transporter transporter, Map<String, Deserializer> deserializerMap);

		ServiceFactory instance();
	}

	static Router routingFactory() {
		return new ServiceFactoryImpl();
	}

	static ServiceFactory remoteFactory(Serializer serializer, Transporter transporter, Map<String, Deserializer> deserializerMap) {
		return new RemoteServiceWrapper(serializer, transporter, deserializerMap);
	}

	@Nonnull
	<S> S get(@Nonnull Class<S> serviceClass) throws InstantiationException;
}
