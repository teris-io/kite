/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

/**
 * Copyright (c) Profidata AG 2017
 */
package io.teris.rpc.http.vertx;

import java.io.IOException;
import java.net.ServerSocket;


public class FreePortProvider {

	private FreePortProvider() {
		super();
	}

	public static int port() {
		while (true) {
			try (ServerSocket socket = new ServerSocket((int) (49152 + Math.random() * (65535 - 49152)))) {
				return socket.getLocalPort();
			}
			catch (IOException e) {
				// repeat
			}
		}
	}
}
