/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.rpc;

import javax.annotation.Nonnull;


/**
 * Similar to TechnicalException, but can be thrown on the client side if
 * the route is not reachable (provided the transport reports unreachable
 * routes, e.g. HTTP does and JMS does not).
 */
public class NotFoundException extends RuntimeException {

	private static final long serialVersionUID = -7944545553830906074L;

	/**
	 * Constructs an InvocationException with the provided detail message.
	 *
	 * @param message the detailed exception message.
	 */
	public NotFoundException(@Nonnull String message) {
		super(message);
	}

	/**
	 * Constructs an InvocationException with the given detail message and cause.
	 *
	 * @param message the detailed exception message.
	 * @param cause the exception cause.
	 */
	public NotFoundException(@Nonnull String message, @Nonnull Throwable cause) {
		super(String.format("%s [caused by %s%s]", message, cause.getClass().getSimpleName(),
			cause.getMessage() != null ? ": " + cause.getMessage() : ""));
		setStackTrace(cause.getStackTrace());
	}
}
