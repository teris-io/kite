/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;


public interface Requester {

	@Nonnull
	CompletableFuture<Entry<Context, byte[]>> execute(@Nonnull String route, @Nonnull Context context, @Nullable byte[] data);
}
