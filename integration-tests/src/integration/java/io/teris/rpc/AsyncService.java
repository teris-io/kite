/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.util.concurrent.CompletableFuture;


@Service
public interface AsyncService {

	CompletableFuture<Double> plus(Context context, @Name("a") Double a, @Name("b") Double b);

	CompletableFuture<Double> minus(Context context, @Name("a") Double a, @Name("b") Double b);
}
