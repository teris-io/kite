/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.jms;

import java.util.Enumeration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import io.teris.rpc.Context;
import io.teris.rpc.ServiceDispatcher;


class JmsServiceRouterImpl implements JmsServiceRouter, JmsServiceRouter.Router {

	static final String JMS_ROUTE = "JMS_ROUTE";


	private final Connection connection;

	private final Session requestSession;

	private final Topic requestTopic;

	private final Session responseSession;

	private final Map<String, ServiceDispatcher> serviceDispatchers = new ConcurrentHashMap<>();


	JmsServiceRouterImpl(ConnectionFactory connectionFactory, String topicName) throws JMSException {
		connection = connectionFactory.createConnection();

		requestSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
		requestTopic = requestSession.createTopic(topicName);

		responseSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
	}

	static class BuilderImpl implements Builder {

		private final ConnectionFactory connectionFactory;

		private String topicName;

		BuilderImpl(ConnectionFactory connectionFactory) {
			this.connectionFactory = connectionFactory;
		}

		@Override
		public Builder topicName(String topicName) {
			this.topicName = topicName;
			return this;
		}

		@Nonnull
		@Override
		public Router build() throws JMSException {
			return new JmsServiceRouterImpl(connectionFactory, topicName);
		}
	}

	@Nonnull
	@Override
	public Router route(@Nonnull ServiceDispatcher serviceDispatcher) throws JMSException {
		StringBuilder sb = new StringBuilder(JMS_ROUTE);
		sb.append(" IN (");
		AtomicBoolean found = new AtomicBoolean(false);
		for (String route :serviceDispatcher.dispatchRoutes()) {
			serviceDispatchers.put(route, serviceDispatcher);

			if (found.getAndSet(true)) {
				sb.append(",");
			}
			sb.append("'");
			sb.append(route);
			sb.append("'");
		}
		sb.append(")");

		String filter = found.get() ? sb.toString() : null; // FIXME should never be null as this will consume all, add UUID?

		requestSession
			.createConsumer(requestTopic, filter)
			.setMessageListener(new RequestConsumer(serviceDispatchers, responseSession));
		return this;
	}


	static class RequestConsumer implements MessageListener {

		private final Map<String, ServiceDispatcher> serviceDispatchers;

		private final Session responseSession;

		RequestConsumer(Map<String, ServiceDispatcher> serviceDispatchers, Session responseSession) {
			// do not copy content, assign reference
			this.serviceDispatchers = serviceDispatchers;
			this.responseSession = responseSession;
		}

		@Override
		public void onMessage(Message message) {
			Destination replyTo;
			try {
				replyTo = message.getJMSReplyTo();
				if (replyTo == null) {
					message.acknowledge();
					throw new JMSException("reply desitnation unavailable"); // FIXME
				}
			}
			catch (JMSException ex) {
				// FIXME log
				return;
			}

			try {
				if (message instanceof BytesMessage) {
					String route = message.getStringProperty(JMS_ROUTE);
					ServiceDispatcher serviceDispatcher = serviceDispatchers.get(route);
					if (serviceDispatcher == null) {
						throw new JMSException(String.format("no service to %s", route));
					}
					Context context = new Context();
					Enumeration e = message.getPropertyNames();
					while (e.hasMoreElements()) {
						String name = String.valueOf(e.nextElement());
						context.put(name, message.getStringProperty(name)); // FIXME not null
					}

					BytesMessage byteMessage = (BytesMessage) message;
					byte[] data = null;
					if (byteMessage.getBodyLength() > 0) {
						data = new byte[(int) byteMessage.getBodyLength()];
						byteMessage.readBytes(data);
					}
					serviceDispatcher.call(route, context, data)
						.whenComplete((entry, t) -> {
							if (t != null) {
								respond(message, t);
							}
							else if (entry == null) {
								respond(message, new NullPointerException("empty response"));
							}
							else {
								respond(message, entry.getKey(), entry.getValue());
							}
						});
				}
				else {
					throw new JMSException("wrong message type");
				}
			}
			catch (JMSException ex) {
				respond(message, ex);
			}
		}

		private void respond(Message message, Throwable t) {
			try {
				TextMessage responseMessage = responseSession.createTextMessage(t.getMessage()); // FIXME message
				responseMessage.setJMSCorrelationID(message.getJMSCorrelationID());
				responseSession.createProducer(message.getJMSReplyTo()).send(responseMessage);
				message.acknowledge();
			}
			catch (JMSException ex) {
				// FIXME implement with retries, log
			}
		}

		private void respond(Message message, Context context, byte[] data) {
			try {
				BytesMessage responseMessage = responseSession.createBytesMessage();
				responseMessage.setJMSCorrelationID(message.getJMSCorrelationID());
				responseMessage.writeBytes(data);
				for (Entry<String, String> entry: context.entrySet()) {
					responseMessage.setStringProperty(entry.getKey(), entry.getValue());
				}
				responseSession.createProducer(message.getJMSReplyTo()).send(responseMessage);
				message.acknowledge();
			}
			catch (JMSException ex) {
				// FIXME implement with retries, log
			}
		}
	}

	@Override
	public JmsServiceRouter start() throws JMSException {
		connection.start();
		return this;
	}

	@Nonnull
	@Override
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
