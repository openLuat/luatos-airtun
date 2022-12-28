package com.luatos.airtun.ws;

import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.plugins.mvc.websocket.AbstractWsEndpoint;
import org.nutz.plugins.mvc.websocket.WsHandler;

import com.luatos.airtun.AppCore;

/**
 * 页面websocket入口
 *
 */
@ServerEndpoint(value = "/ws/airtun", configurator = NutWsConfigurator.class)
@IocBean(create = "init")
public class AirTunWsEndpoint extends AbstractWsEndpoint {

	@Inject
	protected AppCore app;
	
	public WsHandler createHandler(Session session, EndpointConfig config) {
		String host = (String) session.getUserProperties().get("Host");
		String clientId = AppCore.toClientId(host);;
		AirTunWsHandler handler = new AirTunWsHandler(clientId, app);
		handler.join(clientId);
		return handler;
	}
	
	public void init() {
		app.setEndpoint(this);
	}
}
