/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import javax.annotation.Nonnull;


public interface ContextAware<S> {

	@Nonnull
	Context getContext();

	void setContext(@Nonnull Context context);

	@Nonnull
	S newInContext(@Nonnull Context context);
}
