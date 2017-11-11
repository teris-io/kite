/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved 
 */

package io.teris.rpc.internal;

/**
 * Signals that a serialization exception has occurred.
 */
public class RpcException extends Exception {

	static final long serialVersionUID = 9478561856962310L;

	/**
	 * Constructs a {@code RpcException} with the specified detail message.
	 */
	public RpcException(String message) {
		super(message);
	}

	/**
	 * Constructs a {@code RpcException} with the specified detail message
	 * and cause.
	 */
	public RpcException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs a {@code RpcException} with the specified cause.
	 */
	public RpcException(Throwable cause) {
		super(cause);
	}
}
