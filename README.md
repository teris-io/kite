[![Build Status](https://travis-ci.org/teris-io/rpc.svg?branch=master)](https://travis-ci.org/teris-io/rpc)
[![Code Coverage](https://img.shields.io/codecov/c/github/teris-io/rpc.svg)](https://codecov.io/gh/teris-io/rpc)


# rpc

The `rpc` library aims to simplify the definition and implementation of public APIs and
their use for RPC. It draws a clear cut between the invocation, serialization and transport
yielding a definition of the invocation flow by composition of proxy, serialization and
transport.

The `io.teris.rpc:rpc` core library is plain Java-8 with no further dependencies. On the
client side, it implements the service instantiation remote call logic passing service
method arguments through the bound serializer and transport layer. On the server side,
it implements the dispatching mechanism that takes incoming requests from the bound
transport layer, passes the data through a content-type specific bound deserializer and
dispatches to a service implementation.

## Service declaration

APIs are defined by declaring public interfaces annotated with `@io.teris.rpc.Service`. Service
method arguments must also be annotated with `@io.teris.rpc.Name`. The two annotations
control the way invocation routes are composed (e.g. HTTP endpoints or AMQP routing keys),
and how the payload structure looks like. 

The library support two types of service methods. Asynchronous methods return a 
`CompletableFuture` of generic type extending `Serializable` or being `Void`. 
Synchronous methods return an instance of a type that extends `Serializable` directly, or
are declared void. Internally the library itself is fully asynchronous and will benefit 
from non-blocking execution of the implementations.

Service method arguments start with `io.teris.rpc.Context` that serves to pass request
headers (bi-directionally). Therefore, the most simple signature of a service method reads
`void call(Context context);`. Further arguments must explicitly implement `Serializable`.
The same requirement is applied to generic type, array and vararg parameters. Wildcards
are not supported. All method arguments after the context, must be annotated with 
`@io.teris.rpc.Name` even if the compiler options `-parameters` is on.

The following defines a service with two endpoints, a synchronous and an asynchronous ones:

    package com.company.api;

    @Service(replace="com.company")
    public interface DataService {

        CompletableFuture<Void> upload(Context context, @Name("data") HashMap<String, Double> data);

        Double download(Context context, @Name("key") String key);
    }

Each service method receives an independent route composed, by default, from the package
name, service class name (w/o the Service suffix) and a method name, all lower case and
dot-separated. There is a mechanism to override those defaults redefining the routes fully
or partially. The declaration above yields the following routes, `api.data.upload` and
`api.data.download`, respectively. Public inner interfaces are fully supported for
service declaration and their holder class name becomes a part of the route (preserving
the `Service` suffix on the holder if any).

All provided transport implementations are aware of those route definitions and will
automatically expose service endpoints at the server side and route to the correct ones
on the client.

The client side `ServiceFactory` and the server sie `ServiceDispatcher`
will validate method/service declaration before the invocation and at the time of binding
s service implementation. 


## Invocation

Service proxy instances are obtained from an instance of `ServiceFactory`,
which is parametrized using an instance of `Serializer` that transfers method arguments
into a byte array of data transporable over the wire and an instance of `ServiceInvoker` 
that implements that transport layer on the client side. Content type specific deserializers
can additionally be registered for turning the response byte array into a response structure.
Assuming the content type of the response is the same as of the request, the default 
deserializer is provided by the registered serializer.

`ServiceFactory` instances can be constructed for example in the following manner:

	ServiceFactory factory = ServiceFactory.builder()
		.serviceInvoker(invoker)
		.serializer(serializer)
		.build();

Service proxies are then obtained by calling `newInstance` on the factory:

    DataService dataService = factory.newInstance(DataService.class);

## Transport

Transport implementations implement the `ServiceInvoker` interface for the client side and
`ServiceDispatcher` for the server side. Both interfaces, although not related via any
parent-child relation, provide the following method that is central for the bi-directional
data flow:

	CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] data);

An invocating proxy first composes the method route. Then it copies the context and puts a
new unique request/correlation Id along with the serializer content type there. Finally, it
collects method arguments, serializes them into a byte array of data and passes all of the
above into the transport layer behind a `ServiceInvoker` instance. It then asynchronously
waits for the completion of the returned future by the server side.

The transport layer transfers the data and context in form of request/message headers to
the destination route (which for protocol comptibility may be for example transformed into
a URI on a given host/port). The server side transport implementation then receives the
request/message and passes all three values to an instance of `ServiceDispatcher` that,
based on the route, finds a service implementation instance and method to call, deserializes
the data correspondingly, creates a new context from the headers and invokes the method.

The package provides transport layer implementations for HTTP using `vert.x`, JMS using
`ActiveMQ` and AMQP using `RabbitMQ`. It further provides a default JSON serializer
based on the GSON library. Using the `vert.x` HTTP implementation and the GSON serializer,
the fully configured client-side service factory that will invoke service methods over
HTTP can be instantiated like this (adding further optional parameters for completeness):

	HttpClient httpClient = Vertx.vertx().createHttpClient(new HttpClientOptions()
		.setDefaultHost("localhost")
		.setDefaultPort(8080)
		.setMaxPoolSize(50));

	ServiceInvoker invoker = VertxServiceInvoker.builder(httpClient).build();

	Serializer serializer = GsonSerializer.builder().build();

	Supplier<String> uidGenerator = () -> UUID.randomUUID().toString();

	ServiceFactory factory = ServiceFactory.builder()
		.serviceInvoker(invoker)
		.serializer(serializer)
		.uidGenerator(uidGenerator)
		.build();

On the server side, one simply needs to register service implementing instances with an
instance of `ServiceDispatcher` using a matching protocol and a matching (de)serializer.
For `vert.x` HTTP and GSON, the registration code invoked at the start of the server
application can look like this:

	Vertx vertx = Vertx.vertx();

	Serializer serializer = GsonSerializer.builder().build();

	ServiceDispatcher dispatcher = ServiceDispatcher.builder()
		.serializer(serializer)
		.bind(DataService.class, new DataServiceImpl(dataServiceDependency))
		.bind(OtherService.class, new OtherServiceImpl(otherServiceDependency))
		.build();

	Router router = Router.router(vertx);

	VertxServiceRouter.builder(router).build()
		.preconditioner(authHandler)
		.route(dispatcher);

	vertx.createHttpServer(httpServerOptions)
		.requestHandler(router::accept)
		.listen());

Any exceptions during the invocation process will be wrapped into `io.teris.rpc.InvocationException`
or `io.teris.rpc.BusinessExcpeption` when the exception happens during invoking the actual
service method. References to the cause are dropped from the transport while their original
stacktraces are preserved. Exception data will be delivered serialized in the response payload.
The only exception is when the serialization of an (exceptional or normal) response on the
server side causes an exception itself. This will be delivered to the client by different
means depending on the transport resuling in the future completed exceptionally on the
transport layer interface. Exceptions are then recreated and rethrown on the client.

It is essential to understand that _no checked_ exceptions will cross the remote invocation
boundary and all exceptions will descend from the `InvocationException` or `BusinessException`.
This is true even for the case when the service declares and throws a checked exception. The
reason for this is to guarantee that exceptions are always deserializable on the client side!

### Serialization and public APIs

Using the provided GSON-based serializer and the `vert.x` HTTP transport layer implementation,
every service becomes an API that can be easily consumed. For example, using the server side
initialization as shown above, The API endpoint for the `DataService.download` method declared
above will be defined as follows:

	REQUEST:
		POST /api/data/download
		HEADERS: X-Request-Id, Content-Type (automatically set, the rest from Context)
		JSON {
			"key": <string>
		}

And the corresponding response:

	RESPONSE:
		CODE: 200
		HEADERS: (copied from response, plus added in the invocation)
		JSON {
			"payload": <double or null on error>,
			"exception": <serialized io.teris.rpc.BusinessException/InvocationException with original stack, or null>,
			"errorMessage": <exception message duplicated, or null>
		}

Unless the client is Java and is using this library to consume the response, the response payload
for `CompletableFuture<MyType>` and for `MyType` will be identical with `MyType` data under the
`payload` field.

The server will only return non-Ok HTTP codes if it fails to serialize the error response into
a payload structure shown above. Otherwise, the response is always 200, or 3xx if overwritten
by a proxy.

Service requests that contain no arguments beside `Context` will be performed with an empty
request body. Service responses that respond to `void` or `CompletableFuture<Void>` and contain no
exception will contain an empty response body. Both are treated as normal, non-erroneous cases.

### Further notes

When implementing custom de-serializers, it is a requirements that for target types specified
explicitly as `Serializable`, a byte array slice of the (original or not, but deserializeable)
data is returned. Due to the nature of putting dynamic method arguments together, the
server side will first deserialize into a `HashMap<String, Serializable>`and then, in a second
round, it will deserialize a byte array behind each `Serializable` into a concrete type as
declared on the method.

### License and copyright

	Copyright (c) 2017. Oleg Sklyar and teris.io. MIT license applies. All rights reserved.
