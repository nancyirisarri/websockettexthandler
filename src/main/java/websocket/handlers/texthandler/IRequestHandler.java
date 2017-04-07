package org.ordina.websockettexthandler;

import com.google.gson.JsonObject;

public interface IRequestHandler {

	public JsonObject handleRequest(JsonObject requestBody);

}
