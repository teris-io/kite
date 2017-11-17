/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.jms;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

import io.teris.rpc.ServiceInvoker;


public interface JmsServiceInvoker extends ServiceInvoker {

	JmsServiceInvoker start() throws JMSException;

	@Nonnull
	CompletableFuture<Void> close();

	@Nonnull
	static Builder builder(@Nonnull ConnectionFactory connectionFactory) {
		return new JmsServiceInvokerImpl.BuilderImpl(connectionFactory);
	}

	interface Builder {

		Builder topicName(String topicName);

		@Nonnull
		JmsServiceInvoker build() throws JMSException;
	}
}
