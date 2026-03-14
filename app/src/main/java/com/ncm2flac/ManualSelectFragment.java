package com.ncm2flac;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ncm2flac.core.NcmDecryptCore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ManualSelectFragment extends Fragment {

    private TextView tvSelectedFile, tvProgress;
    private Button btnSelectFile, btnConvert;
    private ProgressBar progressConvert;
    private File selectedFile;
    private SharedPreferences sp;
    private final String DEFAULT_SAVE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/NCM2FLAC/";
    private final ExecutorService convertExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> selectFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        selectedFile = uriToFile(uri);
                        if (selectedFile != null && selectedFile.exists()) {
                            tvSelectedFile.setText("已选择：" + selectedFile.getName());
                            btnConvert.setVisibility(View.VISIBLE);
                            btnConvert.setEnabled(true);
                            progressConvert.setVisibility(View.GONE);
                            tvProgress.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "文件选择成功", Toast.LENGTH_SHORT).show();
                        } else {
                            tvSelectedFile.setText("文件解析失败");
                            btnConvert.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "文件解析失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manual_select, container, false);
        sp = requireActivity().getSharedPreferences("app_config", 0);

        // 绑定控件
        btnSelectFile = view.findViewById(R.id.btn_select_file);
        tvSelectedFile = view.findViewById(R.id.tv_selected_file);
        btnConvert = view.findViewById(R.id.btn_convert);
        progressConvert = view.findViewById(R.id.progress_convert);
        tvProgress = view.findViewById(R.id.tv_progress);

        // 重置状态
        btnConvert.setVisibility(View.GONE);
        progressConvert.setVisibility(View.GONE);
        tvProgress.setVisibility(View.GONE);

        // 选择文件按钮
        btnSelectFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            selectFileLauncher.launch(intent);
        });

        // 转换按钮
        btnConvert.setOnClickListener(v -> {
            if (selectedFile == null || !selectedFile.exists()) {
                Toast.makeText(getContext(), "请先选择有效文件", Toast.LENGTH_SHORT).show();
                return;
            }
            startConvert();
        });

        return view;
    }

    private void startConvert() {
        // 更新UI状态
        btnConvert.setVisibility(View.GONE);
        progressConvert.setVisibility(View.VISIBLE);
        tvProgress.setVisibility(View.VISIBLE);
        progressConvert.setProgress(0);
        tvProgress.setText("0%");

        String savePath = sp.getString("save_path", DEFAULT_SAVE_PATH);

        // 提交转换任务到线程池，避免ANR闪退
        convertExecutor.submit(() -> {
            try {
                NcmDecryptCore decryptCore = new NcmDecryptCore(selectedFile, savePath);
                decryptCore.startDecrypt(new OnConvertListener() {
                    @Override
                    public void onStart() {
                        requireActivity().runOnUiThread(() -> {
                            progressConvert.setProgress(0);
                            tvProgress.setText("0%");
                        });
                    }

                    @Override
                    public void onProgress(int progress) {
                        requireActivity().runOnUiThread(() -> {
                            progressConvert.setProgress(progress);
                            tvProgress.setText(progress + "%");
                        });
                    }

                    @Override
                    public void onSuccess(File outputFile) {
                        requireActivity().runOnUiThread(() -> {
                            btnConvert.setVisibility(View.VISIBLE);
                            btnConvert.setText("转换完成");
                            btnConvert.setBackgroundColor(0xFF4CAF50);
                            btnConvert.setEnabled(false);
                            progressConvert.setVisibility(View.GONE);
                            tvProgress.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "转换成功！文件已保存到：" + outputFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                            // 重置状态
                            selectedFile = null;
                            tvSelectedFile.setText("未选择文件");
                        });
                    }

                    @Override
                    public void onFail(String errorMsg) {
                        requireActivity().runOnUiThread(() -> {
                            btnConvert.setVisibility(View.VISIBLE);
                            btnConvert.setText("重试");
                            btnConvert.setEnabled(true);
                            progressConvert.setVisibility(View.GONE);
                            tvProgress.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "转换失败：" + errorMsg, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    btnConvert.setVisibility(View.VISIBLE);
                    btnConvert.setText("重试");
                    btnConvert.setEnabled(true);
                    progressConvert.setVisibility(View.GONE);
                    tvProgress.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "转换异常：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // Uri转本地缓存文件
    private File uriToFile(Uri uri) {
        try {
            InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
            File cacheFile = new File(getContext().getCacheDir(), "temp.ncm");
            FileOutputStream outputStream = new FileOutputStream(cacheFile);
            byte[] buffer = new byte[1024 * 1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            inputStream.close();
            outputStream.close();
            return cacheFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 释放线程池
        if (!convertExecutor.isShutdown()) {
            convertExecutor.shutdownNow();
        }
    }
}
