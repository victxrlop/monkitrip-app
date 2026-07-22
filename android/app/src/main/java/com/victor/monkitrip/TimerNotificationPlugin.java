package com.victor.monkitrip;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "TimerNotification")
public class TimerNotificationPlugin extends Plugin {

    @PluginMethod
    public void start(PluginCall call) {
        String title = call.getString("title", "Monkitrip");
        Integer durationSecs = call.getInt("durationSecs", 0);
        String colorHex = call.getString("color", "#A87EF5");

        Context context = getContext();
        Intent serviceIntent = new Intent(context, TimerForegroundService.class);
        serviceIntent.setAction(TimerForegroundService.ACTION_START);
        serviceIntent.putExtra("title", title);
        serviceIntent.putExtra("durationSecs", (int) durationSecs);
        serviceIntent.putExtra("color", colorHex);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        JSObject ret = new JSObject();
        ret.put("started", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void stop(PluginCall call) {
        Context context = getContext();
        Intent serviceIntent = new Intent(context, TimerForegroundService.class);
        serviceIntent.setAction(TimerForegroundService.ACTION_STOP);
        context.startService(serviceIntent);

        JSObject ret = new JSObject();
        ret.put("stopped", true);
        call.resolve(ret);
    }
}
