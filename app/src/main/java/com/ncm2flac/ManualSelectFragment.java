package com.ncm2flac;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ManualSelectFragment extends Fragment {

    private TextView tvSelectedFile;
    private File selectedFile;

    private final ActivityResultLauncher<Intent> selectFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        selectedFile = uriToFile(uri);
                        if (selectedFile != null && selectedFile.exists()) {
                            tvSelectedFile.setText("已选择：" + selectedFile.getName());
                            Toast.makeText(getContext(), "文件选择成功", Toast.LENGTH_SHORT).show();
                        } else {
                            tvSelectedFile.setText("文件解析失败");
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
        Button btnSelectFile = view.findViewById(R.id.btn_select_file);
        tvSelectedFile = view.findViewById(R.id.tv_selected_file);

        btnSelectFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            selectFileLauncher.launch(intent);
        });

        return view;
    }

    private File uriToFile(Uri uri) {
        try {
            InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
            File cacheFile = new File(getContext().getCacheDir(), "temp.ncm");
            FileOutputStream outputStream = new FileOutputStream(cacheFile);
            byte[] buffer = new byte[1024];
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
}
