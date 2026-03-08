package com.ncmconverter.app;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {
    private static final String AUTO_FOLDER = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/netease/cloudmusic/Music/";
    private static final String DEFAULT_OUTPUT_FOLDER = Environment.getExternalStorageDirectory().getAbsolutePath() + "/NCM2FLAC/";
    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private static final int REQUEST_MANAGE_STORAGE = 200;
    private static final int REQUEST_FILE_SELECT = 300;
    private static final byte[] NCM_KEY = "163 key(Don't modify)".getBytes();

    private RadioGroup bottomNav;
    private RadioButton btnAuto, btnManual;
    private FrameLayout container;
    private ListView autoListView;
    private List<String> autoFileList = new ArrayList<>();
    private EditText manualPathInput, outputPathInput;
    private String tempFilePath = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container = new FrameLayout(this);
        container.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        root.addView(container);
        bottomNav = new RadioGroup(this);
        bottomNav.setOrientation(RadioGroup.HORIZONTAL);
        bottomNav.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setBackgroundColor(0xFFF5F5F5);
        btnAuto = new RadioButton(this);
        btnAuto.setText("自动");
        btnAuto.setId(1);
        btnAuto.setChecked(true);
        btnAuto.setButtonDrawable(null);
        btnAuto.setGravity(Gravity.CENTER);
        btnAuto.setPadding(0, 16, 0, 16);
        RadioGroup.LayoutParams navParams = new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        btnAuto.setLayoutParams(navParams);
        bottomNav.addView(btnAuto);
        btnManual = new RadioButton(this);
        btnManual.setText("手动");
        btnManual.setId(2);
        btnManual.setButtonDrawable(null);
        btnManual.setGravity(Gravity.CENTER);
        btnManual.setPadding(0, 16, 0, 16);
        btnManual.setLayoutParams(navParams);
        bottomNav.addView(btnManual);
        root.addView(bottomNav);
        setContentView(root);
        bottomNav.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == 1) {
                    showAutoPage();
                } else if (checkedId == 2) {
                    showManualPage();
                }
            }
        });
        checkManageStoragePermission();
    }

    private void showAutoPage() {
        container.removeAllViews();
        autoListView = new ListView(this);
        autoListView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(autoListView);
        autoListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final String fileName = autoFileList.get(position);
                convertAutoFile(fileName);
            }
        });
        if (hasManageStoragePermission()) {
            autoFileList.clear();
            scanAutoFilesRecursive(new File(AUTO_FOLDER));
        }
    }

    private void showManualPage() {
        container.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout manualLayout = new LinearLayout(this);
        manualLayout.setOrientation(LinearLayout.VERTICAL);
        manualLayout.setPadding(32, 32, 32, 32);
        scroll.addView(manualLayout);
        TextView label1 = new TextView(this);
        label1.setText("NCM 文件路径：");
        label1.setTextSize(16);
        manualLayout.addView(label1);
        manualPathInput = new EditText(this);
        manualPathInput.setHint("例如：/Download/test.ncm");
        manualPathInput.setText(AUTO_FOLDER);
        manualLayout.addView(manualPathInput);
        Button selectBtn = new Button(this);
        selectBtn.setText("选择文件");
        selectBtn.setPadding(0, 16, 0, 0);
        selectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, "选择NCM文件"), REQUEST_FILE_SELECT);
            }
        });
        manualLayout.addView(selectBtn);
        TextView label2 = new TextView(this);
        label2.setText("输出路径：");
        label2.setTextSize(16);
        label2.setPadding(0, 16, 0, 0);
        manualLayout.addView(label2);
        outputPathInput = new EditText(this);
        outputPathInput.setHint("例如：/NCM2FLAC/");
        outputPathInput.setText(DEFAULT_OUTPUT_FOLDER);
        manualLayout.addView(outputPathInput);
        Button convertBtn = new Button(this);
        convertBtn.setText("开始转换");
        convertBtn.setPadding(0, 32, 0, 0);
        convertBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String ncmPath = manualPathInput.getText().toString().trim();
                final String outPath = outputPathInput.getText().toString().trim();
                if (ncmPath.isEmpty() || outPath.isEmpty()) {
                    showToast("路径不能为空");
                    return;
                }
                convertManualFile(ncmPath, outPath);
            }
        });
        manualLayout.addView(convertBtn);
        container.addView(scroll);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE_SELECT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    // 核心修复：直接从Uri读取文件流，复制到临时文件，再用临时文件路径
                    File tempFile = File.createTempFile("temp_", ".ncm", getCacheDir());
                    InputStream is = getContentResolver().openInputStream(uri);
                    OutputStream os = new FileOutputStream(tempFile);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = is.read(buffer)) > 0) {
                        os.write(buffer, 0, length);
                    }
                    os.close();
                    is.close();
                    tempFilePath = tempFile.getAbsolutePath();
                    manualPathInput.setText(tempFilePath);
                    showToast("文件已加载，可开始转换");
                } catch (Exception e) {
                    e.printStackTrace();
                    showToast("无法加载文件，请重试");
                }
            }
        } else if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (hasManageStoragePermission()) {
                showAutoPage();
            } else {
                showToast("未开启所有文件访问权限，功能将受限");
                finish();
            }
        }
    }

    private boolean hasManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Method method = Environment.class.getMethod("isExternalStorageManager");
                return (Boolean) method.invoke(null);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                   checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void checkManageStoragePermission() {
        if (!hasManageStoragePermission()) {
            if (Build.VERSION.SDK_INT >= 30) {
                new AlertDialog.Builder(this)
                    .setTitle("需要所有文件访问权限")
                    .setMessage("为了扫描和转换NCM文件，需要开启所有文件访问权限。请在设置中找到本应用并开启此权限。")
                    .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                        }
                    })
                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            showToast("未开启所有文件访问权限，功能将受限");
                            finish();
                        }
                    })
                    .setCancelable(false)
                    .show();
            } else {
                new AlertDialog.Builder(this)
                    .setTitle("需要存储权限")
                    .setMessage("为了扫描和转换NCM文件，需要获取存储权限。")
                    .setPositiveButton("授权", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
                        }
                    })
                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            showToast("未授权存储权限，功能将受限");
                            finish();
                        }
                    })
                    .setCancelable(false)
                    .show();
            }
        } else {
            showAutoPage();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showAutoPage();
            } else {
                showToast("未授权存储权限，功能将受限");
                finish();
            }
        }
    }

    private void scanAutoFilesRecursive(File dir) {
        if (dir.getAbsolutePath().equals(DEFAULT_OUTPUT_FOLDER)) {
            return;
        }
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        scanAutoFilesRecursive(file);
                    } else if (file.getName().toLowerCase().endsWith(".ncm") && !autoFileList.contains(file.getName())) {
                        autoFileList.add(file.getName());
                    }
                }
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, autoFileList);
        autoListView.setAdapter(adapter);
        showToast("找到 " + autoFileList.size() + " 个 NCM 文件");
    }

    private void convertAutoFile(final String fileName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                convertNcm(new File(AUTO_FOLDER, fileName), new File(DEFAULT_OUTPUT_FOLDER));
            }
        }).start();
    }

    private void convertManualFile(final String ncmPath, final String outPath) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                File inFile = new File(ncmPath);
                File outDir = new File(outPath);
                convertNcm(inFile, outDir);
            }
        }).start();
    }

    private void convertNcm(File inFile, File outDir) {
        try {
            if (!inFile.exists()) {
                showToast("文件不存在：" + inFile.getAbsolutePath());
                return;
            }
            byte[] data = new byte[(int) inFile.length()];
            FileInputStream in = new FileInputStream(inFile);
            in.read(data);
            in.close();
            byte[] key = md5(NCM_KEY);
            SecretKeySpec skey = new SecretKeySpec(key, "RC4");
            Cipher cipher = Cipher.getInstance("RC4");
            cipher.init(Cipher.DECRYPT_MODE, skey);
            byte[] audioData = cipher.doFinal(data, 8, data.length - 8);
            String outName = inFile.getName().replace(".ncm", ".flac");
            if (!outDir.exists()) outDir.mkdirs();
            File outFile = new File(outDir, outName);
            FileOutputStream fos = new FileOutputStream(outFile);
            fos.write(audioData);
            fos.close();
            showToast("转换成功：" + outFile.getAbsolutePath());
        } catch (final Exception e) {
            e.printStackTrace();
            showToast("转换失败：" + e.getMessage());
        }
    }

    private byte[] md5(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        return md.digest(data);
    }

    private void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}
