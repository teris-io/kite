/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.jms;

import java.util.AbstractMap.SimpleEntry;
import java.util.Enumeration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import io.teris.rpc.Context;
import io.teris.rpc.InvocationException;


class JmsServiceInvokerImpl implements JmsServiceInvoker {

	private final Connection connection;

	private final Session requestSession;

	private final Topic requestTopic;

	private final MessageProducer requestProducer;

	private final Session responseSession;

	private final Queue responseQueue;

	private final Map<String, Entry<Context, CompletableFuture<Entry<Context, byte[]>>>> requestStore = new ConcurrentHashMap<>();


	JmsServiceInvokerImpl(ConnectionFactory connectionFactory, String topicName) throws JMSException {
		connection = connectionFactory.createConnection();

		requestSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
		requestTopic = requestSession.createTopic(topicName);
		requestProducer = requestSession.createProducer(requestTopic);
		// requestProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
		// producer.setTimeToLive(10000000);

		responseSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		responseQueue = responseSession.createTemporaryQueue();
		responseSession
			.createConsumer(responseQueue)
			.setMessageListener(new ResponseReceiver(requestStore));
	}

	static class BuilderImpl implements JmsServiceInvoker.Builder {

		private final ConnectionFactory connectionFactory;

		private String topicName = "RPC";

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
		public JmsServiceInvoker build() throws JMSException {
			return new JmsServiceInvokerImpl(connectionFactory, topicName);
		}
	}

	@Nonnull
	@Override
	public CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] outgoing) {
		Context outgoingContext = new Context(context); // FIXME do I need to copy, deal with request id
		String requestId = outgoingContext.get(Context.REQUEST_ID_KEY); // FIXME corr or req id?
		CompletableFuture<Entry<Context, byte[]>> promise = new CompletableFuture<>();
		try {
			BytesMessage message = requestSession.createBytesMessage();
			if (outgoing != null) {
				message.writeBytes(outgoing);
			}

			for (Entry<String, String> entry: outgoingContext.entrySet()) {
				message.setStringProperty(entry.getKey(), entry.getValue());
			}
			message.setJMSReplyTo(responseQueue);
			message.setJMSCorrelationID(requestId);

			message.setStringProperty(JmsServiceRouterImpl.JMS_ROUTE, route);
			message.setStringProperty(Context.CONTENT_TYPE_KEY, outgoingContext.get(Context.CONTENT_TYPE_KEY));
			requestStore.put(requestId, new SimpleEntry<>(context, promise));
			requestProducer.send(requestTopic, message);
		}
		catch (JMSException ex) {
			// requestStore.remove(requestId);
			promise.completeExceptionally(ex);
		}
		return promise;
	}

	static class ResponseReceiver implements MessageListener {

		private final Map<String, Entry<Context, CompletableFuture<Entry<Context, byte[]>>>> requestStore;

		ResponseReceiver(Map<String, Entry<Context, CompletableFuture<Entry<Context, byte[]>>>> requestStore) {
			this.requestStore = requestStore;
		}

		@Override
		public void onMessage(Message message) {
			String requestId;
			try {
				requestId = message.getJMSCorrelationID();
			}
			catch (JMSException ex) {
				// FIXME log
				return;
			}
			Entry<Context, CompletableFuture<Entry<Context, byte[]>>> entry = requestStore.remove(requestId);
			if (entry == null) {
				// FIXME log
				return;
			}
			CompletableFuture<Entry<Context, byte[]>> promise = entry.getValue();
			try {

				if (message instanceof BytesMessage) {
					Context context = entry.getKey();
					Enumeration e = message.getPropertyNames();
					while (e.hasMoreElements()) {
						String name = String.valueOf(e.nextElement());
						context.put(name, message.getStringProperty(name)); // FIXME not null
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
					promise.completeExceptionally(new InvocationException(((TextMessage) message).getText()));
				}
				else {
					promise.completeExceptionally(new InvocationException("unsupported message type"));
				}
			}
			catch (JMSException ex) {
				promise.completeExceptionally(ex);
			}
		}
	}

	@Override
	public JmsServiceInvoker start() throws JMSException {
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
