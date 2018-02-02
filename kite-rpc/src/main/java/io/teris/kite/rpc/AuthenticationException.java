/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.rpc;

import javax.annotation.Nonnull;


/**
 * Similar to TechnicalException, but should be used in preprocessor to
 * identify authentication problems.
 */
public class AuthenticationException extends RuntimeException {

	private static final long serialVersionUID = 1545013728297877453L;

	/**
	 * Constructs an InvocationException with the provided detail message.
	 *
	 * @param message the detailed exception message.
	 */
	public AuthenticationException(@Nonnull String message) {
		super(message);
	}

	/**
	 * Constructs an InvocationException with the given detail message and cause.
	 *
	 * @param message the detailed exception message.
	 * @param cause the exception cause.
	 */
	public AuthenticationException(@Nonnull String message, @Nonnull Throwable cause) {
		super(String.format("%s [caused by %s%s]", message, cause.getClass().getSimpleName(),
			cause.getMessage() != null ? ": " + cause.getMessage() : ""));
		setStackTrace(cause.getStackTrace());
	}
}
