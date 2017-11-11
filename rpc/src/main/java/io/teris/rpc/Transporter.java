/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.util.concurrent.CompletableFuture;

import io.teris.rpc.context.CallerContext;


public interface Transporter {

	CompletableFuture<byte[]> transport(String routingKey, CallerContext callerContext, byte[] data);
}
