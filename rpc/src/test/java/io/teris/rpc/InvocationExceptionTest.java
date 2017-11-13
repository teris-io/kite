/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

/**
 * Copyright (c) Profidata AG 2017
 */
package io.teris.rpc;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;

import org.junit.Test;


public class InvocationExceptionTest {

	interface AService {

		void thrown();

		void caused();
	}


	@Test
	public void thrown_correctMessage() {
		try {
			AService service = Proxier.get(AService.class);
			service.thrown();
			throw new AssertionError("unreachable code");
		}
		catch (UndeclaredThrowableException actual) {
			InvocationException ex = (InvocationException) actual.getCause();
			assertEquals("Failed to invoke AService.thrown", ex.getMessage());
		}
	}

	@Test
	public void caused_correctMessage() {
		try {
			AService service = Proxier.get(AService.class);
			service.caused();
			throw new AssertionError("unreachable code");
		}
		catch (UndeclaredThrowableException actual) {
			InvocationException ex = (InvocationException) actual.getCause();
			assertEquals("Failed to invoke AService.caused [caused by NumberFormatException: For input string: \"bad\"]", ex.getMessage());
		}
	}

	private static class Proxier implements InvocationHandler {

		static <S> S get(Class<S> serviceClass) {
			@SuppressWarnings("unchecked")
			S res = (S) Proxy.newProxyInstance(Proxier.class.getClassLoader(), new Class[]{ serviceClass }, new Proxier());
			return res;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws InvocationException {
			if ("thrown".equals(method.getName())) {
				throw new InvocationException(method);
			}
			try {
				Integer.valueOf("bad");
				throw new AssertionError("unreachable code");
			}
			catch (Exception ex) {
				throw new InvocationException(method, ex);
			}
		}
	}

}
