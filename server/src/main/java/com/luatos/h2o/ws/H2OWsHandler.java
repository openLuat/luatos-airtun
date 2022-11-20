package com.luatos.h2o.ws;

import org.nutz.lang.util.NutMap;
import org.nutz.plugins.mvc.websocket.handler.SimpleWsHandler;

import com.luatos.h2o.AppCore;
import com.luatos.h2o.bean.LinkMessage;
import com.luatos.h2o.bean.LinkWs;

public class H2OWsHandler extends SimpleWsHandler {

	protected String clientId;
	protected AppCore app;

	public H2OWsHandler(String clientId, AppCore app) {
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
