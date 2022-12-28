package com.luatos.airtun;

import org.nutz.boot.NbApp;
import org.nutz.ioc.impl.PropertiesProxy;
import org.nutz.ioc.loader.annotation.*;
import org.nutz.mvc.annotation.*;

import com.luatos.airtun.ws.AirTunWsEndpoint;

@IocBean(create="init", depose="depose")
public class MainLauncher {
    
    @Inject
    protected PropertiesProxy conf;
    @At("/")
    @Ok("->:/index.html")
    public void index() {}
    
    @Inject
    protected AppCore appCore;
    
    @Inject
    protected AirTunWsEndpoint endpoint;
    
    public void init() {
        // NB自身初始化完成后会调用这个方法
    }

    public void depose() {}

    public static void main(String[] args) throws Exception {
        new NbApp().setArgs(args).setPrintProcDoc(false).run();
    }

}
