/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.amqp;


import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

import com.rabbitmq.client.ConnectionFactory;

import io.teris.rpc.ServiceDispatcher;


public interface AmqpServiceRouter {

	@Nonnull
	CompletableFuture<Void> close();

	@Nonnull
	static Builder builder(@Nonnull ConnectionFactory connectionFactory) {
		return new AmqpServiceRouterImpl.BuilderImpl(connectionFactory);
	}

	interface Builder {

		Builder exchangeName(String exchangeName);

		@Nonnull
		Router build() throws IOException, TimeoutException;
	}

	interface Router {

		@Nonnull
		Router route(@Nonnull ServiceDispatcher serviceDispatcher) throws IOException;

		AmqpServiceRouter start();
	}
}
