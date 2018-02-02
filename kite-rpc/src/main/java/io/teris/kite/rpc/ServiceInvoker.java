/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc;

import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.teris.kite.Context;


/**
 * The client side transport layer interface. An implementing instance
 * is expected to be provided to the ServiceFactory builder,
 * so that service proxies get a way of performing a remote call over the wire.
 */
public interface ServiceInvoker {

	@Nonnull
	CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] data);
}
