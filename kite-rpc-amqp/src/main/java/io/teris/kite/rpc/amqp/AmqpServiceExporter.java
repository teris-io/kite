/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc.amqp;


import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

import com.rabbitmq.client.ConnectionFactory;

import io.teris.kite.rpc.ServiceExporter;
import io.teris.kite.rpc.amqp.AmqpServiceExporterImpl.ConfiguratorImpl;


public interface AmqpServiceExporter {

	@Nonnull
	AmqpServiceExporter export(@Nonnull ServiceExporter serviceExporter) throws IOException;

	@Nonnull
	AmqpServiceExporter start();

	@Nonnull
	CompletableFuture<Void> close();

	@Nonnull
	static Configurator connectionFactory(@Nonnull ConnectionFactory connectionFactory) {
		return new ConfiguratorImpl(connectionFactory);
	}

	interface Configurator {

		@Nonnull
		AmqpServiceExporter requestExchange(String requestExchange) throws IOException, TimeoutException;
	}
}
