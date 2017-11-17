/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.impl;

import java.util.concurrent.CompletableFuture;

import io.teris.rpc.AsyncService;
import io.teris.rpc.Context;


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
