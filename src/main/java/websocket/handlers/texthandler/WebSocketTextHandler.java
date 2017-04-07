package websocket.handlers.texthandler;

import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

@Controller
public class WebSocketTextHandler extends TextWebSocketHandler {
	Gson gson = new Gson();

	@Autowired(required = false)
	DispatcherServlet servlet;

	@Autowired
	WebApplicationContext ctx;

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		System.out.println("Connected: from " + session.getRemoteAddress());

		HttpSession httpSession = create(session);
		SecurityContext sec = (SecurityContext) httpSession.getAttribute("SPRING_SECURITY_CONTEXT");
		String username = sec.getAuthentication().getName();
		System.out.println(session.getRemoteAddress() + " logged in as " + username);

		super.afterConnectionEstablished(session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		System.out.println("Disconnected: " + session.getRemoteAddress());

		HttpSession httpSession = create(session);
		SecurityContext sec = (SecurityContext) httpSession.getAttribute("SPRING_SECURITY_CONTEXT");
		if (sec != null && sec.getAuthentication() != null) {
			String username = sec.getAuthentication().getName();
		}
		super.afterConnectionClosed(session, status);
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

		try {
			System.out.println("Received text message:" + message.getPayload());
			JsonObject obj = gson.fromJson(message.getPayload(), JsonObject.class);

			if (obj.has("jsonrpc")) {
				System.out.println("text message is a RPC request.");

				JsonObject result = new JsonObject();
				// Check if we have a method parameter
				if (obj.has("method") && obj.get("method").getAsString() != null) {
					String method = obj.get("method").getAsString();

					MockHttpServletRequest req = new MockHttpServletRequest(ctx.getServletContext(), "GET",
							"/api/" + method);

					// make a map to hold all IRequestHandler in the
					// WebApplicationContext
					Map<String, IRequestHandler> mapHandlers = ctx.getBeansOfType(IRequestHandler.class);

					// Extract and eject
					if (obj.has("params")) {
						JsonElement params = obj.get("params");
						parseParameters(params, req);
					}
					if (servlet != null) {
						HttpSession httpSession = create(session);
						req.setSession(httpSession);
						MockHttpServletResponse res = new MockHttpServletResponse();
						servlet.service(req, res);
						String content = res.getContentAsString();
						System.out.println(content);

						if (res.getStatus() == 200) {
							result.add("result", new Gson().fromJson(content, JsonElement.class));

							// if mapHandlers has a IRequestHandler with the
							// given method
							// call its handleRequest()
						} else if (mapHandlers.containsKey(method)) {

							IRequestHandler handler = mapHandlers.get(method);

							result = handler.handleRequest(obj);

						} else {
							JsonObject error = new JsonObject();
							error.addProperty("code", res.getStatus());
							error.addProperty("message", content);
							result.add("error", error);
						}
					} else {
						// WebSocket Servlet depends on Dispatcher servlet which
						// isnt available in the test context.
						throw new IllegalStateException("Context is without servlet");
					}

				} else {
					JsonObject error = new JsonObject();

					error.addProperty("code", 400);
					error.addProperty("message", "Invalid request");
					result.add("error", error);
				}

				System.out.println("sending response.");
				session.sendMessage(buildResponse(obj, result));
			}
		} catch (JsonSyntaxException e) {
			System.out.println(e.getMessage());
		}
	}

	private void parseParameters(JsonElement params, MockHttpServletRequest req) {
		try {

			if (params.isJsonObject()) {
				// For each parameter in the json-rpc call
				for (Entry<String, JsonElement> entry : params.getAsJsonObject().entrySet()) {
					JsonElement el = entry.getValue();
					// Skip null values as spring mvc doesnt support them
					if (!el.isJsonNull()) {
						// JsonPrimitive have a helpfull getAsString function we
						// can use
						if (el.isJsonPrimitive()) {
							req.addParameter(entry.getKey(), el.getAsString());
							// Array values are parsed as double parameters
							/*
							 * Disabled because this messes with deserialization
							 * //TODO: Fix }else if(el.isJsonArray()){ JsonArray
							 * arr = el.getAsJsonArray(); for(int i= 0; i <
							 * arr.size(); i++){ JsonElement el1 = arr.get(i);
							 * if(el1.isJsonPrimitive()){
							 * req.addParameter(entry.getKey(),
							 * el1.getAsString()); // Objects and Arrays are
							 * serialized to Json so we can parse them in the
							 * controllers }else{
							 * 
							 * req.addParameter(entry.getKey(), el1.toString());
							 * } }
							 * 
							 */

							// Objects are serialized to Json so we can parse
							// them in the controllers
						} else {
							req.addParameter(entry.getKey(), el.toString());
						}
					}
				}

			} else {
				System.err.println("We only support named parameters");
			}

		} catch (Exception e) {
			System.out.println("Error parsing params");
			e.printStackTrace();
		}

	}

	public HttpSession create(WebSocketSession session) {
		MockHttpSession httpSession = new MockHttpSession(ctx.getServletContext());

		for (Entry<String, Object> entry : session.getAttributes().entrySet()) {
			httpSession.putValue(entry.getKey(), entry.getValue());

		}
		return httpSession;

	}

	private TextMessage buildResponse(JsonObject original, JsonObject result) {

		JsonObject response = new JsonObject();

		if (original != null) {
			if (original.has("id")) {
				response.add("id", original.get("id"));
			}
			if (original.has("jsonrpc")) {
				response.addProperty("jsonrpc", original.get("jsonrpc").getAsString());
			}
		}
		for (Entry<String, JsonElement> entry : result.entrySet()) {
			response.add(entry.getKey(), entry.getValue());
		}

		TextMessage message = new TextMessage(response.toString());

		return message;
	}
}
