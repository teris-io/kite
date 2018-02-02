/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.rpc.jms;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;


abstract class JmsServiceBase {

	static final String JMS_ROUTE = "JMS_ROUTE";

	static final String ACCESS_DENIED = "Access denied: ";

	final Connection connection;

	final Session requestSession;

	final Session responseSession;

	JmsServiceBase(Connection connection, Session requestSession, Session responseSession) {
		this.connection = connection;
		this.requestSession = requestSession;
		this.responseSession = responseSession;
	}

	@Nonnull
	public CompletableFuture<Void> close() {
		CompletableFuture<Void> result = new CompletableFuture<>();
		CompletableFuture.runAsync(() -> {
			try {
				requestSession.close();
				connection.close();
				responseSession.close();
				result.complete(null);
			}
			catch (JMSException ex) {
				result.completeExceptionally(ex);
			}
		});
		return result;
	}
}
