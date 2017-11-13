/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import javax.annotation.Nonnull;


/**
 * Thrown by the invocation layer when an instance of a service proxy cannot be constructed.
 */
public class InstantiationException extends ServiceException {

	static final long serialVersionUID = 480967234506456L;

	/**
	 * Constructs a ServiceFactoryException to indicate a failure of constructing a proxy
	 * for the given service class.
	 */
	public <S> InstantiationException(@Nonnull Class<S> serviceClass, @Nonnull String message) {
		super(String.format("Failed to construct an instance of service %s: %s",
			serviceClass.getSimpleName(), message));
	}

	/**
	 * Constructs a ServiceFactoryException to indicate a failure of constructing a proxy
	 * for the given service class.
	 */
	public <S> InstantiationException(@Nonnull Class<S> serviceClass, @Nonnull Throwable cause) {
		super(String.format("Failed to construct an instance of service %s", serviceClass.getSimpleName()), cause);
	}
}
