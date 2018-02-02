/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc.jms;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

import io.teris.kite.rpc.ServiceInvoker;
import io.teris.kite.rpc.jms.JmsServiceInvokerImpl.ConfiguratorImpl;


public interface JmsServiceInvoker extends ServiceInvoker {

	@Nonnull
	JmsServiceInvoker start() throws JMSException;

	@Nonnull
	CompletableFuture<Void> close();

	@Nonnull
	static Configurator connectionFactory(@Nonnull ConnectionFactory connectionFactory) {
		return new ConfiguratorImpl(connectionFactory);
	}

	interface Configurator {

		@Nonnull
		JmsServiceInvoker requestTopic(String requestTopic) throws JMSException;
	}
}
