/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc.jms;

import java.util.Enumeration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.teris.kite.Context;
import io.teris.kite.rpc.AuthenticationException;
import io.teris.kite.rpc.ServiceExporter;


class JmsServiceExporterImpl extends JmsServiceBase implements JmsServiceExporter {

	private static final Logger log = LoggerFactory.getLogger(JmsServiceExporter.class);

	private final Topic requestTopic;

	private final Map<String, ServiceExporter> serviceDispatchers = new ConcurrentHashMap<>();


	JmsServiceExporterImpl(ConnectionFactory connectionFactory, String topicName) throws JMSException {
		this(connectionFactory.createConnection(), topicName);
	}

	JmsServiceExporterImpl(Connection connection, String topicName) throws JMSException {
		super(connection, connection.createSession(false, Session.CLIENT_ACKNOWLEDGE),
			connection.createSession(false, Session.AUTO_ACKNOWLEDGE));

		requestTopic = requestSession.createTopic(topicName);
	}

	static class ConfiguratorImpl implements Configurator {

		private final ConnectionFactory connectionFactory;

		ConfiguratorImpl(ConnectionFactory connectionFactory) {
			this.connectionFactory = connectionFactory;
		}

		@Nonnull
		@Override
		public JmsServiceExporter requestTopic(String requestTopic) throws JMSException {
			return new JmsServiceExporterImpl(connectionFactory, requestTopic);
		}
	}

	@Nonnull
	@Override
	public JmsServiceExporter export(@Nonnull ServiceExporter serviceExporter) throws JMSException {
		StringBuilder sb = new StringBuilder(JMS_ROUTE);
		sb.append(" IN (");
		AtomicBoolean found = new AtomicBoolean(false);
		for (String route : serviceExporter.routes()) {
			serviceDispatchers.put(route, serviceExporter);

			if (found.getAndSet(true)) {
				sb.append(",");
			}
			sb.append("'");
			sb.append(route);
			sb.append("'");
		}
		sb.append(")");

		// no consumer if no routes
		if (found.get()) {
			requestSession
				.createConsumer(requestTopic, sb.toString())
				.setMessageListener(new RequestConsumer(requestTopic.getTopicName(), serviceDispatchers, responseSession));
		}
		return this;
	}


	static class RequestConsumer implements MessageListener {

		private final String topicName;

		private final Map<String, ServiceExporter> serviceDispatchers;

		private final Session responseSession;

		RequestConsumer(String topicName, Map<String, ServiceExporter> serviceDispatchers, Session responseSession) {
			this.topicName = topicName;
			// do not copy content, assign reference
			this.serviceDispatchers = serviceDispatchers;
			this.responseSession = responseSession;
		}

		@Override
		public void onMessage(Message message) {
			try {
				Destination replyTo = message.getJMSReplyTo();
				if (replyTo == null) {
					throw new JMSException("No address to reply");
				}

				log.debug("server received request {} on '{}'", message.getJMSMessageID(), topicName);

				if (message instanceof BytesMessage) {
					String route = message.getStringProperty(JMS_ROUTE);
					ServiceExporter serviceExporter = serviceDispatchers.get(route);
					if (serviceExporter == null) {
						throw new JMSException(String.format("no service for route %s", route));
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
					serviceExporter
						.call(route, context, data)
						.handle((entry, t) -> {
							if (t != null) {
								respond(message, t);
							}
							else if (entry == null) {
								respond(message, new NullPointerException("Empty response"));
							}
							else {
								respond(message, entry.getKey(), entry.getValue());
							}
							return null;
						});
				}
				else {
					throw new JMSException(String.format("unsupported message type %s", message.getClass().getSimpleName()));
				}
			}
			catch (JMSException ex) {
				respond(message, ex);
			}
		}

		private void respond(Message message, Throwable t) {
			try {
				if (message.getJMSReplyTo() != null) {
					t = t.getCause() != null && (t instanceof CompletionException || t instanceof ExecutionException) ? t.getCause() : t;
					String errorMessage = t.getMessage();
					if (t instanceof AuthenticationException) {
						errorMessage = ACCESS_DENIED + t.getMessage();
					}
					TextMessage responseMessage = responseSession.createTextMessage(errorMessage); // FIXME message null
					responseMessage.setJMSCorrelationID(message.getJMSCorrelationID());
					responseSession.createProducer(message.getJMSReplyTo()).send(responseMessage);
					message.acknowledge();
					log.debug("server sent response for {} to '{}'", responseMessage.getJMSCorrelationID(), message.getJMSReplyTo());
				}
				else {
					message.acknowledge();
					log.error(String.format("No address to reply for Id %s", message.getJMSMessageID()), t);
				}
			}
			catch (JMSException ex) {
				// FIXME implement with retries, log
				log.error(String.format("Failed to send response to request %s", message), ex);
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
				MessageProducer producer = responseSession.createProducer(message.getJMSReplyTo());
				producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
				producer.send(responseMessage);
				message.acknowledge();
				log.debug("server sent response for {} to '{}'", responseMessage.getJMSCorrelationID(), message.getJMSReplyTo());
			}
			catch (JMSException ex) {
				// FIXME implement with retries, log
				log.error(String.format("Failed to send response to request %s", message), ex);
			}
		}
	}

	@Nonnull
	@Override
	public JmsServiceExporter start() throws JMSException {
		connection.start();
		return this;
	}
}
