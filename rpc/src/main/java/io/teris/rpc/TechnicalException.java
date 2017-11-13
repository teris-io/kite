/**
 * Copyright (c) Profidata AG 2017
 */
package io.teris.rpc;

import javax.annotation.Nonnull;


/**
 * Thrown by the invocation layer in response to an internal technical exception of the
 * invocation layer.
 */
public class TechnicalException extends ServiceException {

	static final long serialVersionUID = 4563467345675L;

	/**
	 * Constructs a TechnicalException with the provided detail message.
	 */
	public TechnicalException(@Nonnull String message) {
		super(message);
	}

	/**
	 * Constructs a TechnicalException with the provided detail message and the cause.
	 */
	public TechnicalException(@Nonnull String message, @Nonnull Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs a TechnicalException with the provided cause.
	 */
	public TechnicalException(@Nonnull Throwable cause) {
		super(cause);
	}
}
