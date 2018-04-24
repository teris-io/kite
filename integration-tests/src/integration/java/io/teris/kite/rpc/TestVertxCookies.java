package io.teris.kite.rpc;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.kite.Context;
import io.teris.kite.Serializer;
import io.teris.kite.Service;
import io.teris.kite.gson.JsonSerializer;
import io.teris.kite.rpc.vertx.HttpServiceExporter;
import io.teris.kite.rpc.vertx.HttpServiceInvoker;
import io.teris.kite.rpc.vertx.KiteCookieHandler;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;


public class TestVertxCookies {

	private static final String AUTH_HEADER_VALUE = "bearer valid";

	private static final String AUTH_HEADER_KEY = "authorization";

	@Service
	public interface XService {
		void checkAuthorization(Context context);
	}

	private static XService xservice;

	private static HttpServer server;

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@BeforeClass
	public static void init() throws Exception {
		int port;
		while (true) {
			try (ServerSocket socket = new ServerSocket((int) (49152 + Math.random() * (65535 - 49152)))) {
				port = socket.getLocalPort();
				break;
			}
			catch (IOException e) {
				// repeat
			}
		}

		Vertx vertx = Vertx.vertx();
		Serializer serializer = JsonSerializer.builder().build();

		HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions()
			.setDefaultHost("localhost")
			.setDefaultPort(port)
			.setMaxPoolSize(200));

		HttpServiceInvoker invoker = HttpServiceInvoker.httpClient(httpClient).build();

		xservice = ServiceFactory.invoker(invoker)
			.serializer(serializer)
			.build()
			.newInstance(XService.class);


		HttpServerOptions httpServerOptions = new HttpServerOptions().setHost("0.0.0.0").setPort(port);

		XService xserviceImpl = new XService() {
			@Override
			public void checkAuthorization(Context context) {
				if (!AUTH_HEADER_VALUE.equals(context.get(AUTH_HEADER_KEY))) {
					throw new AuthenticationException("wrong auth");
				}
			}
		};

		Handler<RoutingContext> cookieProcessor = (ctx) -> {
			Cookie cookie = ctx.getCookie("session");
			if (cookie == null) {
				String authHeader = ctx.request().getHeader(AUTH_HEADER_KEY);
				if (authHeader != null) {
					cookie = Cookie.cookie("session", new String(Base64.getEncoder().encode(authHeader.getBytes())));
					cookie.setPath("/");
					cookie.setMaxAge(3600);
					ctx.addCookie(cookie);
				}
			}
			else {
				ctx.request().headers().add(AUTH_HEADER_KEY, new String(Base64.getDecoder().decode(cookie.getValue())));
			}
			ctx.next();
		};

		HttpServiceExporter httpExporter = HttpServiceExporter.router(vertx)
			.bodyHandler(BodyHandler.create().setBodyLimit(10000000))
			.preprocessor(new KiteCookieHandler())
			.preprocessor(cookieProcessor)
			.export(ServiceExporter.serializer(serializer)
				.export(XService.class, xserviceImpl)
				.build());

		CompletableFuture<HttpServer> promise = new CompletableFuture<>();
		CompletableFuture.runAsync(() ->
			vertx.createHttpServer(httpServerOptions)
				.requestHandler(httpExporter.router()::accept)
				.listen(handler -> {
					if (handler.failed()) {
						promise.completeExceptionally(handler.cause());
						return;
					}
					promise.complete(handler.result());
				}));

		server = promise.get(5, TimeUnit.SECONDS);
	}

	@AfterClass
	public static void teardown() {
		server.close();
	}

	@Test
	public void noAuth_throws() {
		exception.expectMessage("wrong auth");
		xservice.checkAuthorization(new Context());
	}

	@Test
	public void auth_thenCookie_ok() {
		Context authContext = new Context();
		authContext.put(AUTH_HEADER_KEY, AUTH_HEADER_VALUE);
		xservice.checkAuthorization(authContext);
		assertTrue(authContext.containsKey("Set-Cookie"));

		Context emptyContext = new Context();
		xservice.checkAuthorization(emptyContext);
		assertTrue(emptyContext.containsKey("Cookie"));
	}
}
