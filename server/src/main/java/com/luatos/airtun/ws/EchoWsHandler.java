package com.luatos.airtun.ws;

import org.nutz.lang.util.NutMap;
import org.nutz.plugins.mvc.websocket.handler.AbstractWsHandler;

public class EchoWsHandler extends AbstractWsHandler {

	public EchoWsHandler() {
		super("");
	}

	public void echo(NutMap params) {
		params.put("server", "airtun");
		endpoint.sendJson(session.getId(), params);
	}
}
