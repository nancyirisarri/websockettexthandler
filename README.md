Part of a REST web application that uses JSON-RPC based on Java and Spring.

Contains an interface `IRequestHandler` that can be implemented to handle HTTP requests with JSON format. One component that implements it `GreetingComponent` is provided as an example.

The servlet controller `WebSocketTextHandler` dispatches the text messages. The controller tries calling a service to handle the request. All the `IRequestHandler` components are stored in a `Map`. If the service response is not successful, then it looks for the right component in the map.
