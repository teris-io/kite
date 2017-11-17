/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.util.concurrent.CompletableFuture;


@Service
public interface ThrowingService {

	void boomNow(Context context);

	CompletableFuture<Void> boomThen(Context context);
}
