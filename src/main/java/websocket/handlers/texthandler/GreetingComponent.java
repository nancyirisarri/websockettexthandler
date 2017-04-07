package websocket.handlers.texthandler;

import org.springframework.stereotype.Component;

import com.google.gson.JsonObject;

@Component
public class GreetingComponent implements IRequestHandler {

	public JsonObject handleRequest(JsonObject requestBody) {

		JsonObject myString = new JsonObject();

		myString.addProperty("result", "Hello from GreetingComponent!");

		return myString;

	}

}
