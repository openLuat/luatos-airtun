package com.luatos.airtun.ws;

import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.plugins.mvc.websocket.AbstractWsEndpoint;
import org.nutz.plugins.mvc.websocket.WsHandler;

/**
 * 页面websocket入口
 *
 */
@ServerEndpoint(value = "/ws/echo", configurator = NutWsConfigurator.class)
@IocBean
public class EchoEndpoint extends AbstractWsEndpoint {
	
	public WsHandler createHandler(Session session, EndpointConfig config) {
		return new EchoWsHandler();
	}
}
