/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc.jms;


import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

import io.teris.kite.rpc.ServiceExporter;
import io.teris.kite.rpc.jms.JmsServiceExporterImpl.ConfiguratorImpl;


public interface JmsServiceExporter {

	@Nonnull
	JmsServiceExporter export(@Nonnull ServiceExporter serviceExporter) throws JMSException;

	@Nonnull
	JmsServiceExporter start() throws JMSException;

	@Nonnull
	CompletableFuture<Void> close();

	@Nonnull
	static Configurator connectionFactory(@Nonnull ConnectionFactory connectionFactory) {
		return new ConfiguratorImpl(connectionFactory);
	}

	interface Configurator {

		@Nonnull
		JmsServiceExporter requestTopic(String requestTopic) throws JMSException;
	}
}
