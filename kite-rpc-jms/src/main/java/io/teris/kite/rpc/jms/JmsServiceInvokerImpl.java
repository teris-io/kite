/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc.jms;

import java.util.AbstractMap.SimpleEntry;
import java.util.Enumeration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.teris.kite.Context;
import io.teris.kite.rpc.AuthenticationException;
import io.teris.kite.rpc.TechnicalException;


class JmsServiceInvokerImpl extends JmsServiceBase implements JmsServiceInvoker {

	private static final Logger log = LoggerFactory.getLogger(JmsServiceInvoker.class);

	private final Topic requestTopic;

	private final MessageProducer requestProducer;

	private final Queue responseQueue;

	private final Map<String, Entry<Context, CompletableFuture<Entry<Context, byte[]>>>> requestStore = new ConcurrentHashMap<>();


	JmsServiceInvokerImpl(ConnectionFactory connectionFactory, String topicName) throws JMSException {
		this(connectionFactory.createConnection(), topicName);

	}

	JmsServiceInvokerImpl(Connection connection, String topicName) throws JMSException {
		super(connection, connection.createSession(false, Session.CLIENT_ACKNOWLEDGE),
			connection.createSession(false, Session.AUTO_ACKNOWLEDGE));

		requestTopic = requestSession.createTopic(topicName);
		requestProducer = requestSession.createProducer(requestTopic);
		requestProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

		responseQueue = responseSession.createTemporaryQueue();
		responseSession
			.createConsumer(responseQueue)
			.setMessageListener(new ResponseReceiver(responseQueue.toString(), requestStore));
	}

	static class ConfiguratorImpl implements Configurator {

		private final ConnectionFactory connectionFactory;

		ConfiguratorImpl(ConnectionFactory connectionFactory) {
			this.connectionFactory = connectionFactory;
		}

		@Nonnull
		@Override
		public JmsServiceInvoker requestTopic(String topicName) throws JMSException {
			return new JmsServiceInvokerImpl(connectionFactory, topicName);
		}
	}

	@Nonnull
	@Override
	public CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] outgoing) {
		String correlationId = context.get(Context.X_REQUEST_ID_KEY);
		CompletableFuture<Entry<Context, byte[]>> promise = new CompletableFuture<>();
		try {
			Objects.requireNonNull(correlationId, "Context contains no " + Context.X_REQUEST_ID_KEY);
			BytesMessage message = requestSession.createBytesMessage();
			message.setJMSCorrelationID(correlationId);
			message.setJMSReplyTo(responseQueue);
			for (Entry<String, String> entry: context.entrySet()) {
				message.setStringProperty(entry.getKey(), entry.getValue());
			}
			if (outgoing != null) {
				message.writeBytes(outgoing);
			}
			message.setStringProperty(JMS_ROUTE, route);
			message.setStringProperty(Context.CONTENT_TYPE_KEY, context.get(Context.CONTENT_TYPE_KEY));
			requestStore.put(correlationId, new SimpleEntry<>(context, promise));
			requestProducer.send(requestTopic, message);
			log.debug("client sent request {} to '{}'", correlationId, requestTopic.getTopicName());
		}
		catch (Exception ex) {
			if (correlationId != null) {
				requestStore.remove(correlationId);
			}
			promise.completeExceptionally(ex);
		}
		return promise;
	}

	static class ResponseReceiver implements MessageListener {

		private final String responseQueueName;

		private final Map<String, Entry<Context, CompletableFuture<Entry<Context, byte[]>>>> requestStore;

		ResponseReceiver(String responseQueueName, Map<String, Entry<Context, CompletableFuture<Entry<Context, byte[]>>>> requestStore) {
			this.responseQueueName = responseQueueName;
			this.requestStore = requestStore;
		}

		@Override
		public void onMessage(Message message) {
			CompletableFuture<Entry<Context, byte[]>> promise = null;
			String correlationId = null;
			try {
				correlationId = message.getJMSCorrelationID();

				Entry<Context, CompletableFuture<Entry<Context, byte[]>>> entry = requestStore.remove(correlationId);
				if (entry == null || entry.getValue() == null) {
					throw new JMSException("No request information found");
				}

				log.debug("client received response for {} on  '{}'", correlationId, responseQueueName);

				promise = entry.getValue();
				if (message instanceof BytesMessage) {
					Context context = entry.getKey();
					Enumeration e = message.getPropertyNames();
					while (e.hasMoreElements()) {
						String name = String.valueOf(e.nextElement());
						String property = message.getStringProperty(name);
						if (property != null) {
							context.put(name, property);
						}
					}

					byte[] data = null;
					BytesMessage byteMessage = (BytesMessage) message;
					if (byteMessage.getBodyLength() > 0) {
						data = new byte[(int) byteMessage.getBodyLength()];
						byteMessage.readBytes(data);
					}
					promise.complete(new SimpleEntry<>(context, data));
				}
				else if (message instanceof TextMessage) {
					String errorMessage = ((TextMessage) message).getText();
					errorMessage = errorMessage == null ? "unknown error" : errorMessage;
					if (errorMessage.startsWith(ACCESS_DENIED)) {
						promise.completeExceptionally(new AuthenticationException(errorMessage.replace(ACCESS_DENIED, "")));
					}
					else {
						promise.completeExceptionally(new TechnicalException(errorMessage));
					}
				}
				else {
					promise.completeExceptionally(new TechnicalException("Unsupported message type"));
				}
			}
			catch (JMSException ex) {
				if (promise != null) {
					promise.completeExceptionally(ex);
				}
				else {
					log.error(String.format("client %s from '%s' failed to process", correlationId, responseQueueName), ex);
				}
			}
		}
	}

	@Nonnull
	@Override
	public JmsServiceInvoker start() throws JMSException {
		connection.start();
		return this;
	}
}
