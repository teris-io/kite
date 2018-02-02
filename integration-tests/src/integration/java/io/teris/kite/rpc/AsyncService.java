/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc;

import java.util.concurrent.CompletableFuture;

import io.teris.kite.Context;
import io.teris.kite.Name;
import io.teris.kite.Service;


@Service
public interface AsyncService {

	CompletableFuture<Double> plus(Context context, @Name("a") Double a, @Name("b") Double b);

	CompletableFuture<Double> minus(Context context, @Name("a") Double a, @Name("b") Double b);
}
