/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.teris.rpc.context.CallerContext;


public interface Transporter {

	@Nonnull
	CompletableFuture<byte[]> transport(@Nonnull String routingKey, @Nonnull CallerContext callerContext, @Nullable byte[] data);
}
