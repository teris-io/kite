/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.lang.reflect.Method;
import javax.annotation.Nonnull;


/**
 * Thrown by the invocation layer in case of technical issues with the invocation of
 * a remote service call: declaration, serialization, transport, deserialization error
 * etc.
 */
public class InvocationException extends TechnicalException {

	static final long serialVersionUID = 234908723405672L;

	/**
	 * Constructs a InvocationException composing a detail message from the method
	 * class and name.
	 */
	public InvocationException(@Nonnull Method method) {
		super(String.format("Failed to invoke %s.%s", method.getDeclaringClass().getSimpleName(),
			method.getName()));
	}

	/**
	 * Constructs a InvocationException composing a detail message from the method
	 * class and name and appending a user message at the end.
	 */
	public InvocationException(@Nonnull Method method, @Nonnull String message) {
		super(String.format("Failed to invoke %s.%s: %s", method.getDeclaringClass().getSimpleName(),
			method.getName(), message));
	}


	/**
	 * Constructs a InvocationException composing a detail message from the method
	 * class and name and attaching the cause.
	 */
	public InvocationException(@Nonnull Method method, @Nonnull Throwable cause) {
		super(String.format("Failed to invoke %s.%s", method.getDeclaringClass().getSimpleName(),
			method.getName()), cause);
	}
}
