/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.http.vertx;

abstract class RoutingBase {

	private final String uriPrefix;

	RoutingBase(String uriPrefix) {
		this.uriPrefix = uriPrefix;
	}

	String routeToUri(String route) {
		return (uriPrefix != null ? uriPrefix : "") + "/" + route.replaceAll("\\.", "/");
	}

	String uriToRoute(String uri) {
		if (uriPrefix != null && uri.startsWith(uriPrefix)) {
			uri = uri.substring(uriPrefix.length());
		}
		return uri.replaceAll("/", ".").substring(1);
	}
}
