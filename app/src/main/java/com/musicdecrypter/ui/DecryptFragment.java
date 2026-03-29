package com.musicdecrypter.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.musicdecrypter.R;
import com.musicdecrypter.databinding.FragmentDecryptBinding;
import com.musicdecrypter.utils.DecryptBridge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class DecryptFragment extends Fragment implements DecryptBridge.DecryptCallback {

    private FragmentDecryptBinding binding;
    private static final String TARGET_URL = "https://demo.unlock-music.dev/";
    private boolean isWebViewReady = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final List<Uri> pendingFileUris = new ArrayList<>();
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private int totalFileCount = 0;

    // 文件选择器
    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    return;
                }

                pendingFileUris.clear();
                successCount.set(0);
                failedCount.set(0);

                if (result.getData().getClipData() != null) {
                    int count = result.getData().getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        Uri uri = result.getData().getClipData().getItemAt(i).getUri();
                        pendingFileUris.add(uri);
                    }
                } else if (result.getData().getData() != null) {
                    pendingFileUris.add(result.getData().getData());
                }

                totalFileCount = pendingFileUris.size();
                if (totalFileCount == 0) {
                    Toast.makeText(requireContext(), "未选择任何文件", Toast.LENGTH_SHORT).show();
                    return;
                }

                startDecryptQueue();
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDecryptBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initWebView();

        // 打开解密文件目录按钮
        binding.btnDownload.setOnClickListener(v -> openSaveDir());

        // 选择加密文件按钮
        binding.btnSelectFile.setOnClickListener(v -> {
            if (!isWebViewReady) {
                Toast.makeText(requireContext(), "解密引擎正在加载，请稍候", Toast.LENGTH_SHORT).show();
                return;
            }
            openFileChooser();
        });
    }

    // 初始化WebView解密内核
    private void initWebView() {
        WebSettings webSettings = binding.webview.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0 Mobile Safari/537.36");
        WebView.setWebContentsDebuggingEnabled(true);

        binding.webview.addJavascriptInterface(new DecryptBridge(this), "AndroidDecryptBridge");

        binding.webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request, @NonNull WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    isWebViewReady = false;
                    binding.webview.setVisibility(View.GONE);
                    binding.errorTip.setVisibility(View.VISIBLE);
                    binding.tvStatus.setText(R.string.web_load_error);
                }
            }

            @Override
            public void onReceivedHttpError(@NonNull WebView view, @NonNull WebResourceRequest request, @NonNull WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                if (request.isForMainFrame()) {
                    binding.tvStatus.setText("网页加载失败，HTTP错误：" + errorResponse.getStatusCode());
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return !url.startsWith(TARGET_URL) && !url.startsWith("https://unlock-music.dev");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isWebViewReady = true;
                binding.webview.setVisibility(View.INVISIBLE);
                binding.errorTip.setVisibility(View.GONE);
                binding.tvStatus.setText("解密引擎已就绪，可选择NCM/MGG/QMC等文件");
            }
        });

        binding.webview.setWebChromeClient(new WebChromeClient());
        binding.tvStatus.setText(R.string.status_preparing);
        binding.webview.loadUrl(TARGET_URL);
    }

    // 打开系统文件选择器
    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        fileChooserLauncher.launch(intent);
    }

    // 批量解密队列
    private void startDecryptQueue() {
        if (pendingFileUris.isEmpty()) {
            requireActivity().runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                binding.tvStatus.setText(String.format(getString(R.string.status_success), successCount.get(), failedCount.get()));
            });
            return;
        }

        Uri fileUri = pendingFileUris.remove(0);
        int currentIndex = totalFileCount - pendingFileUris.size();

        executor.execute(() -> {
            try {
                String fileName = getFileNameFromUri(fileUri);
                String mimeType = getMimeTypeFromUri(fileUri);
                byte[] fileData = readFileToByteArray(fileUri);
                String base64Data = android.util.Base64.encodeToString(fileData, android.util.Base64.NO_WRAP);

                requireActivity().runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.tvStatus.setText(String.format(getString(R.string.status_decrypting), fileName, currentIndex, totalFileCount));
                });

                injectFileToWeb(fileName, base64Data, mimeType);

            } catch (Exception e) {
                failedCount.incrementAndGet();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "读取文件失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
                startDecryptQueue();
            }
        });
    }

    // 注入文件到前端解密页面
    private void injectFileToWeb(String fileName, String base64Data, String mimeType) {
        String js = "(async ()=>{try{"
                + "let b=atob('" + base64Data + "');"
                + "let arr=new Uint8Array(b.length);"
                + "for(let i=0;i<b.length;i++)arr[i]=b.charCodeAt(i);"
                + "let blob=new Blob([arr],{type:'" + mimeType + "'});"
                + "let f=new File([blob],'" + fileName + "');"
                + "let ipt=document.querySelector('input[type=file]')||document.createElement('input');"
                + "ipt.type='file';ipt.multiple=true;"
                + "let dt=new DataTransfer();dt.items.add(f);ipt.files=dt.files;"
                + "ipt.dispatchEvent(new Event('change',{bubbles:true}));"
                + "setTimeout(()=>{"
                + "let a=document.querySelector('a[download]');"
                + "if(a&&a.href.startsWith('blob:')){"
                + "fetch(a.href).then(r=>r.blob()).then(bl=>{"
                + "let rd=new FileReader();rd.onloadend=()=>{"
                + "let d=rd.result.split(',')[1];"
                + "AndroidDecryptBridge.onDecryptSuccess(a.download,d);}});"
                + "}else{AndroidDecryptBridge.onDecryptFailed('解密无输出');}"
                + "},4000);"
                + "}catch(e){AndroidDecryptBridge.onDecryptFailed(e.message);}})();";

        requireActivity().runOnUiThread(() -> binding.webview.evaluateJavascript(js, null));
    }

    // 解密成功回调
    @Override
    public void onDecryptSuccess(String fileName, byte[] fileData) {
        executor.execute(() -> {
            try {
                saveToDownloadDir(fileName, fileData);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failedCount.incrementAndGet();
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "保存失败："+e.getMessage(), Toast.LENGTH_SHORT).show());
            }
            requireActivity().runOnUiThread(() -> {
                binding.btnDownload.setVisibility(View.VISIBLE);
                startDecryptQueue();
            });
        });
    }

    // 解密失败回调
    @Override
    public void onDecryptFailed(String errorMsg) {
        failedCount.incrementAndGet();
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            startDecryptQueue();
        });
    }

    // 获取文件名
    private String getFileNameFromUri(Uri uri) {
        String name = "unknown_audio";
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = requireContext().getContentResolver().query(uri,null,null,null,null)){
                if(c!=null&&c.moveToFirst()){
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if(idx>-1) name = c.getString(idx);
                }
            }
        } else if(uri.getPath()!=null){
            name = new File(uri.getPath()).getName();
        }
        return name;
    }

    // 获取MIME类型
    private String getMimeTypeFromUri(Uri uri) {
        String type = requireContext().getContentResolver().getType(uri);
        if(type==null){
            String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        }
        return type!=null ? type : "application/octet-stream";
    }

    // Uri转字节数组
    private byte[] readFileToByteArray(Uri uri) throws Exception {
        InputStream is = requireContext().getContentResolver().openInputStream(uri);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while((len=is.read(buf))!=-1) bos.write(buf,0,len);
        is.close();
        return bos.toByteArray();
    }

    // 保存到 Download/MusicDecrypter
    private void saveToDownloadDir(String fileName, byte[] data) throws Exception {
        File root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MusicDecrypter");
        if(!root.exists()) root.mkdirs();
        File outFile = new File(root, fileName);
        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(data);
        fos.flush();
        fos.close();
        // 刷新媒体库
        requireContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outFile)));
    }

    // 打开解密文件保存目录
    private void openSaveDir(){
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MusicDecrypter");
        if(!dir.exists()) dir.mkdirs();
        Uri uri;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            uri = Uri.parse(dir.getAbsolutePath());
        }else{
            uri = Uri.fromFile(dir);
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "*/*");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(Intent.createChooser(intent, "打开解密文件夹"));
    }

    @Override
    public void onDestroyView() {
        if(binding.webview!=null){
            binding.webview.stopLoading();
            binding.webview.destroy();
        }
        executor.shutdownNow();
        super.onDestroyView();
        binding = null;
    }
}
