/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import javax.annotation.Nonnull;


/**
 * Thrown by the invocation layer in response to an exception in the actual remote
 * execution of a service call.
 */
public class BusinessException extends ServiceException {

	static final long serialVersionUID = 23489765234056L;

	/**
	 * Constructs a BusinessException with the provided detail message.
	 */
	public BusinessException(@Nonnull String message) {
		super(message);
	}

	/**
	 * Constructs a BusinessException with the provided detail message and the cause.
	 */
	public BusinessException(@Nonnull String message, @Nonnull Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs a BusinessException with the provided cause.
	 */
	public BusinessException(@Nonnull Throwable cause) {
		super(cause);
	}
}
