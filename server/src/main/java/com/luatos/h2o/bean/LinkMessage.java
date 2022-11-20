package com.luatos.h2o.bean;

public class LinkMessage {

	public int version = 1;
	public String action;
	public LinkConn conn;
	public LinkConAck conack;
	public LinkUpload upload;
	public LinkReq req;
	public LinkResp resp;
	public LinkWs ws;
	
	public LinkMessage() {
	}
	

	public LinkMessage(String action) {
		this.action = action;
	}
}
