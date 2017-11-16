/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.io.Serializable;
import javax.annotation.Nonnull;


public class ExceptionDataHolder implements Serializable {

	private static final long serialVersionUID = 23489765234056L;

	public String message = "";

	public String type = InvocationException.class.getSimpleName();

	public StackTraceElement[] stackTrace = new StackTraceElement[]{};

	// required for deserialization
	protected ExceptionDataHolder() {}

	ExceptionDataHolder(@Nonnull InvocationException ex) {
		message = ex.getMessage() != null ? ex.getMessage() : "Technical exception";
		type = ex.getClass().getSimpleName();
		stackTrace = ex.getStackTrace();
	}

	ExceptionDataHolder(@Nonnull BusinessException ex) {
		message = ex.getMessage() != null ? ex.getMessage() : "Business exception";
		type = ex.getClass().getSimpleName();
		stackTrace = ex.getStackTrace();
	}

	ExceptionDataHolder(@Nonnull Throwable t) {
		this(new InvocationException("Unexpected invocation exception", t));
	}

	@Nonnull
	RuntimeException exception() {
		if (BusinessException.class.getSimpleName().equalsIgnoreCase(type)) {
			return new BusinessException(message, stackTrace);
		}
		return new InvocationException(message, stackTrace);
	}
}
