package com.magpie.android;

import android.app.Application;
import android.content.Context;

/**
 * Holds an application context for the static helpers in {@link GatewayService}
 * (the log ring, the log file) that have no Activity to ask for one.
 */
public class App extends Application {

    /** 本版「后台释放页面内存」的默认值（分钟）。 */
    public static final int SHELL_DEFAULT_WEBC_RELEASE = 5;

    private static Context ctx;

    @Override
    public void onCreate() {
        super.onCreate();
        ctx = getApplicationContext();
        // the Web UI's signing key is generated before anything else, so the
        // first process magpie starts already has one to carry
        Prefs.webKey(this);
        // 大数字的单位（英文 K/M/B 还是中文 万/亿）在这里读一次，
        // 之后全 App 的数字格式化都按它走
        NativeData.setUnitMode(Prefs.unitMode(this));
        // 本版把「后台释放页面内存」的默认改成 5 分钟，并对老用户强制覆盖一次
        // （只做一次，之后用户在设置里改的值照样保留）
        Prefs.migrateWebReleaseOnce(this, SHELL_DEFAULT_WEBC_RELEASE);
    }

    public static Context context() {
        return ctx;
    }
}
