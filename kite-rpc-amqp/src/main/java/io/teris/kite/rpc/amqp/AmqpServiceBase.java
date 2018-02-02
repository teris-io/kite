/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.rpc.amqp;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;


abstract class AmqpServiceBase {

	static final String MSGTYPE_REQUEST = "request";

	static final String MSGTYPE_RESPONSE = "response";

	static final String MSGTYPE_ERROR = "error";

	static final String MSGTYPE_ERROR_AUTH = "error:auth";

	static final String MSGTYPE_ERROR_NOTFOUND = "error:not-found";

	final Connection connection;

	final Channel channel;


	AmqpServiceBase(Connection connection, Channel channel) {
		this.connection = connection;
		this.channel = channel;
	}

	@Nonnull
	public CompletableFuture<Void> close() {
		CompletableFuture<Void> result = new CompletableFuture<>();
		CompletableFuture.runAsync(() -> {
			try {
				channel.close();
				connection.close();
				result.complete(null);
			}
			catch (IOException | TimeoutException ex) {
				result.completeExceptionally(ex);
			}
		});
		return result;
	}
}
