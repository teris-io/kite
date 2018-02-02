/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc.impl;

import java.util.concurrent.CompletableFuture;

import io.teris.kite.Context;
import io.teris.kite.rpc.AsyncService;


public class AsyncServiceImpl implements AsyncService {

	private final String id;

	public AsyncServiceImpl(String id) {
		this.id = id;
	}

	@Override
	public CompletableFuture<Double> plus(Context context, Double a, Double b) {
		context.put("invoked-by", id);
		return CompletableFuture.supplyAsync(() -> Double.valueOf(a.doubleValue() + b.doubleValue()));
	}

	@Override
	public CompletableFuture<Double> minus(Context context, Double a, Double b) {
		context.put("invoked-by", id);
		return CompletableFuture.supplyAsync(() -> Double.valueOf(a.doubleValue() - b.doubleValue()));
	}
}
