/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.context;


import javax.annotation.Nonnull;


public interface ServiceContextFactory {

	static ServiceContextFactory instance() {
		return GenericServiceContextFactory.instance();
	}

	@Nonnull
	<S> S copy(@Nonnull  S instance, @Nonnull  CallerContext context) throws InstantiationException;
}
