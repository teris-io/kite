/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.jms;


import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

import io.teris.rpc.ServiceDispatcher;


public interface JmsServiceRouter {


	@Nonnull
	CompletableFuture<Void> close();

	@Nonnull
	static Builder builder(@Nonnull ConnectionFactory connectionFactory) {
		return new JmsServiceRouterImpl.BuilderImpl(connectionFactory);
	}

	interface Builder {

		Builder topicName(String topicName);

		@Nonnull
		Router build() throws JMSException;
	}

	interface Router {

		@Nonnull
		Router route(@Nonnull ServiceDispatcher serviceDispatcher) throws JMSException;

		JmsServiceRouter start() throws JMSException;
	}
}
