/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.impl;

import io.teris.rpc.SyncService;
import io.teris.rpc.Context;


public class SyncServiceImpl implements SyncService {

	private final String id;

	public SyncServiceImpl(String id) {
		this.id = id;
	}

	@Override
	public Double plus(Context context, Double a, Double b) {
		context.put("invoked-by", id);
		return Double.valueOf(a.doubleValue() + b.doubleValue());
	}

	@Override
	public Double minus(Context context, Double a, Double b) {
		context.put("invoked-by", id);
		return Double.valueOf(a.doubleValue() - b.doubleValue());
	}
}
