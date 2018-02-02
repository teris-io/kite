/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.rpc.vertx;

import static org.junit.Assert.assertEquals;

import org.junit.Test;


public class RoutingBaseTest {

	@Test
	public void routeToUri_prependPrefix_success() {
		RoutingBase underTest = new RoutingBase("/api") {};
		assertEquals("/api/whatever/route", underTest.routeToUri("whatever.route"));
	}

	@Test
	public void routeToUri_prependSlashToPrefix_success() {
		RoutingBase underTest = new RoutingBase("api") {};
		assertEquals("/api/whatever/route", underTest.routeToUri("whatever.route"));
	}

	@Test
	public void routeToUri_prependSlashWithoutPrefix_success() {
		RoutingBase underTest = new RoutingBase(null) {};
		assertEquals("/whatever/route", underTest.routeToUri("whatever.route"));
	}

	@Test
	public void routeToUri_changesDotsToSlashes_success() {
		RoutingBase underTest = new RoutingBase("/a.p.i") {};
		assertEquals("/a/p/i/whatever/route", underTest.routeToUri("whatever.route"));
	}

	@Test
	public void routeToUri_removesSlashDuplicates_success() {
		RoutingBase underTest = new RoutingBase("..a....pi") {};
		assertEquals("/a/pi/what/ever/route", underTest.routeToUri("what//ever./route"));
	}

	@Test
	public void uriToRoute_removesSlashedPrefix_success() {
		RoutingBase underTest = new RoutingBase("/a/pi") {};
		assertEquals("whatever.route", underTest.uriToRoute("/a/pi/whatever/route"));
	}

	@Test
	public void uriToRoute_removesMultiSlashesEverywhere_success() {
		RoutingBase underTest = new RoutingBase("///a/pi/") {};
		assertEquals("whatever.route", underTest.uriToRoute("/a//pi////whatever//route"));
	}

	@Test
	public void uriToRoute_removesPrefixWithoutSlash_success() {
		RoutingBase underTest = new RoutingBase("api/") {};
		assertEquals("whatever.route", underTest.uriToRoute("//api////whatever//route"));
	}

	@Test
	public void uriToRoute_noPrefix_success() {
		RoutingBase underTest = new RoutingBase(null) {};
		assertEquals("whatever.route", underTest.uriToRoute("//whatever//route"));
	}
}
