/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.amqp;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

import com.rabbitmq.client.ConnectionFactory;

import io.teris.rpc.ServiceInvoker;


public interface AmqpServiceInvoker extends ServiceInvoker {

	AmqpServiceInvoker start();

	@Nonnull
	CompletableFuture<Void> close();

	@Nonnull
	static Builder builder(@Nonnull ConnectionFactory connectionFactory) {
		return new AmqpServiceInvokerImpl.BuilderImpl(connectionFactory);
	}

	interface Builder {

		Builder exchangeName(String exchangeName);

		@Nonnull
		AmqpServiceInvoker build() throws IOException, TimeoutException;
	}
}
