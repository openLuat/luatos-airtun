package com.luatos.h2o;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

import javax.servlet.AsyncContext;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.nutz.boot.starter.WebServletFace;
import org.nutz.ioc.impl.PropertiesProxy;
import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.json.Json;
import org.nutz.json.JsonFormat;
import org.nutz.lang.Files;
import org.nutz.lang.Lang;
import org.nutz.lang.Strings;
import org.nutz.lang.random.R;
import org.nutz.lang.util.NutMap;
import org.nutz.log.Log;
import org.nutz.log.Logs;

import com.luatos.h2o.bean.LinkConAck;
import com.luatos.h2o.bean.LinkFile;
import com.luatos.h2o.bean.LinkMessage;
import com.luatos.h2o.bean.LinkReq;

@IocBean(create = "myinit", depose = "mydepose")
public class H2O_AsyncServlet extends HttpServlet implements WebServletFace, MqttCallback {

	private static final long serialVersionUID = 1L;
	
	@Inject
	protected PropertiesProxy conf;
	
	protected static final Log log = Logs.get();
	
	public MqttClient mqttc;
	
	public WeakHashMap<String, AsyncContext> acs = new WeakHashMap<String, AsyncContext>();

	public String cacheDir;
	public String dftDir;
	public String fragDir;
	
	public TreeMap<String, Long> recvTm = new TreeMap<String, Long>();
	
	public TreeMap<String, HashMap<String, byte[]>> uploads = new TreeMap<String, HashMap<String,byte[]>>();
	
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String clientId = toClientId(req.getHeader("Host"));
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
						}
						catch (Throwable e) {
							log.info("bad json", e);
							resp.sendError(403);
							return;
						}
					}
					else if (contenType.contains("form")) {
						// 让容器自行处理
					}
					else {
						msg.req.body = Base64.getEncoder().encodeToString(buff.toByteArray());
					}
				}
				else {
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
			acs.put(clientId + "_" + id, ac);
			if (!publish2client(clientId, msg)) {
				acs.remove(clientId + "_" + id);
				((HttpServletResponse)ac.getResponse()).setStatus(500);
				ac.complete();
			}
		}
		else {
			if (uri.contains("..") || uri.contains(":")) {
				resp.sendError(403);
				return;
			}
			String path = uri;
	    	if (path.endsWith("/"))
	    		path += "index.html";
	    	
	    	// 暂时写死host为 1234567890
	    	String dst = cacheDir + "/" + clientId  + path;
	    	File f = new File(dst).getAbsoluteFile();
	    	if (!f.exists()) {
	    		f = new File(dftDir + "/" + path);
	    		if (!f.exists()) {
	    			resp.sendError(404);
	    			return;
	    		}
	    	}

			resp.setContentLength((int)f.length());
			// 设置一下content-type, 简陋版
			String suffix = Files.getSuffix(f);
			if (suffix.endsWith("html")) {
				resp.setContentType("text/html; charset=utf8");
			}
			else if (suffix.endsWith("css")) {
				resp.setContentType("text/css; charset=utf8");
			}
			else if (suffix.endsWith("json")) {
				resp.setContentType("application/json; charset=utf8");
			}
			else if (suffix.endsWith("js")) {
				resp.setContentType("text/javascript; charset=utf8");
			}
			else if (suffix.endsWith("jpg")) {
				resp.setContentType("image/jpeg");
			}
			else if (suffix.endsWith("jpeg")) {
				resp.setContentType("image/jpeg");
			}
			else if (suffix.endsWith("png")) {
				resp.setContentType("image/png");
			}
			else if (suffix.endsWith("webp")) {
				resp.setContentType("image/webp");
			}
			else if (suffix.endsWith("bmp")) {
				resp.setContentType("image/bmp");
			}
			else if (suffix.endsWith("svg")) {
				resp.setContentType("svg+xml");
			}

			OutputStream out = resp.getOutputStream();
			byte[] buff = new byte[4096];
			try (FileInputStream ins = new FileInputStream(f)){
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

    public void myinit() throws MqttException {
        // NB自身初始化完成后会调用这个方法
    	cacheDir = conf.get("h2o.cache.dir", "/opt/h2o/cache");
    	dftDir = conf.get("h2o.dft.dir", "/opt/h2o/dft");
    	fragDir = conf.get("h2o.frag.dir", "/opt/h2o/frag");
    	Files.createDirIfNoExists(cacheDir);
    	Files.createDirIfNoExists(dftDir);
    	Files.createDirIfNoExists(fragDir);
    	
//    	String broker = "tcp://lbsmqtt.airm2m.com:1883";
    	String broker = conf.get("h2o.mqtt.url", "tcp://broker-cn.emqx.io:1883");
        String clientId = conf.get("h2o.mqtt.client_id", R.UU32());
        String username = conf.get("h2o.mqtt.username", R.UU32());
        String password = conf.get("h2o.mqtt.password", R.UU32());
        MemoryPersistence persistence = new MemoryPersistence();
        mqttc = new MqttClient(broker, clientId, persistence);

        // MQTT 连接选项
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setUserName(username);
        connOpts.setPassword(password.toCharArray());

        connOpts.setAutomaticReconnect(true);
        
        mqttc.setCallback(this);
        
        mqttc.connect(connOpts);
        mqttc.subscribe("$h2o/+/up");
        log.info("mqtt ready");
    }
    
    public boolean publish2client(String clientId, LinkMessage msg) {
    	JsonFormat jf = JsonFormat.full().setIndentBy("").setCompact(true);
		try {
			String topic = "$h2o/" + clientId + "/down";
			String data = Json.toJson(msg, jf);
			log.infof("down %s %s", topic, data);
			mqttc.publish(topic, data.getBytes(), 1, false);
			return true;
		} catch (MqttException e) {
			log.warn("down error", e);
		}
		return false;
    }
    
    @Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		log.infof("arrived %s", topic);
		String clientId = topic.split("/")[1];
		if (Strings.isBlank(clientId))
			return;
		byte[] buff = message.getPayload();
		if (buff[0] == '{' && buff[buff.length - 1] == '}') {
			try {
				LinkMessage msg = Json.fromJson(LinkMessage.class, new String(buff));
				log.debugf("uplink %s %s", topic ,Json.toJson(msg, JsonFormat.compact()));
				if (msg.version != 1) {
					log.info("only version 1 is ok");
					return; // 当前仅处理version=1的上报
				}
				if (Strings.isBlank(msg.action)) {
					log.info("action is emtry, skip");
					return;
				}
				switch (msg.action) {
				// 心跳
				case "ping":
					publish2client(clientId, new LinkMessage("pong"));
					break;
				// upload
				case "upload":
					if (msg.upload == null || msg.upload.body == null || Strings.isBlank(msg.upload.name))
						break;// 防御一下
					if (msg.upload.name.contains("/") || msg.upload.name.contains("..") || msg.upload.name.length() > 64)
						break; // 防御非法路径
					byte[] data = Base64.getDecoder().decode(msg.upload.body);
					if (data == null)
						break;
					if (msg.upload.index == null) {
						// 单文件, 数据不大
						Files.write(cacheDir + "/" + clientId + "/" + msg.upload.name, data);
						break;
					}
					Files.write(fragDir + "/" + clientId + "/" + msg.upload.name + "/" + msg.upload.index, data);
					if (msg.upload.end) {
						ByteArrayOutputStream out = new ByteArrayOutputStream(msg.upload.size);
						for (int i = 0; i < 1000; i++) {
							String tmpPath = fragDir + "/" + clientId + "/" + msg.upload.name + "/" + i;
							File tmp = new File(tmpPath);
							if (!tmp.exists()) {
								break;
							}
							out.write(Files.readBytes(tmp));
						}
						Files.write(cacheDir + "/" + clientId + "/" + msg.upload.name, out.toByteArray());
					}
					break;
				// 响应
				case "resp":
					if (msg.resp == null || Strings.isBlank(msg.resp.id)) {
						log.warn("bad resp payload, skip");
						return;
					}
					AsyncContext ac = acs.get(clientId + "_" + msg.resp.id);
					if (ac == null) {
						log.warnf("no such req id %s_%s", clientId, msg.resp.id);
						return;
					}
					try {
						HttpServletResponse resp = (HttpServletResponse) ac.getResponse();
						if (msg.resp.code != null)
							resp.setStatus(msg.resp.code);
						if (msg.resp.headers != null && !msg.resp.headers.isEmpty()) {
							for (Map.Entry<String, String> header : msg.resp.headers.entrySet()) {
								resp.setHeader(header.getKey(), header.getValue());
							}
						}
						if (msg.resp.body != null) {
							OutputStream out = resp.getOutputStream();
							out.write(Base64.getDecoder().decode(msg.resp.body));
						}
					}
					catch (Throwable e) {
						log.warn("resp error", e);
					}
					finally {
						try {
							ac.complete();
						}
						catch (Throwable e) {
							// 不管咯
						}
					}
					acs.remove(clientId + "_" + msg.resp.id);
					break;
				case "conn":
					List<LinkFile> dfiles = new ArrayList<LinkFile>();
					if (msg.conn != null) {
						for (LinkFile lf : msg.conn.files) {
							// 不信任客户端上报的任何数据, 全部防御
							if (lf == null || Strings.isBlank(lf.name) || lf.name.contains("/")) {
								continue;
							}
							if (lf.name.contains("..") || lf.size == null || lf.size.intValue() < 1) {
								continue;
							}
							// 检查sha1值本身是否合法
							if (lf.sha1 == null || Strings.isBlank(lf.sha1)) {
								continue;
							}
							// 好了, 开始检查缓存文件
							File f = new File(cacheDir + "/" + clientId + "/" + lf.name);
							if (!f.exists() || f.length() != lf.size.intValue() || Lang.sha1(f).equalsIgnoreCase(lf.sha1)) {
								// 缓存文件不存在,那就让客户端上传吧
								lf = new LinkFile();
								lf.upload = true;
								dfiles.add(lf);
								continue;
							}
						}
					}
					LinkMessage dmsg = new LinkMessage("conack");
					if (!dfiles.isEmpty()) {
						dmsg.conack = new LinkConAck();
						dmsg.conack.files = dfiles.toArray(new LinkFile[dfiles.size()]);
					}
					publish2client(clientId, dmsg);
					break;
				// 未知的action,就不处理了
				default:
					log.warnf("unkown action %s", msg.action);
					return;
				}
			} catch (Throwable e) {
				log.info("uplink", e);
			}
		}
		else {
			return;
		}
	}
	
	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		log.infof("deliveryComplete %s", Json.toJson(token.getTopics()));
	}
	
	@Override
	public void connectionLost(Throwable cause) {
		log.info("lose", cause);
	}
    
    public File to_static_path(String clientId, String path) {
    	if (path.contains("..") || path.contains(":"))
    		return null;
    	if (path.endsWith("/"))
    		path += "index.html";
    	
    	// 暂时写死host为 1234567890
    	String dst = cacheDir + "/" + clientId  + path;
    	File f = new File(dst).getAbsoluteFile();
    	if (f.exists()) {
    		return f;
    	}
    	return null;
    }
    
    public String toClientId(String host) {
    	if (Strings.isBlank(host) || host.startsWith("127.0.0.1"))
    		return "1234567890"; // 暂时固定,测试嘛
    	return host.split("\\.")[0];
    }

    public void mydepose() throws MqttException {
    	if (mqttc != null)
    		mqttc.close(true);
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
