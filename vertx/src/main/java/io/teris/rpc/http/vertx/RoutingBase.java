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
		String res = (uriPrefix != null ? uriPrefix : "") + "/" + route;
		if (!res.startsWith("/")) {
			res = "/" + res;
		}
		return res.replaceAll("\\.", "/").replaceAll("(/)\\1+", "$1");
	}

	String uriToRoute(String uri) {
		String uriStart = "/";
		if (uriPrefix != null) {
			uriStart = routeToUri("");
		}
		uri = uri.replaceAll("(/)\\1+", "$1");
		if (uri.startsWith(uriStart)) {
			uri = uri.substring(uriStart.length());
		}
		String res = uri.replaceAll("/", ".");
		return res.startsWith(".") ? res.substring(1) : res;
	}
}
