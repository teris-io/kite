package io.teris.kite.rpc.vertx;

import static io.vertx.core.http.HttpHeaders.COOKIE;
import static io.vertx.core.http.HttpHeaders.SET_COOKIE;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.impl.CookieImpl;


public class KiteCookieHandler implements CookieHandler {

	private static final Logger logger = LoggerFactory.getLogger(KiteCookieHandler.class);

	@Override
	public void handle(RoutingContext context) {
		String cookieHeader = context.request().headers().get(COOKIE);

		if (cookieHeader != null) {
			Set<io.netty.handler.codec.http.cookie.Cookie> nettyCookies = ServerCookieDecoder.STRICT.decode(cookieHeader);
			for (io.netty.handler.codec.http.cookie.Cookie cookie : nettyCookies) {
				Cookie ourCookie = new CookieImpl(cookie);
				context.addCookie(ourCookie);
			}
		}

		context.addHeadersEndHandler($ -> {
			// save the cookies
			MultiMap headers = context.response().headers();
			for (Cookie cookie: context.cookies()) {
				if (cookie.isChanged()) {
					try {
						String encodedCookie = cookie.encode();
						headers.add(SET_COOKIE, encodedCookie);
					}
					catch (IllegalArgumentException ex) {
						logger.error(String.format("Failed to encode cookie %s. The cookie will be ignored in response", cookie), ex);
					}
				}
			}
		});

		context.next();
	}
}
