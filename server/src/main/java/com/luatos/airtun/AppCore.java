package com.luatos.airtun;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.Session;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.nutz.ioc.Ioc;
import org.nutz.ioc.impl.PropertiesProxy;
import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.json.Json;
import org.nutz.json.JsonFormat;
import org.nutz.lang.ContinueLoop;
import org.nutz.lang.Each;
import org.nutz.lang.ExitLoop;
import org.nutz.lang.Files;
import org.nutz.lang.Lang;
import org.nutz.lang.LoopException;
import org.nutz.lang.Strings;
import org.nutz.lang.random.R;
import org.nutz.log.Log;
import org.nutz.log.Logs;

import com.luatos.airtun.bean.LinkConAck;
import com.luatos.airtun.bean.LinkFile;
import com.luatos.airtun.bean.LinkMessage;
import com.luatos.airtun.ws.AirTunWsEndpoint;

@IocBean(create = "init", depose = "depose")
public class AppCore implements MqttCallback {
	
	public static String VERSION = "1.0-Gift";

	protected static final Log log = Logs.get();

	@Inject
	protected PropertiesProxy conf;
	
	@Inject
	protected Ioc ioc;
	
	
	// 注意, 这个bean虽然是ioc bean, 但不能注入,因为有循环依赖
	protected AirTunWsEndpoint endpoint;

	public MqttClient mqttc;

	public WeakHashMap<String, AsyncContext> acs = new WeakHashMap<String, AsyncContext>();

	public String cacheDir;
	public String dftDir;
	public String fragDir;

	public void init() throws MqttException {
		// NB自身初始化完成后会调用这个方法
		cacheDir = conf.get("airtun.cache.dir", "/opt/airtun/cache");
		dftDir = conf.get("airtun.dft.dir", "/opt/airtun/dft");
		fragDir = conf.get("airtun.frag.dir", "/opt/airtun/frag");
		Files.createDirIfNoExists(cacheDir);
		Files.createDirIfNoExists(dftDir);
		Files.createDirIfNoExists(fragDir);

//    	String broker = "tcp://lbsmqtt.airm2m.com:1883";
		String broker = conf.get("airtun.mqtt.url", "tcp://broker-cn.emqx.io:1883");
		String clientId = conf.get("airtun.mqtt.client_id", R.UU32());
		String username = conf.get("airtun.mqtt.username", R.UU32());
		String password = conf.get("airtun.mqtt.password", R.UU32());
		MemoryPersistence persistence = new MemoryPersistence();
		mqttc = new MqttClient(broker, clientId, persistence);

		// MQTT 连接选项
		MqttConnectOptions connOpts = new MqttConnectOptions();
		connOpts.setUserName(username);
		connOpts.setPassword(password.toCharArray());

		connOpts.setAutomaticReconnect(true);

		mqttc.setCallback(this);

		mqttc.connect(connOpts);
		mqttc.subscribe("$airtun/+/up");
		log.info("airtun ready, version " + VERSION);
	}

	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		log.infof("arrived %s", topic);
		String clientId = topic.split("/")[1];
		if (Strings.isBlank(clientId))
			return;
		clientId = clientId.toLowerCase();
		byte[] buff = message.getPayload();
		if (buff[0] == '{' && buff[buff.length - 1] == '}') {
			try {
				LinkMessage msg = Json.fromJson(LinkMessage.class, new String(buff));
				log.debugf("uplink %s %s", topic, Json.toJson(msg, JsonFormat.compact()));
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
					if (msg.upload.name.contains("/") || msg.upload.name.contains("..")
							|| msg.upload.name.length() > 64)
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
					} catch (Throwable e) {
						log.warn("resp error", e);
					} finally {
						try {
							ac.complete();
						} catch (Throwable e) {
							// 不管咯
						}
					}
					acs.remove(clientId + "_" + msg.resp.id);
					break;
				case "conn":
					Map<String, LinkFile> dfiles = new HashMap<String, LinkFile>();
					if (msg.conn != null) {
						for (Map.Entry<String, LinkFile> ent : msg.conn.files.entrySet()) {
							// 不信任客户端上报的任何数据, 全部防御
							String name = ent.getKey();
							LinkFile lf = ent.getValue();
							if (lf == null || Strings.isBlank(name) || name.contains("/")) {
								continue;
							}
							if (name.contains("..") || lf.size == null || lf.size.intValue() < 1) {
								continue;
							}
							// 检查sha1值本身是否合法
							if (lf.sha1 == null || Strings.isBlank(lf.sha1)) {
								continue;
							}
							// 好了, 开始检查缓存文件
							File f = new File(cacheDir + "/" + clientId + "/" + name);
							if (!f.exists() || f.length() != lf.size.intValue()
									|| !Lang.sha1(f).equalsIgnoreCase(lf.sha1)) {
								// 缓存文件不存在,那就让客户端上传吧
								lf = new LinkFile();
								lf.upload = true;
								dfiles.put(name, lf);
								continue;
							}
						}
					}
					LinkMessage dmsg = new LinkMessage("conack");
					if (!dfiles.isEmpty()) {
						dmsg.conack = new LinkConAck();
						dmsg.conack.files = dfiles;
					}
					publish2client(clientId, dmsg);
					break;
				case "ws" :
					if (endpoint == null)
						endpoint = ioc.get(AirTunWsEndpoint.class);
					endpoint.each(clientId, new Each<Session>() {
						public void invoke(int index, Session ele, int length) throws ExitLoop, ContinueLoop, LoopException {
							endpoint.sendJson(ele.getId(), msg);
						}
					});
					break;
				// 未知的action,就不处理了
				default:
					log.warnf("unkown action %s", msg.action);
					return;
				}
			} catch (Throwable e) {
				log.info("uplink", e);
			}
		} else {
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

	public boolean publish2client(String clientId, LinkMessage msg) {
		JsonFormat jf = JsonFormat.full().setIndentBy("").setCompact(true).setIgnoreNull(true);
		try {
			String topic = "$airtun/" + clientId + "/down";
			String data = Json.toJson(msg, jf);
			log.infof("down %s %s", topic, data);
			mqttc.publish(topic, data.getBytes(), 1, false);
			return true;
		} catch (MqttException e) {
			log.warn("down error", e);
		}
		return false;
	}

	public static String toClientId(String host) {
		if (Strings.isBlank(host) || host.startsWith("127.0.0.1") || host.startsWith("192.168."))
			return "6055F9779010".toLowerCase(); // 暂时固定,测试嘛
		return host.split("\\.")[0].toLowerCase();
	}

	public void depose() throws MqttException {
		if (mqttc != null)
			mqttc.close(true);
	}
}
