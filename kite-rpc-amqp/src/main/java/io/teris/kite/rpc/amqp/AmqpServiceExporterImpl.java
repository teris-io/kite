/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc.amqp;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import io.teris.kite.Context;
import io.teris.kite.rpc.AuthenticationException;
import io.teris.kite.rpc.NotFoundException;
import io.teris.kite.rpc.ServiceExporter;


class AmqpServiceExporterImpl extends AmqpServiceBase implements AmqpServiceExporter {

	private static final Logger logger = LoggerFactory.getLogger(AmqpServiceExporter.class);

	private final String exchangeName;

	private final String requestQueue = "request-" + UUID.randomUUID().toString(); // FIXME pass in

	private final Map<String, ServiceExporter> serviceDispatchers = new ConcurrentHashMap<>();


	AmqpServiceExporterImpl(ConnectionFactory connectionFactory, String exchangeName) throws IOException, TimeoutException {
		this(connectionFactory.newConnection(), exchangeName);
	}

	AmqpServiceExporterImpl(Connection connection, String exchangeName) throws IOException {
		super(connection, connection.createChannel());

		this.exchangeName = exchangeName;
		channel.exchangeDeclare(exchangeName, BuiltinExchangeType.TOPIC);

		channel.queueDeclare(requestQueue, true, false, true, Collections.emptyMap());
		channel.basicConsume(requestQueue, false, requestQueue,
			new RequestConsumer(channel, exchangeName, serviceDispatchers));
	}

	static class ConfiguratorImpl implements Configurator {

		private final ConnectionFactory connectionFactory;

		ConfiguratorImpl(ConnectionFactory connectionFactory) {
			this.connectionFactory = connectionFactory;
		}

		@Nonnull
		@Override
		public AmqpServiceExporter requestExchange(String requestExchange) throws IOException, TimeoutException {
			return new AmqpServiceExporterImpl(connectionFactory, requestExchange);
		}
	}

	@Nonnull
	@Override
	public AmqpServiceExporter export(@Nonnull ServiceExporter serviceExporter) throws IOException  {
		for (String route : serviceExporter.routes()) {
			serviceDispatchers.put(route, serviceExporter);
			channel.queueBind(requestQueue, exchangeName, route);
		}
		return this;
	}


	static class RequestConsumer extends DefaultConsumer implements Consumer {

		private final String exchangeName;

		private final Map<String, ServiceExporter> serviceDispatchers;

		RequestConsumer(Channel channel, String exchangeName, Map<String, ServiceExporter> serviceDispatchers) {
			super(channel);
			this.exchangeName = exchangeName;
			// do not copy content, assign reference
			this.serviceDispatchers = serviceDispatchers;
		}

		@Override
		public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties props, byte[] body) {
			try {
				String replyTo = props.getReplyTo();
				if (replyTo == null) {
					throw new IOException("No address to reply");
				}

				logger.debug("server received request {} on '{}'", props.getCorrelationId(), exchangeName);

				if (MSGTYPE_REQUEST.equals(props.getType())) {
					String route = envelope.getRoutingKey();
					ServiceExporter serviceExporter = serviceDispatchers.get(route);
					if (serviceExporter == null) {
						throw new IOException(String.format("no service for route %s", route));
					}
					Context context = new Context();
					if (props.getContentType() != null) {
						context.put(Context.CONTENT_TYPE_KEY, props.getContentType());
					}
					for (Entry<String, Object> prop: props.getHeaders().entrySet()) {
						context.put(prop.getKey(), String.valueOf(prop.getValue()));
					}

					serviceExporter.call(route, context, body)
						.whenComplete((entry, t) -> {
							if (t != null) {
								respond(props, envelope, t);
							}
							else if (entry == null) {
								respond(props, envelope, new NullPointerException("Empty response"));
							}
							else {
								respond(props, envelope, entry.getKey(), entry.getValue());
							}
						});
				}
				else {
					throw new IOException(String.format("unsupported message type %s", props.getType()));
				}
			}
			catch (IOException ex) {
				respond(props, envelope, ex);
			}
		}

		private void respond(BasicProperties props, Envelope envelope, Throwable t) {
			try {
				t = t.getCause() != null && (t instanceof CompletionException || t instanceof ExecutionException) ? t.getCause() : t;
				String replyTo = props.getReplyTo();
				props = new BasicProperties.Builder()
					.correlationId(props.getCorrelationId())
					.contentEncoding("UTF-8")
					.type(t instanceof AuthenticationException ? MSGTYPE_ERROR_AUTH :
						t instanceof NotFoundException ? MSGTYPE_ERROR_NOTFOUND : MSGTYPE_ERROR)
					.build();

				if (replyTo != null) {
					String message = t.getMessage() != null ? t.getMessage() : t.toString();
					getChannel().basicPublish(exchangeName, replyTo, props, message.getBytes());
					getChannel().basicAck(envelope.getDeliveryTag(), false);
					logger.debug("server sent response for {} to '{}'", props.getCorrelationId(), replyTo);
				}
				else {
					getChannel().basicAck(envelope.getDeliveryTag(), false);
					logger.error(String.format("No address to reply for Id %s", props.getCorrelationId()), t);
				}
			}
			catch (IOException ex) {
				// FIXME implement with retries, log
				logger.error(String.format("Failed to send response to request Id %s", props.getCorrelationId()), ex);
			}
		}

		private void respond(BasicProperties props, Envelope envelope, Context context, byte[] data) {
			@SuppressWarnings("unchecked")
			Map<String, Object> headers = (Map) context;
			try {
				String replyTo = props.getReplyTo();
				props = new BasicProperties.Builder()
					.correlationId(props.getCorrelationId())
					.contentType(context.get(Context.CONTENT_TYPE_KEY))
					.contentEncoding("UTF-8")
					.headers(headers)
					.type(MSGTYPE_RESPONSE)
					.build();

				getChannel().basicPublish(exchangeName, replyTo, props, data);
				getChannel().basicAck(envelope.getDeliveryTag(), false);
				logger.debug("server sent response for {} to '{}'", props.getCorrelationId(), replyTo);
			}
			catch (IOException ex) {
				// FIXME implement with retries, log
				logger.error(String.format("Failed to send response to request Id %s", props.getCorrelationId()), ex);
			}
		}
	}

	@Nonnull
	@Override
	public AmqpServiceExporter start() {
		return this;
	}
}
