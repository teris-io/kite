/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

public final class ResponseFields {

	private ResponseFields() {}

	public static final String PAYLOAD = "payload";

	public static final String EXCEPTION = "exception";

	@Deprecated
	public static final String ERROR_MESSAGE = "errorMessage";
}
