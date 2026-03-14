package com.ncm2flac;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private LinearLayout layoutPermissionDenied;
    private LinearLayout layoutContent;
    private View rootLayout;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sp = getSharedPreferences("app_config", 0);

        // 绑定所有控件
        rootLayout = findViewById(R.id.root_layout);
        layoutPermissionDenied = findViewById(R.id.layout_permission_denied);
        layoutContent = findViewById(R.id.layout_content);
        Button btnAutoScan = findViewById(R.id.btn_auto_scan);
        Button btnManualSelect = findViewById(R.id.btn_manual_select);
        Button btnGetPermission = findViewById(R.id.btn_get_permission);

        // 一键获取权限按钮点击事件
        btnGetPermission.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });

        // 无权限时按钮置灰提示
        View.OnClickListener toPermissionTip = v -> {
            Toast.makeText(this, "请先点击下方按钮获取文件访问权限", Toast.LENGTH_SHORT).show();
        };
        btnAutoScan.setOnClickListener(toPermissionTip);
        btnManualSelect.setOnClickListener(toPermissionTip);

        // 加载自定义背景
        updateBackground();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 自动检查权限
        checkPermission();
    }

    private void checkPermission() {
        if (hasAllFilePermission()) {
            layoutPermissionDenied.setVisibility(View.GONE);
            layoutContent.setVisibility(View.VISIBLE);
            initViewPager();
        } else {
            layoutPermissionDenied.setVisibility(View.VISIBLE);
            layoutContent.setVisibility(View.GONE);
        }
    }

    // 权限判断核心方法
    private boolean hasAllFilePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == getPackageManager().PERMISSION_GRANTED
                    && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == getPackageManager().PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void initViewPager() {
        ViewPager2 viewPager = findViewById(R.id.view_pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(3);

        // 绑定Tab和ViewPager，同步图标和文字状态
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("自动");
                    tab.setIcon(R.drawable.ic_tab_auto);
                    break;
                case 1:
                    tab.setText("手动");
                    tab.setIcon(R.drawable.ic_tab_manual);
                    break;
                case 2:
                    tab.setText("设置");
                    tab.setIcon(R.drawable.ic_tab_setting);
                    break;
            }
        }).attach();
    }

    // 供设置页调用的背景更新方法
    public void updateBackground() {
        String backgroundUri = sp.getString("background_uri", "");
        if (!backgroundUri.isEmpty()) {
            try {
                Uri uri = Uri.parse(backgroundUri);
                InputStream inputStream = getContentResolver().openInputStream(uri);
                rootLayout.setBackgroundDrawable(android.graphics.drawable.Drawable.createFromStream(inputStream, uri.toString()));
                inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
                rootLayout.setBackgroundColor(0xFFFFFFFF);
            }
        } else {
            rootLayout.setBackgroundColor(0xFFFFFFFF);
        }
    }
}
