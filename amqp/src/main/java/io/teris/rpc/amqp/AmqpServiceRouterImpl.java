/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.amqp;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

import io.teris.rpc.Context;
import io.teris.rpc.ServiceDispatcher;


class AmqpServiceRouterImpl implements AmqpServiceRouter, AmqpServiceRouter.Router {

	private static final Logger logger = LoggerFactory.getLogger(AmqpServiceRouter.class);

	private final Connection connection;

	private final Channel channel;

	private final String exchangeName;

	private final String requestQueue = "request-" + UUID.randomUUID().toString(); // FIXME pass in

	private final Map<String, ServiceDispatcher> serviceDispatchers = new ConcurrentHashMap<>();


	AmqpServiceRouterImpl(ConnectionFactory connectionFactory, String exchangeName) throws IOException, TimeoutException {
		this.exchangeName = exchangeName;
		connection = connectionFactory.newConnection();
		channel = connection.createChannel();

		channel.exchangeDeclare(exchangeName, BuiltinExchangeType.TOPIC);

		channel.queueDeclare(requestQueue, true, false, true, Collections.emptyMap());
		channel.basicConsume(requestQueue, false, requestQueue,
			new RequestConsumer(channel, exchangeName, serviceDispatchers));
	}

	static class BuilderImpl implements Builder {

		private final ConnectionFactory connectionFactory;

		private String exchangeName;

		BuilderImpl(ConnectionFactory connectionFactory) {
			this.connectionFactory = connectionFactory;
		}

		@Override
		public Builder exchangeName(String exchangeName) {
			this.exchangeName = exchangeName;
			return this;
		}

		@Nonnull
		@Override
		public Router build() throws IOException, TimeoutException {
			return new AmqpServiceRouterImpl(connectionFactory, exchangeName);
		}
	}

	@Nonnull
	@Override
	public Router route(@Nonnull ServiceDispatcher serviceDispatcher) throws IOException  {
		for (String route :serviceDispatcher.dispatchRoutes()) {
			serviceDispatchers.put(route, serviceDispatcher);
			channel.queueBind(requestQueue, exchangeName, route);
		}
		return this;
	}


	static class RequestConsumer extends DefaultConsumer implements Consumer {

		private final String exchangeName;

		private final Map<String, ServiceDispatcher> serviceDispatchers;

		RequestConsumer(Channel channel, String exchangeName, Map<String, ServiceDispatcher> serviceDispatchers) {
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

				if (AmqpServiceInvokerImpl.MSGTYPE_REQUEST.equals(props.getType())) {
					String route = envelope.getRoutingKey();
					ServiceDispatcher serviceDispatcher = serviceDispatchers.get(route);
					if (serviceDispatcher == null) {
						throw new IOException(String.format("no service for route %s", route));
					}
					Context context = new Context();
					if (props.getContentType() != null) {
						context.put(Context.CONTENT_TYPE_KEY, props.getContentType());
					}
					for (Entry<String, Object> prop: props.getHeaders().entrySet()) {
						context.put(prop.getKey(), String.valueOf(prop.getValue()));
					}

					serviceDispatcher.call(route, context, body)
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
				String replyTo = props.getReplyTo();
				props = new BasicProperties.Builder()
					.correlationId(props.getCorrelationId())
					.contentEncoding("UTF-8")
					.type(AmqpServiceInvokerImpl.MSGTYPE_ERROR)
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
					.contentType(context.getOrDefault(Context.CONTENT_TYPE_KEY, Context.DEFAULT_CONTENT_TYPE))
					.contentEncoding("UTF-8")
					.headers(headers)
					.type(AmqpServiceInvokerImpl.MSGTYPE_RESPONSE)
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

	@Override
	public AmqpServiceRouter start() {
		return this;
	}

	@Nonnull
	@Override
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
