/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.context;

public interface ContextAware {

	CallerContext callerContext();

	void callerContext(CallerContext callerContext);
}
