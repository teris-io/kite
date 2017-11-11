/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.service;


import javax.annotation.Nonnull;


public interface ServiceFactory {

	@Nonnull
	<S> S get(@Nonnull Class<S> serviceClass) throws InstantiationException;
}
