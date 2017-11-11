/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.http;

import javax.annotation.Nonnull;

import io.teris.rpc.service.ServiceFactory;


public class VertxWebServiceExporter implements ServiceFactory {

	@Nonnull
	@Override
	public <S> S get(@Nonnull Class<S> serviceClass) throws InstantiationException {
		return null;
	}
}
