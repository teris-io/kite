/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import java.util.concurrent.CompletableFuture;


public interface RpcInvoker {
	CompletableFuture<RpcResponse> invoke(RpcRequest message);
}
