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

## Service definitions

The APIs are defined by means of `@Service` and `@Name` annotated interfaces. These
provide a full control over the exported service routes (e.g. HTTP endpoints), payload
structure and names. The following defines a service with two endpoints, a synchronous
and an asynchronous ones:

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
`api.data.download`, respectively.

All provided transport implementations are aware of those route definitions and will
automatically expose service endpoints at the server side and route to the correct ones
on the client.

The library support two types of service methods. Asynchronous return a `CompletableFuture`
of a `? extends Serializable` type and synchronous return an instance of
`? extends Serializable` type directly. Internally the library itself is fully asynchronous
and will benefit from non-blocking execution of the implementations.

## Invocation

Service proxy instances can be obtained on the client side from an instance of `ServiceFactory`,
which is parametrized with a `Serializer` and a `ServiceInvoker` responsible for the
transport layer.


### License and copyright

	Copyright (c) 2017. Oleg Sklyar and teris.io. MIT license applies. All rights reserved.
