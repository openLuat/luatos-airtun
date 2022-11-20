package com.luatos.h2o.bean;

import java.util.Map;

import org.nutz.lang.util.NutMap;

public class LinkReq {

	public String id;
	public String method;
	public String uri;
	public Map<String, String> headers;
	public String body;
	public NutMap json;
	public NutMap form;
}
