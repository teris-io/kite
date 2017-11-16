/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import javax.annotation.Nonnull;


/**
 * Thrown by the invocation layer in response to an internal technical exception. This
 * exception is safe for the remote transport as it does not preserve the instance
 * reference of the original exception thrown.
 */
public class InvocationException extends RuntimeException {

	static final long serialVersionUID = 4563467345675L;

	/**
	 * Constructs an InvocationException with the provided detail message.
	 *
	 * @param message the detailed exception message.
	 */
	public InvocationException(@Nonnull String message) {
		super(message);
	}

	/**
	 * Constructs an InvocationException with the given detail message and cause.
	 *
	 * @param message the detailed exception message.
	 * @param cause the exception cause.
	 */
	public InvocationException(@Nonnull String message, @Nonnull Throwable cause) {
		super(String.format("%s [caused by %s%s]", message, cause.getClass().getSimpleName(),
			cause.getMessage() != null ? ": " + cause.getMessage() : ""));
		setStackTrace(cause.getStackTrace());
	}

	/**
	 * Constructs an instance dispatching from an instance of {@link ExceptionDataHolder}
	 *
	 * @param message the original exception message.
	 * @param stackTrace the original stack trace.
	 */
	InvocationException(@Nonnull String message, @Nonnull StackTraceElement... stackTrace) {
		super(message);
		setStackTrace(stackTrace);
	}
}
