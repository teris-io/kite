/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

@Service
public interface SyncService {

	Double plus(Context context, @Name("a") Double a, @Name("b") Double b);

	Double minus(Context context, @Name("a") Double a, @Name("b") Double b);
}
