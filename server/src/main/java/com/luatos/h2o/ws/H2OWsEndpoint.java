package com.luatos.h2o.ws;

import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.plugins.mvc.websocket.AbstractWsEndpoint;
import org.nutz.plugins.mvc.websocket.WsHandler;

import com.luatos.h2o.AppCore;

/**
 * 页面websocket入口
 *
 */
@ServerEndpoint(value = "/ws/h2o", configurator = NutWsConfigurator.class)
@IocBean
public class H2OWsEndpoint extends AbstractWsEndpoint {

	@Inject
	protected AppCore app;
	
	public WsHandler createHandler(Session session, EndpointConfig config) {
		String host = (String) session.getUserProperties().get("Host");
		String clientId = AppCore.toClientId(host);;
		H2OWsHandler handler = new H2OWsHandler(clientId, app);
		handler.join(clientId);
		return handler;
	}
}
