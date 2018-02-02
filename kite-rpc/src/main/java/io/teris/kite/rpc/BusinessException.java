/*
 * Copyright (c) teris.io & Oleg Sklyar, 2018. All rights reserved
 */

package io.teris.kite.rpc;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


/**
 * Thrown by the invocation layer in response to an exception in the remote service call,
 * that is triggered by the business logic execution. This exception is safe for the
 * remote transport as it does not preserve the instance reference of the original
 * exception thrown.
 *
 * This exception is not intended to be instantiated outside of the rpc infrastructure,
 * therefore, package local constructors only.
 */
public class BusinessException extends RuntimeException {

	private static final long serialVersionUID = 351065990620300587L;

	/**
	 * Constructs a BusinessException with the provided cause.
	 *
	 * @param cause the original exception.
	 */
	BusinessException(@Nonnull Throwable cause) {
		super(String.format("%s%s",
			cause.getClass().getSimpleName(),
			cause.getMessage() != null ? ": " + cause.getMessage() : ""));
		setStackTrace(cause.getStackTrace());
	}

	/**
	 * Constructs an instance dispatching from an instance of {@link ExceptionDataHolder}
	 *
	 * @param message the original exception message.
	 * @param stackTrace the original stack trace.
	 */
	BusinessException(@Nullable String message, @Nonnull StackTraceElement... stackTrace) {
		super(message);
		setStackTrace(stackTrace);
	}
}
