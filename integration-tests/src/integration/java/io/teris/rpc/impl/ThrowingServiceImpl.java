/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.impl;

import java.util.concurrent.CompletableFuture;

import io.teris.rpc.Context;
import io.teris.rpc.ThrowingService;


public class ThrowingServiceImpl implements ThrowingService {

	private final String id;

	public ThrowingServiceImpl(String id) {
		this.id = id;
	}

	@Override
	public void boomNow(Context context) {
		context.put("invoked-by", id);
		throw new IllegalStateException("BOOM");
	}

	@Override
	public CompletableFuture<Void> boomThen(Context context) {
		context.put("invoked-by", id);
		return CompletableFuture.runAsync(() -> {
			throw new IllegalStateException("BOOM");
		});
	}
}
