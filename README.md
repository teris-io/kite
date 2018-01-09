[![Build Status](https://travis-ci.org/teris-io/rpc.svg?branch=master)](https://travis-ci.org/teris-io/rpc)
[![Code Coverage](https://img.shields.io/codecov/c/github/teris-io/rpc.svg)](https://codecov.io/gh/teris-io/rpc)


# rpc - service-based RPC and public APIs in Java

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

## Obtaining the library

Get it with gradle:

    repositories.jcenter()

    dependencies {
      compile("io.teris.rpc:rpc:+")
      compile("io.teris.rpc:serialization-json:+")
      compile("io.teris.rpc:vertx:+")
    }

Or download the `rpc`, `vertx` and `serialization-json` jars directly from the
[jcenter repository](http://jcenter.bintray.com/io/teris/rpc/).

## Building and testing

In order to run all integration tests one needs to have `rabbitmq` broker running on `localhost:5672`
using the default guest account. The easiest way to get it deployed is by using an official `rabbitmq`
docker image:

	docker run -it -p 5672:5672 rabbitmq:alpine

Vert.x and ActiveMQ tests will run their server side deployments from within the tests. With rabbit deployed
one can run the full build with unit and integration tests and a deployment to the local `~/.m2` maven
repository using:

	./gradlew build test integration coverage install

Without rabbit installed, add `--continue` to continue upon encounterring a failed test.


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

```java
package com.company.api;

@Service(replace="com.company")
public interface DataService {

    CompletableFuture<Void> upload(Context context, @Name("data") HashMap<String, Double> data);

    Double download(Context context, @Name("key") String key);
}
```

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


## Client-side invocation

Service proxy instances are obtained from an instance of `ServiceFactory`,
which is parametrized using an instance of `Serializer` that transfers method arguments
into a byte array of data transporable over the wire and an instance of `ServiceInvoker` 
that implements that transport layer on the client side. Content type specific deserializers
can additionally be registered for turning the response byte array into a response structure.
Assuming the content type of the response is the same as of the request, the default 
deserializer is provided by the registered serializer.

`ServiceFactory` instances can be constructed for example in the following manner:

```java
ServiceFactory factory = ServiceFactory.builder()
	.serviceInvoker(invoker)
	.serializer(serializer)
	.build();
```

Service proxies are then obtained by calling `newInstance` on the factory:

```java
DataService dataService = factory.newInstance(DataService.class);

Double value = dataService.download(new Context(), "key");
```

## Serialization

Serialization and deserialization are intergral parts of the remote invocation workflow.
The client serializes outgoing requests and deserializes incoming responses while the server
deserializes incoming requests and serializes outgoing responses.

Serialization is pluggable into the client and server sides via instances implementing the
`io.teris.rpc.Serializer`

```java
@Nonnull
<CT extends Serializable> CompletableFuture<byte[]> serialize(@Nonnull CT value);
```

and `io.teris.rpc.Deserializer` interfaces

```java
@Nonnull
<CT extends Serializable> CompletableFuture<CT> deserialize(@Nonnull byte[] data, @Nonnull Class<CT> clazz);

@Nonnull
<CT extends Serializable> CompletableFuture<CT> deserialize(@Nonnull byte[] data, @Nonnull Type type);
```

When sending out a message (client request or server response), the serializer on the
sending side defines and sets the content type of the data to transfer. This value is read
from the headers on the receiving side and a matching deserializer is used to
deserialize the content (in case no matching one found, the deserializer of the registered
serializer is tried).

The package provides JSON serializer and deserializer in `io.teris.rpc:serialization-json`
based on GSON and released along with the core library. The serializer and deserializer
are configurable with further GSON options beyond default.

*Note*: Custom deserializers must satisfy a requirements to deliver a deserializable slice
of the original or transformed byte array for every explicit `Serializable` parameter.
Due to the necessity to dynamically put heterogeneous method arguments together, the server
side will first deserialize the data into a `HashMap<String, Serializable>`and then, in a
second iteration, it will use the same deserializer to process byte arrays behind each
`Serializable` value into a concrete type as declared on the method.

## Transport and server-side invocation

Transport layers implement the `ServiceInvoker` interface for the client side and
`ServiceDispatcher` for the server side. Both interfaces, although not related via any
parent-child relation, provide the following same method that is central for the bi-directional
data flow:

	CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] data);

Invoking proxies first compose the method route; then copy the context and put new unique
request Id and the correct content type there; finally, collect method arguments and serialize
them into byte arrays. The three values are then passed into an instance of the above
`ServiceInvoker` interface implementation. This performs an asynchronous network transport
of the data and waits for the completion of the returned future by the server side.

The transport layer transfers the data and context (in form of request/message headers) to
the destination route (which for protocol comptibility may be transformed into a URI on a
given host/port or anything else). The server side transport implementation receives the
request/message, passes its route, headers and data into an instance of the `ServiceDispatcher`
implementation. Based on the route, it finds the matching service implementation instance and
the method to call, deserializes the data correspondingly, creates a new context from the
headers and invokes the method. The same asynchronous process is repeated in reverse for the
response.

The package provides the following three transport layer implementations:

* HTTP(s) POST using `vert.x` in `io.teris.rpc:vertx` (released together with the core)
* JMS using `ActiveMQ` in `io.teris.rpc:jms` (not yet finalized, not released, can be used from source only)
* AMQP using `RabbitMQ` in `io.teris.rpc:amqp` (not yet finalized, not released, can be used from source only)

Using the `vert.x` HTTP implementation and the GSON serializer, the fully configured client-side
service factory that will invoke service methods over HTTP on a corresponding HTTP server
can be instantiated as follows (adding further optional parameters for completeness):

```java
Vertx vertx = Vertx.vertx();

HttpClient httpClient = vertx
	.createHttpClient(new HttpClientOptions()
		.setDefaultHost("localhost")
		.setDefaultPort(8080)
		.setMaxPoolSize(50));

ServiceInvoker invoker = VertxServiceInvoker.builder(httpClient).build();

Serializer serializer = GsonSerializer.builder().build();

Supplier<String> uidGenerator = () -> UUID.randomUUID().toString();

ServiceFactory factory = ServiceFactory.builder()
	.serviceInvoker(invoker)
	.serializer(serializer)
	.uidGenerator(uidGenerator) // optional (default same as above)
	.deserialier("application/json", serializer.deserializer()) // optional
	.build();
```

On the server side, one needs to register services along with implementing instances in
an instance of the `ServiceDispatcher` implementation. Initializing an `VertxServiceRouter`
instance with the dispatcher instance and a `vert.x` router will register HTTP POST
endpoint for all services and their methods on every dispatcher used:

```java
Serializer serializer = GsonSerializer.builder().build();

ServiceDispatcher dispatcher = ServiceDispatcher.builder()
	.serializer(serializer)
	.bind(DataService.class, new DataServiceImpl(dataServiceDependency))
	.bind(OtherService.class, new OtherServiceImpl(otherServiceDependency))
	.build();

Vertx vertx = Vertx.vertx();
Router router = Router.router(vertx);

VertxServiceRouter.builder(router).build()
	.preconditioner(authHandler)
	.route(dispatcher);

vertx.createHttpServer(httpServerOptions)
	.requestHandler(router::accept)
	.listen());
```

Exception thrown during the invocation process are wrapped into `io.teris.rpc.InvocationException`
or `io.teris.rpc.BusinessExcpeption` in case the exception occurs when invoking the actual
service method. References to the cause are dropped from the transport, however, their original
stacktraces are preserved. Exception data will be delivered serialized in the response payload.
The only exception from this case is when the serialization of an (exceptional or normal) response on the
server side throws itself an exception. This exception will be delivered to the client by different
means depending on the transport. In the `vert.x` implementation it will result in the server
500 HTTP code and a text error message. Any middleware (preprocessor) exception should
result in a similar behavior. The resuling future is then completed exceptionally on the transport
layer interface.

It is essential to note that _no checked_ exceptions will cross the remote invocation
boundary and all runtime exceptions will descend from `InvocationException` or `BusinessException`.
This is true even for the case when the service declares and throws an exception of a
particular type. The reason for this is to guarantee that exceptions are always deserializable
on the client side!

## Generic preprocessing (and authentication)

The service dispatcher allows for registration of a series of preprocessors that are executed
(asynchronously) before dispatching to the actual service implementation. Preprocessors are
functions that satisfy the following declaration:

	BiFunction<Context, Entry<String, byte[]>, CompletableFuture<Context>>

Each preprocessor asynchronously receives the context from the previous iteration and can
use its values and the original data to generate new values for the context and/or
validate permissions. The pair of route and incoming data is passed into every preprocesor
along with the context.


## Public APIs

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


### License and copyright

	Copyright (c) 2017. Oleg Sklyar and teris.io. MIT license applies. All rights reserved.
