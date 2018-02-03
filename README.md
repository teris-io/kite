![kite logo](https://raw.githubusercontent.com/teris-io/kite/master/kite.png)

# kite - service-based RPC, public APIs and PubSub in Java

[![Build Status](https://travis-ci.org/teris-io/kite.svg?branch=master)](https://travis-ci.org/teris-io/kite)
[![Code Coverage](https://img.shields.io/codecov/c/github/teris-io/kite.svg)](https://codecov.io/gh/teris-io/kite)

`kite` is a collection of reactive application messaging libraries that aim at 
providing high level abstractions for PubSub, RPC and public API definitions with 
minimal dependencies. Both PubSub and RPC (of which API defintion is a subset) provide 
a clean separation between the service and message definition, arguments/result/message 
(de)serialization and actual transport layer. The latter two injectable at the 
application wiring stage making client ans server implementations agnostic to 
serialization and transport and relying on POJO service and message defintions only.

All interfaces and implementations are fully reactive including the RPC and (de)serialization.

The collections consist of the following production-ready libraries (released to
`jcenter`):

- [*] `kite` -- the core defintion library. Provides `@Service`, `@Name`, `Context` as well as 
 `Serializer` and `Deserializer` interfaces;
- [*] `kite-rpc` -- the RPC and public API implementation with pluggable serialization and 
 transport. Provides the `ServiceFactory` interface and implementation to obtain client
 side service proxies, the `ServiceExporter` interface and implementation to export
 service implementation on the server side to a given transport and the `ServiceInvoker`
 interface used in conjuncion with the given transport to construct instances of 
 `ServiceFactory`. Plain Java 8 w/o further dependencies;
- [*] `kite-gson` -- the (default) implementation of JSON (de)serialization with `Gson.
- [*] `kite-rpc-vertx` -- the Vert.x based HTTP transport layer for the client and server
 sides. Provides `HttpServiceInvoker` that implements `ServiceInvoker` over HTTP(S) 
 and `HttpServiceExporter` that exports service implementations bound to one or 
 a few generic `ServiceExporter`s as HTTPS POST endpoints;
 
The following libraries are under development or not yet ready for production 
(only available as source from GitHub, not released to `jcenter`):

- [ ] `kite-fasterxml` -- a JSON (de)serializer implementation based on the FasterXML
 `ObjectMapper` rather than `Gson`. The implementation has two outstanding gaps:
 enum name-based deserialzation does not work without a `toString` method, encodings
 other than UTF-8 are not supported for strings;
- [*] `kite-rpc-amqp` -- a provisional RPC implementation for the RabbitMQ variant of `AMQP`.
 The implementation is fully functional, however, more work needs to be done on
 the reconnection and parameter tuning;
- [*] `kite-rpc-jms` -- a provisional RPC implementation for the JMS1.1. The integration test
 is performed with ActiveMQ.
- [ ] `kite-pubsub` -- under development, not yet publicly available (ETA March 2018);
- [ ] `kite-pubsub-vertx` -- under development, not yet publicly available (ETA April 2018);
- [ ] `kite-pubsub-amqp` -- under development, not yet publicly available (ETA April 2018);
- [ ] `kite-pubsub-vertx` -- under development, not yet publicly available (ETA March 2018);


### Obtaining the library

Get it with gradle:

    repositories.jcenter()

    dependencies {
      compile("io.teris.kite:kite:+")
      compile("io.teris.kite:kite-rpc:+")
      // add these to inject implementation when wiring up
      compile("io.teris.kite:kite-gson:+")
      compile("io.teris.kite:kite-rpc-vertx:+")
    }

Or download the jars directly from the [jcenter repository](http://jcenter.bintray.com/io/teris/kite/).

### Building and testing

In order to run all integration tests a `rabbitmq` broker is expected to be running 
on `localhost:5672` using the default guest account. The easiest way to get it 
deployed is by using the official `rabbitmq` alpine docker image (this is exactly 
what the Travis CI deployment script is doing):

	docker run -it -p 5672:5672 rabbitmq:alpine

Vert.x and ActiveMQ integration tests will start their server/broker from within 
the corresponding tests. Given a deployed `rabbitmq` broker one can build, test
and deploy the binaries to a local Maven repository for reuse with (drop tasks which
you do not want to execute):

	./gradlew clean build test integration coverage install

Without the `rabbitmq` installed, add `--continue` to continue upon encounterring 
a failed test.

## RPC and public APIs

APIs are defined by declaring public interfaces annotated with `@io.teris.kite.Service`. 
In order to enable predictable and user-friend payload definition for public APIs,
service method arguments must also be annotated with `@io.teris.kite.Name`. Java
compiler by default erases actual argument names and to have no dependency on compiler
settings the `@Name` annotaton has to be used instead. 

The two annotations also control the way invocation routes are composed yielding
a route per method of each service. The routes are dot-separated lower-case
strings that serve as routing keys for AMQP, message filters for JMS or HTTP URIs
having substituted dots for forward slashes). 

The library support two types of service methods. Asynchronous methods return a 
`CompletableFuture` of `Void` or a type extending `Serializable`. Synchronous 
methods return an instance of a type extending `Serializable` directly, or
can be declared void.  

Internally the library is fully reactive and will benefit from non-blocking 
execution of the implementations where possible. 

**Important**: any service method returning a `CompletableFuture` must not block 
in the implementation as the library will *not* spawn a thread for such methods,
synchronous methods are always executed via `ExecutorService` even if they are
returning void and are internally non blocking.

Service method arguments must take at least one argument, `io.teris.rpc.Context`,
that serves to pass request headers (bi-directionally). Further arguments must 
explicitly implement `Serializable` and be concrete classes. The same requirement 
is applied to generic types, arrays and varargs, all of which are supported
as concrete serializable types. Wildcards in generic are not supported. 

All method arguments after the context, must be annotated with `@io.teris.kite.Name` 
even if the compiler options `-parameters` is on.

Services are assumed to accept and return serializable POJOs, therefore interfaces 
are not supported, both as arguments and as return types (this is the case even 
for standard collections, of which the interfaces are in fact not serializable 
even though most JSON deserializers support them).

The compliance of the declaration to above rules is checked on the server side
within the `ServiceExporter` builder when attaching services for export. On the
client side, it is checked when obtaining a proxy instance from the factory.
Further checks are performed during the invocation. Any exception in such a
check will result in a client side exception or an exceptionally completed future.

For plain HTTP clients contacting the public API declared using the above method,
technical errors will result in HTTP500, authentication errors in HTTP403 and
all busines errors (exceptions during the service implementation invocation) in
HTTP200 with an error field in the payload.

The following defines a service with two endpoints, a synchronous and an asynchronous ones:

```java
package com.company.api;

@Service(replace="com.company")
public interface DataService {

    CompletableFuture<Void> upload(Context context, @Name("data") HashMap<String, Double> data);

    Double download(Context context, @Name("key") String key);
}
```

Each service method receives an independent route. By default it is composed from 
the package name, service class name (w/o the Service suffix) and method name, 
all lower case and dot-separated. 

There is a mechanism to override these defaults redefining the routes fully or 
partially. The `@Service` annotation takes two optional arguements `replace` 
(what to replace in the default route) and `value` (what to replace it for). By
default nothing is replaced. If only `replace` is provided, the matching part 
will be removed (replaced with nothing). If only the `value` is provided, the 
fully route will be replaced with ìt. Thus, the above declaration yields the 
following routes, `api.data.upload` and `api.data.download`, respectively. The
`@Name` annotation applied to a method allows to fully replace the last part
of the route (the method name) with a new one. E.g. 
`@Name("import") void importPrices(...` will be exported as `{prefix}.import`.

Public inner interfaces are fully supported for service declaration and their 
holder class name becomes a part of the route (preserving the `Service` suffix 
on the holder if any).

All provided transport implementations are aware of those route definitions and 
will automatically expose service endpoints at the server side and route to the 
correct ones on the client. For the HTTP implementation an optional prefix 
can be added to the URI on both server and client sides.

The client side `ServiceFactory` and the server sie `ServiceExporter` will 
validate method/service declaration before the invocation and at the time of 
binding a service implementation. 


### Client-side invocation

Service proxy instances are obtained from an instance of `ServiceFactory`,
which is parametrized using an instance of `Serializer` that transfers service 
method arguments into a byte array and a transport specific client side instance 
of `ServiceInvoker`. Content type specific deserializers can additionally be 
registered for turning the response byte array into a response structure based
on the reported content type. Assuming the content type of the response is the 
same as of the request, the default deserializer is provided by the registered 
serializer.

`ServiceFactory` instances can be constructed for example in the following manner:

```java
ServiceFactory factory = ServiceFactory.invoker(httpServiceInvoker)
	.serializer(gsonSerializer)
	.deserializer("application/json", fasterXmlDeserializer)
	.uidGenerator(() -> UUID.randomUUID().toString())
	.build();
```

The only two required arguments are the invoker and serializer though:

```java
ServiceFactory factory = ServiceFactory.invoker(httpServiceInvoker)
	.serializer(JsonSerializer.builder().build())
	.build();
```

Service proxies are then obtained by calling `newInstance` on the factory:

```java
DataService dataService = factory.newInstance(DataService.class);

Double value = dataService.download(new Context(), "key");
```

### Transport and server-side invocation

Transport implementations must provide a client side `ServiceInvoker` implementation
and a server side service exporter that accept instances of `ServiceExporter`
and export transport specific endpoints for each route found in each service exporter.
 
Both interfaces, although not related by any parent-child relation, provide the 
following same method that is central for the bi-directional data flow:

	CompletableFuture<Entry<Context, byte[]>> call(@Nonnull String route, @Nonnull Context context, @Nullable byte[] data);

Invoking proxies first compose the method route; then copy the context and put new unique
request Id and the correct content type there; collect method arguments and serialize
them into byte arrays; finally, the three values are passed into a transport specific
instance of `ServiceInvoker` for sending it to the server (directly or via some sort
of broker or reverse proxy). The future is completed when the transport receives a
corresponding response from the server. In case of HTTP implementation the HTTP
request itself is synchronous and is kept open for the duration of execution. JMS and
AMQP implementations are fully asynchrnous and are based on message publishing on a
shared corrlation Id.

The transport layer transfers the data and the context (in form of request/message 
headers) to the destination route (which for protocol compatibility may be 
transformed into a URI on a given host/port or anything else). The server side transport
receives the request/message and passes its route, headers and data into an instance of 
`ServiceExporter`. Based on the route, the latter finds the matching service 
implementation instance and method to call, deserializes the data, creates a new 
context from the headers and invokes the method. 

The same process is repeated in reverse for the response.

Using the `vert.x` HTTP implementation and the GSON serializer, the fully configured 
client side service factory that invokes service methods over HTTP can be instantiated 
as follows (adding further optional parameters for completeness):

```java
HttpClient httpClient = Vertx.vertx().createHttpClient(new HttpClientOptions()
	.setDefaultHost("localhost")
	.setDefaultPort(8080)
	.setMaxPoolSize(50));

ServiceInvoker invoker = HttpServiceInvoker.httpClient(httpClient).build();

ServiceFactory factory = ServiceFactory.invoker(invoker)
	.serializer(JsonSerializer.builder().build())
	.build();
```

The server side, correspondingly:

```java
ServiceExporter exporter1 = ServiceExporter.serializer(JsonSerializer.builder().build())
	.preprocessor(authenticator1)
	.export(DataService1.class, new DataService1Impl(dataServiceDependency))
	.export(DataService2.class, new DataService2Impl(dataServiceDependency));

ServiceExporter exporter2 = ServiceExporter.serializer(JsonSerializer.builder().build())
	.preprocessor(authenticator2)
	.export(OtherService.class, new OtherServiceImpl(otherServiceDependency));

HttpServiceExporter httpExporter = HttpServiceExporter.router(Vertx.vertx())
	.export(exporter1)
	.export(exporter2);

vertx.createHttpServer(httpServerOptions)
	.requestHandler(httpExporter.router()::accept)
	.listen());
```

Here, the `exporter1` and `exporter2` are exported via the same `HttpServiceExporter`,
but each define a different authenticating preprocessor (e.g. two different
authentication methods).

Exception thrown during the invocation process are wrapped into `io.teris.kite.rpc.InvocationException`
or `io.teris.kite.rpc.BusinessExcpeption`. Their constructors are not publicly 
exported and can only be used from within the RPC mechanism. These exceptions are
fully transportable over the serialization mechanism and are rethrown on
the client side with the original stack trace. If the client is a non-library
component, e.g. a CURL HTTP request to the public API, these excpetions are
transformed in the payload field "exception" (assuming JSON payload) and 
additionally the field "errorMessage" contains the actual text message only. The
HTTP status code is in this case < 400.
 
Exceptions occurring in the preprocessors or during the invocation processing, but
not related to service definition correctness, deserialization or business logic,
are delivered via different means (that is not serialized in the payload) and are
transport specific. The following cases are supported:

* a public `AuthenticationException` is delivered as a text message and is rethrown
 on the client side as `AuthenticationException`. For HTTP it is signalled by HTTP403;
* a server-side `NotFoundException` is delivered as a text message and is rethrown
 on the client side as `NotFoundException`. Currently it is only implemented by the
 HTTP transport and is signlled by HTTP404;
* other exceptions are delivered as a text message and are rethrown on the client
 side as `TechnicalException`. For HTTP they are signalled by HTTP500.

It is essential to note that _no checked_ exceptions will cross the remote invocation
boundary and all runtime exceptions will descend from `InvocationException` or 
`BusinessException`. This is true even for the case when the service declares and 
throws an exception of a particular type. The reason for this is to guarantee that 
exceptions are always deserializable on the client side without futher dependencies!

## Generic preprocessing (and authentication)

The service exporter allows for the registration of a series of preprocessors that 
are executed before dispatching to the actual service implementation. Preprocessors are
functions that satisfy the following declaration and must be non-blocking:

	BiFunction<Context, Entry<String, byte[]>, CompletableFuture<Context>>

Each preprocessor asynchronously receives the context, the route and raw data from 
the previous iteration and can use them to generate new values for the context and/or
validate permissions. The pair of the route and incoming data is passed into every 
preprocesor along with the evolving context.

The preprocessors are executed sequentially (as a next completion stage) in the 
order of their registration on the exporter. Any preprocessor completing exceptionally
will result in aborting the chain and responding to the original request before the
actual method call can be made. With one exception all exceptions will be rethrown 
on the client side as `TechnicalException` using the original exception message.

One can use preprocessor to implement request authentication and authorisation.
It is advisable to throw an `AuthenticationException` in case of authentication
errors in this case as they have a special treatment in the transport.


### Public APIs

Using the provided GSON-based serializer and the `vert.x` HTTP transport layer 
implementation, every service becomes an API that has an easily deductable structure: 

* all requests use the POST method
* the URI is defined by the service method route (substituting . for /) with an
 optional prefix
* context is written as is into request headers, returning context into response headers
* the request payload is a JSON object with attributes being method arguments as
 they are named in the definition
* the response is HTTP200 with JSON payload containing the "payload" field of the same type
 as method return type (or null if `void` or `CompletableFuture<Void>`)
* in case of `BusinessException` or `TechnicalException` the response is HTTP200
 with JSON payload containing a non-null "errorMessage" (and "exception") field
* in case of any other exception the response is one of HTTP403, HTTP404, HTTP500
 or other HTTP error status codes as generated by the generic HTTP protocol.

For example, the API endpoint for the `DataService.download` method declared
above will be defined as follows:

	REQUEST:
		POST /api/data/download
		HEADERS: X-Request-Id, Content-Type + context
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

Service requests that contain no arguments beside `Context` will be performed with an empty
request body. Service responses that respond to `void` or `CompletableFuture<Void>` and contain no
exception will contain an empty response body. Both are treated as normal, non-erroneous cases.


## Serialization in RPC and PubSub

Serialization and deserialization are intergral parts of remote messaging. In the RPC 
the client serializes arguments of outgoing requests and deserializes incoming results 
while the server deserializes incoming requests and serializes outgoing responses.
In PubSub the publisher serializes the message while the subscriber deserializes it.

Serialization is pluggable via instances implementing the `io.teris.kite.Serializer`

```java
@Nonnull
<CT extends Serializable> CompletableFuture<byte[]> serialize(@Nonnull CT value);
```

and `io.teris.kite.Deserializer` interfaces

```java
@Nonnull
<CT extends Serializable> CompletableFuture<CT> deserialize(@Nonnull byte[] data, @Nonnull Class<CT> clazz);

@Nonnull
<CT extends Serializable> CompletableFuture<CT> deserialize(@Nonnull byte[] data, @Nonnull Type type);
```

For the case of multiple registered deserializers, the content type transmitted in
`context` (or in the header) is used to decide which deserializer to use. Serialization,
on the other hand, is only possible with one and the same serialize registered at
the time of constructing the trasport element.

**Important**: for RPC custom deserializers must satisfy a requirements to deliver 
a fully deserializable slice of the original or transformed byte array for every 
explicit `Serializable` declaration. This is the only exception from the response
type: wherever it contains the `Serializable` interface the original slice of byte
array will be delivered. This functionality is used to deserialize RPC service
method arguments when they arrive on the server side. The structure of arguments
is dynamic and cannot be described by a specific class (even if all the individual
element types are known at runtime), so the deserialization is performed into
a map of argument name to `Serializable` and then every argument is deserialized
into its concrete type independently. 


### License and copyright

	Copyright (c) 2017-2018. Oleg Sklyar and teris.io. All rights reserved. MIT license applies
