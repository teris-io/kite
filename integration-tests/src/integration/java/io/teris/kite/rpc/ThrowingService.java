/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc;

import java.util.concurrent.CompletableFuture;

import io.teris.kite.Context;
import io.teris.kite.Service;


@Service
public interface ThrowingService {

	void boomNow(Context context);

	CompletableFuture<Void> boomThen(Context context);
}
