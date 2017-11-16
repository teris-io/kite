/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


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
	 * Constructs an instance dispatching from an instance of {@link ExceptionDataHolder}
	 */
	TechnicalException(@Nullable String message, @Nonnull StackTraceElement... stackTrace) {
		super(message, stackTrace);
	}
}
