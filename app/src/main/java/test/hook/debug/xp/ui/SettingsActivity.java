package test.hook.debug.xp.ui;

import android.os.Bundle;
import android.widget.Switch;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import test.hook.debug.xp.utils.Settings;

public class SettingsActivity extends android.app.Activity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Settings.init();
        
        // 创建设置界面布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        // 添加试用表盘开关
        addSwitch(layout, "试用表盘功能", "trial_watchface_enabled", true);
        
        // 添加广告屏蔽开关
        addSwitch(layout, "屏蔽广告", "disable_ad_enabled", true);
        
        // 添加连接保护通知屏蔽开关
        addSwitch(layout, "屏蔽连接保护通知", "disable_keep_link_notify_enabled", true);
        
        // 添加调试日志开关
        addSwitch(layout, "调试日志", "debug_log_enabled", false);
        
        setContentView(layout);
        
        // 设置标题
        setTitle("Wearable Debug 设置");
    }
    
    private void addSwitch(LinearLayout layout, String title, String key, boolean defaultValue) {
        // 创建开关布局
        LinearLayout switchLayout = new LinearLayout(this);
        switchLayout.setOrientation(LinearLayout.HORIZONTAL);
        switchLayout.setPadding(0, 16, 0, 16);
        
        // 标题
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        // 开关
        Switch switchView = new Switch(this);
        boolean value = getSwitchValue(key, defaultValue);
        switchView.setChecked(value);
        
        // 点击监听
        final String prefKey = key;
        switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Settings.setProperty(prefKey, String.valueOf(isChecked));
            Toast.makeText(SettingsActivity.this, 
                isChecked ? title + " 已开启" : title + " 已关闭", 
                Toast.LENGTH_SHORT).show();
        });
        
        switchLayout.addView(titleView);
        switchLayout.addView(switchView);
        layout.addView(switchLayout);
        
        // 添加分隔线
        View divider = new View(this);
        divider.setBackgroundColor(0xFFCCCCCC);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 2));
        layout.addView(divider);
    }
    
    private boolean getSwitchValue(String key, boolean defaultValue) {
        switch (key) {
            case "trial_watchface_enabled":
                return Settings.isTrialWatchFaceEnabled();
            case "disable_ad_enabled":
                return Settings.isDisableAdEnabled();
            case "disable_keep_link_notify_enabled":
                return Settings.isDisableKeepLinkNotifyEnabled();
            case "debug_log_enabled":
                return Settings.isDebugLogEnabled();
            default:
                return defaultValue;
        }
    }
}