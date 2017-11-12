/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;


import javax.annotation.Nonnull;


/**
 * ServiceFactory defines a utility to acquire client-side service implementations or
 * service proxies implementing invocation via either remote or local calls.
 */
public interface ServiceFactory {

	/**
	 * Creates an instance of a service proxy for a local or remote invocation.
	 *
	 * @param serviceClass the interface class to route.
	 * @throws InstantiationException when no instance can be constructes for any reason.
	 */
	@Nonnull
	<S> S get(@Nonnull Class<S> serviceClass) throws InstantiationException;
}
