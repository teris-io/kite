/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


/**
 * Thrown by the invocation layer in response to an exception in the actual remote
 * execution of a service call.
 */
public class BusinessException extends ServiceException {

	static final long serialVersionUID = 23489765234056L;

	/**
	 * Constructs a BusinessException with the provided cause.
	 */
	public BusinessException(@Nonnull Throwable cause) {
		super(cause);
	}

	/**
	 * Constructs an instance dispatching from an instance of {@link ExceptionDataHolder}
	 */
	BusinessException(@Nullable String message, @Nonnull StackTraceElement... stackTrace) {
		super(message, stackTrace);
	}
}
