package com.luatos.airtun.ws;

import org.nutz.lang.util.NutMap;
import org.nutz.plugins.mvc.websocket.handler.SimpleWsHandler;

import com.luatos.airtun.AppCore;
import com.luatos.airtun.bean.LinkMessage;
import com.luatos.airtun.bean.LinkWs;

public class AirTunWsHandler extends SimpleWsHandler {

	protected String clientId;
	protected AppCore app;

	public AirTunWsHandler(String clientId, AppCore app) {
		this.clientId = clientId;
		this.app = app;
	}

	// 发送数据到单个或全部客户端
	public void sendc(NutMap req) {
		LinkMessage msg = new LinkMessage("ws");
		msg.ws = new LinkWs();
		msg.ws.data = req;
		app.publish2client(clientId, msg);
	}
}
