/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.teris.rpc.BusinessException;
import io.teris.rpc.Context;
import io.teris.rpc.InvocationException;
import io.teris.rpc.Serializer;
import io.teris.rpc.ServiceException;


public class InvocationCompleter {

	private final Method method;

	private final Serializer serializer;

	public InvocationCompleter(Method method, Serializer serializer) {
		this.method = method;
		this.serializer = serializer;
	}

	public CompletableFuture<Entry<Context, byte[]>> complete(Context context) {
		return CompletableFuture.completedFuture(new SimpleEntry<>(context, null));
	}

	public CompletableFuture<Entry<Context, byte[]>> complete(ServiceException ex) {
		CompletableFuture<Entry<Context, byte[]>> promise = new CompletableFuture<>();
		promise.completeExceptionally(ex);
		return promise;
	}

	public CompletableFuture<Entry<Context, byte[]>> complete(Method method, Exception ex) {
		if (ex instanceof ServiceException) {
			return complete((ServiceException) ex);
		}
		CompletableFuture<Entry<Context, byte[]>> promise = new CompletableFuture<>();
		promise.completeExceptionally(new InvocationException(method, ex));
		return promise;
	}

	public CompletableFuture<Entry<Context, byte[]>> complete(Context context, Serializable returnValue) {
		CompletableFuture<Entry<Context, byte[]>> promise = new CompletableFuture<>();
		CompletableFuture.runAsync(() -> {
			try {
				byte[] outgoing = serializer.serialize(returnValue);
				promise.complete(new SimpleEntry<>(context, outgoing));
			}
			catch (Exception ex) {
				promise.completeExceptionally(new InvocationException(method, ex));
			}
		});
		return promise;
	}

	public CompletableFuture<Entry<Context, byte[]>> complete(Context context, CompletableFuture<?> returnValue) {
		CompletableFuture<Entry<Context, byte[]>> promise = new CompletableFuture<>();
		returnValue.handleAsync((obj, t) -> {
			try {
				if (t instanceof Exception) {
					promise.completeExceptionally(new BusinessException(t));
				}
				else if (t != null) {
					promise.completeExceptionally(new BusinessException(t.toString()));
				}
				else if (obj == null || void.class.isAssignableFrom(obj.getClass()) || Void.class.isAssignableFrom(obj.getClass())) {
					promise.complete(new SimpleEntry<>(context, null));
				}
				else if (obj instanceof Serializable) {
					byte[] outgoing = serializer.serialize((Serializable) obj);
					promise.complete(new SimpleEntry<>(context, outgoing));
				}
				else {
					promise.completeExceptionally(new InvocationException(method, "return type is neither Serializable nor void"));
				}
			}
			catch (Exception ex) {
				promise.completeExceptionally(new InvocationException(method, ex));
			}
			return null;
		});
		return promise;
	}

	public CompletableFuture<Entry<Context, byte[]>> complete(Context context, Future returnValue) {
		CompletableFuture<Entry<Context, byte[]>> promise = new CompletableFuture<>();
		CompletableFuture.runAsync(() -> {
			try {
				Object obj = returnValue.get();
				if (obj == null || void.class.isAssignableFrom(obj.getClass()) || Void.class.isAssignableFrom(obj.getClass())) {
					promise.complete(new SimpleEntry<>(context, null));
				}
				else if (obj instanceof Serializable) {
					byte[] outgoing = serializer.serialize((Serializable) obj);
					promise.complete(new SimpleEntry<>(context, outgoing));
				}
				else {
					promise.completeExceptionally(new InvocationException(method, "return type is neither Serializable nor void"));
				}
			}
			catch (ExecutionException ex) {
				promise.completeExceptionally(new BusinessException(ex.getCause()));
			}
			catch (Exception ex) {
				promise.completeExceptionally(new InvocationException(method, ex));
			}
		});
		return promise;
	}

}
