/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import javax.annotation.Nonnull;


/**
 * Defines the base class for all service related exceptions.
 */
public class ServiceException extends Exception {

	/**
	 * Constructs a ServiceException with the specified detail message.
	 */
	public ServiceException(@Nonnull String message) {
		super(message);
	}

	/**
	 * Constructs a ServiceException with the specified detailed message and the cause.
	 * In order to avoid deserialization issues in case the cause exception class is
	 * not available on the caller side, only the stacktrace and the message are preserved
	 * from the cause, but not the instance.
	 */
	public ServiceException(@Nonnull String message, @Nonnull Throwable cause) {
		super(String.format("%s [caused by %s%s]",
			message,
			cause.getClass().getSimpleName(),
			cause.getMessage() != null ? ": " + cause.getMessage() : ""));
		setStackTrace(cause.getStackTrace());
	}

	/**
	 * Constructs a ServiceException taking the detail message from the cause and its
	 * stacktrace. In order to avoid deserialization issues in case the cause exception
	 * class is not available on the caller side, only the stacktrace and the message
	 * are preserved from the cause, but not the instance.
	 */
	public ServiceException(@Nonnull Throwable cause) {
		super(String.format("%s%s",
			cause.getClass().getSimpleName(),
			cause.getMessage() != null ? ": " + cause.getMessage() : ""));
	}
}
