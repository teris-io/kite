/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.teris.rpc.Context;
import io.teris.rpc.testfixture.ServiceContextPropagatorFixture.DImpl;
import io.teris.rpc.testfixture.ServiceContextPropagatorFixture.DService;


public class ServiceContextPropagatorTest {

	@Test
	public void apply_propagates_toAllElements() {
		ServiceContextPropagator<DService> p = new ServiceContextPropagator<>();

		DService d = new DImpl();
		Context c = ((DImpl) d).getContext();
		assertEquals(1111, d.d());

		Context c1 = new Context();
		DService d1 = p.apply(d, c1);
		assertEquals(2222, d1.d());

		Context c2 = new Context();
		DService d2 = p.apply(d1, c2);
		assertEquals(3333, d2.d());
	}
}
