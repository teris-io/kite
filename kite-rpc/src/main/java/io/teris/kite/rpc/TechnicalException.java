/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.rpc;

import javax.annotation.Nonnull;


/**
 * Similar to InvocationException, but public and instead of being transported as is
 * it will be converted to the transport specific error notification (thus
 * resulting in e.g. 4xx HTTP codes with the HTTP transport etc).
 */
public class TechnicalException extends RuntimeException {

	private static final long serialVersionUID = -6793878671436341755L;

	/**
	 * Constructs an InvocationException with the provided detail message.
	 *
	 * @param message the detailed exception message.
	 */
	public TechnicalException(@Nonnull String message) {
		super(message);
	}

	/**
	 * Constructs an InvocationException with the given detail message and cause.
	 *
	 * @param message the detailed exception message.
	 * @param cause the exception cause.
	 */
	public TechnicalException(@Nonnull String message, @Nonnull Throwable cause) {
		super(String.format("%s [caused by %s%s]", message, cause.getClass().getSimpleName(),
			cause.getMessage() != null ? ": " + cause.getMessage() : ""));
		setStackTrace(cause.getStackTrace());
	}
}
