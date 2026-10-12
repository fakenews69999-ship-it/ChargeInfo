package com.gage.chargeinfo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Shows live charging info only while the app is on screen.
 * No services, no wake locks, no manifest receivers: everything is
 * registered in onResume and torn down in onPause.
 */
public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BatteryManager bm;
    private Intent lastBattery;
    private TextView big, details;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { lastBattery = i; render(); }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this, 1000); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);
        big = new TextView(this);
        big.setTextSize(40);
        big.setTypeface(Typeface.DEFAULT_BOLD);
        details = new TextView(this);
        details.setTextSize(18);
        details.setLineSpacing(0, 1.3f);
        details.setTypeface(Typeface.MONOSPACE);
        root.addView(big);
        root.addView(details);
        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override protected void onResume() {
        super.onResume();
        lastBattery = registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        handler.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
        handler.removeCallbacks(tick);
    }

    private void render() {
        Intent i = lastBattery;
        if (i == null) return;
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = scale > 0 ? level * 100 / scale : level;
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        int temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        int health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
        String tech = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);

        long nowRaw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        long avgRaw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
        long chargeRaw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        double nowMa = toMa(nowRaw);
        double volts = volt > 100 ? volt / 1000.0 : volt; // mV on most devices
        double watts = Math.abs(nowMa) / 1000.0 * volts;

        big.setText(pct + "%  " + statusName(status));
        StringBuilder s = new StringBuilder();
        s.append("Source:      ").append(plugName(plugged)).append('\n');
        s.append("Current:     ").append(valid(nowRaw) ? fmt("%.0f mA", nowMa) : "n/a").append('\n');
        if (valid(avgRaw) && avgRaw != 0)
            s.append("Avg current: ").append(fmt("%.0f mA", toMa(avgRaw))).append('\n');
        s.append("Voltage:     ").append(fmt("%.3f V", volts)).append('\n');
        if (valid(nowRaw))
            s.append("Power:       ").append(fmt("%.2f W", watts)).append('\n');
        s.append("Temperature: ").append(fmt("%.1f °C", temp / 10.0)).append('\n');
        s.append("Health:      ").append(healthName(health)).append('\n');
        if (valid(chargeRaw) && chargeRaw > 0)
            s.append("Charge:      ").append(chargeRaw / 1000).append(" mAh\n");
        if (Build.VERSION.SDK_INT >= 28) {
            long ttf = bm.computeChargeTimeRemaining();
            if (ttf > 0) s.append("Full in:     ").append(ttf / 60000).append(" min\n");
        }
        if (tech != null) s.append("Technology:  ").append(tech).append('\n');
        s.append("\nSign of current varies by phone (+ or - may mean charging).");
        details.setText(s.toString());
    }

    private static String fmt(String f, double v) { return String.format(Locale.US, f, v); }

    private static boolean valid(long v) { return v != Long.MIN_VALUE && v != Integer.MIN_VALUE; }

    // Most phones report microamps; some report milliamps. Values beyond +/-20000 are assumed microamps.
    private static double toMa(long raw) { return Math.abs(raw) > 20000 ? raw / 1000.0 : raw; }

    private static String statusName(int s) {
        switch (s) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not charging";
            default: return "Unknown";
        }
    }

    private static String plugName(int p) {
        switch (p) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "AC charger";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Wireless";
            case 8: return "Dock";
            case 0: return "Battery";
            default: return "Other (" + p + ")";
        }
    }

    private static String healthName(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over voltage";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
            default: return "Unknown";
        }
    }
}
