/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.amqp;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import io.teris.rpc.InvocationException;


class AmqpServiceInvokerImpl implements AmqpServiceInvoker {

	private static final Logger log = LoggerFactory.getLogger(AmqpServiceInvoker.class);

	static final String MSGTYPE_REQUEST = "request";

	static final String MSGTYPE_RESPONSE = "response";

	static final String MSGTYPE_ERROR = "error";

	private final String exchangeName;

	private final Connection connection;

	private final Channel channel;

	private final Map<String, Entry<Context, CompletableFuture<Entry<Context, byte[]>>>> requestStore = new ConcurrentHashMap<>();

	private final String clientId;


	AmqpServiceInvokerImpl(ConnectionFactory connectionFactory, String exchangeName) throws IOException, TimeoutException {
		this.exchangeName = exchangeName;
		connection = connectionFactory.newConnection();
		channel = connection.createChannel();

		channel.exchangeDeclare(exchangeName, BuiltinExchangeType.TOPIC);

		clientId = UUID.randomUUID().toString();
		// routed via the same exchange but to a queue with the clientId name via routing key being clientId
		String responseQueueName = "response-" + clientId;
		channel.queueDeclare(responseQueueName, true, true, true, Collections.emptyMap());
		channel.queueBind(responseQueueName, exchangeName, clientId);
		channel.basicConsume(responseQueueName, true, clientId, new ResponseReceiver(channel, clientId, requestStore));
	}

	static class BuilderImpl implements AmqpServiceInvoker.Builder {

		private final ConnectionFactory connectionFactory;

		private String exchangeName = "RPC";

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
		public AmqpServiceInvoker build() throws IOException, TimeoutException {
			return new AmqpServiceInvokerImpl(connectionFactory, exchangeName);
		}
	}

	@Nonnull
	@Override
	public CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] outgoing) {
		String correlationId = context.get(Context.X_REQUEST_ID_KEY);
		CompletableFuture<Entry<Context, byte[]>> promise = new CompletableFuture<>();
		try {
			Objects.requireNonNull(correlationId, "Context contains no " + Context.X_REQUEST_ID_KEY);
			@SuppressWarnings("unchecked")
			Map<String, Object> headers = (Map) context;
			BasicProperties props = new BasicProperties.Builder()
				.correlationId(correlationId)
				.replyTo(clientId)
				.contentType(context.get(Context.CONTENT_TYPE_KEY))
				.contentEncoding("UTF-8")
				.headers(headers)
				.type(MSGTYPE_REQUEST)
				.build();
				requestStore.put(correlationId, new SimpleEntry<>(context, promise));
				channel.basicPublish(exchangeName, route, props, outgoing);
			log.debug("client sent request {} to '{}'", correlationId, exchangeName);
		}
		catch (Exception ex) {
			if (correlationId != null) {
				requestStore.remove(correlationId);
			}
			promise.completeExceptionally(ex);
		}
		return promise;
	}

	static class ResponseReceiver extends DefaultConsumer implements Consumer {

		private final String clientId;

		private final Map<String, Entry<Context, CompletableFuture<Entry<Context, byte[]>>>> requestStore;

		ResponseReceiver(Channel channel, String clientId, Map<String, Entry<Context, CompletableFuture<Entry<Context, byte[]>>>> requestStore) {
			super(channel);
			this.clientId = clientId;
			this.requestStore = requestStore;
		}

		@Override
		public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties props, byte[] body) {
			String correlationId = null;
			try {
				correlationId = props.getCorrelationId();

				Entry<Context, CompletableFuture<Entry<Context, byte[]>>> entry = requestStore.remove(correlationId);
				if (entry == null || entry.getValue() == null) {
					throw new IOException("No request information found");
				}

				log.debug("client received response for {} on  '{}'", correlationId, "response-" + clientId);

				CompletableFuture<Entry<Context, byte[]>> promise = entry.getValue();
				if (MSGTYPE_RESPONSE.equals(props.getType())) {
					Context context = entry.getKey();
					if (props.getContentType() != null) {
						context.put(Context.CONTENT_TYPE_KEY, props.getContentType());
					}
					for (Entry<String, Object> prop: props.getHeaders().entrySet()) {
						context.put(prop.getKey(), String.valueOf(prop.getValue()));
					}
					promise.complete(new SimpleEntry<>(context, body));
				}
				else if (MSGTYPE_ERROR.equals(props.getType())) {
					promise.completeExceptionally(new InvocationException(new String(body)));
				}
				else {
					promise.completeExceptionally(new InvocationException("Unsupported message type: " + new String(body)));
				}
			}
			catch (IOException ex) {
				log.error(String.format("client %s from 'response-%s' failed to process", correlationId, clientId), ex);
			}
		}
	}

	@Override
	public AmqpServiceInvoker start()  {
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
