package com.luatos.h2o.servlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.TreeMap;

import javax.servlet.AsyncContext;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.nutz.boot.starter.WebServletFace;
import org.nutz.ioc.impl.PropertiesProxy;
import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.json.Json;
import org.nutz.lang.Files;
import org.nutz.lang.Strings;
import org.nutz.lang.random.R;
import org.nutz.lang.util.NutMap;
import org.nutz.log.Log;
import org.nutz.log.Logs;

import com.luatos.h2o.AppCore;
import com.luatos.h2o.bean.LinkMessage;
import com.luatos.h2o.bean.LinkReq;

@IocBean
public class H2O_AsyncServlet extends HttpServlet implements WebServletFace {

	private static final long serialVersionUID = 1L;

	@Inject
	protected PropertiesProxy conf;

	protected static final Log log = Logs.get();

	@Inject
	protected AppCore app;

	public TreeMap<String, Long> recvTm = new TreeMap<String, Long>();

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String clientId = AppCore.toClientId(req.getHeader("Host"));
		if (clientId == null) {
			resp.sendError(404);
			return;
		}

		recvTm.put(clientId, Long.valueOf(System.currentTimeMillis()));

		String uri = req.getRequestURI();
		if (uri.startsWith("/api/")) {
			String id = R.UU32().toLowerCase().substring(0, 6);
			LinkMessage msg = new LinkMessage();
			msg.action = "req";
			msg.req = new LinkReq();
			msg.req.id = id;
			msg.req.uri = req.getRequestURI();
			msg.req.method = req.getMethod().toUpperCase();
			msg.req.headers = new HashMap<String, String>();
			int bodySize = req.getContentLength();
			if (bodySize > 2048) {
				log.warn("reject big req body");
				resp.sendError(403);
				return;
			}
			if (bodySize > 0) {
				ByteArrayOutputStream buff = new ByteArrayOutputStream(bodySize);
				InputStream ins = req.getInputStream();
				byte[] tmp = new byte[4096];
				while (true) {
					int len = ins.read(tmp);
					if (len < 0)
						break;
					buff.write(tmp, 0, len);
				}
				String contenType = req.getHeader("Content-Type");
				if (!Strings.isBlank(contenType)) {
					if (contenType.contains("json")) {
						try {
							msg.req.json = Json.fromJson(NutMap.class, new String(buff.toByteArray()));
						} catch (Throwable e) {
							log.info("bad json", e);
							resp.sendError(403);
							return;
						}
					} else if (contenType.contains("form")) {
						// 让容器自行处理
					} else {
						msg.req.body = Base64.getEncoder().encodeToString(buff.toByteArray());
					}
				} else {
					msg.req.body = Base64.getEncoder().encodeToString(buff.toByteArray());
				}
			}
			msg.req.form = new NutMap();
			Enumeration<String> names = req.getParameterNames();
			while (names.hasMoreElements()) {
				String name = names.nextElement();
				msg.req.form.put(name, req.getParameter(name));
			}

			// 处理一下部分字段,减少不必要的数据量
			if (msg.req.form.isEmpty())
				msg.req.form = null;
			if (msg.req.headers.isEmpty())
				msg.req.headers = null;

			AsyncContext ac = req.startAsync();
			app.acs.put(clientId + "_" + id, ac);
			if (!app.publish2client(clientId, msg)) {
				app.acs.remove(clientId + "_" + id);
				((HttpServletResponse) ac.getResponse()).setStatus(500);
				ac.complete();
			}
		} else {
			if (uri.contains("..") || uri.contains(":")) {
				resp.sendError(403);
				return;
			}
			String path = uri;
			if (path.endsWith("/"))
				path += "index.html";

			String dst = app.cacheDir + "/" + clientId + path;
			File f = new File(dst).getAbsoluteFile();
			if (!f.exists()) {
				f = new File(app.dftDir + "/" + path);
				if (!f.exists()) {
					resp.sendError(404);
					return;
				}
			}

			resp.setContentLength((int) f.length());
			// 设置一下content-type, 简陋版
			String suffix = Files.getSuffix(f);
			if (suffix.endsWith("html")) {
				resp.setContentType("text/html; charset=utf8");
			} else if (suffix.endsWith("css")) {
				resp.setContentType("text/css; charset=utf8");
			} else if (suffix.endsWith("json")) {
				resp.setContentType("application/json; charset=utf8");
			} else if (suffix.endsWith("js")) {
				resp.setContentType("text/javascript; charset=utf8");
			} else if (suffix.endsWith("jpg")) {
				resp.setContentType("image/jpeg");
			} else if (suffix.endsWith("jpeg")) {
				resp.setContentType("image/jpeg");
			} else if (suffix.endsWith("png")) {
				resp.setContentType("image/png");
			} else if (suffix.endsWith("webp")) {
				resp.setContentType("image/webp");
			} else if (suffix.endsWith("bmp")) {
				resp.setContentType("image/bmp");
			} else if (suffix.endsWith("svg")) {
				resp.setContentType("svg+xml");
			}

			OutputStream out = resp.getOutputStream();
			byte[] buff = new byte[4096];
			try (FileInputStream ins = new FileInputStream(f)) {
				while (true) {
					int len = ins.read(buff);
					if (len < 1)
						break;
					out.write(buff, 0, len);
				}
			}
			out.flush();
		}
	}

	@Override
	public boolean isAsyncSupported() {
		return true;
	}

	@Override
	public String getName() {
		return "h2o";
	}

	@Override
	public String getPathSpec() {
		return "/*";
	}

	@Override
	public Servlet getServlet() {
		return this;
	}

}
