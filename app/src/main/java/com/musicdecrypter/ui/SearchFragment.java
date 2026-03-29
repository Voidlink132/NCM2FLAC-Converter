// 替换 MUSIC_DIRS 为适配 Android 11+ 分区存储的路径
private static final List<FileScannerUtils.MusicDir> MUSIC_DIRS = List.of(
    new FileScannerUtils.MusicDir("netease", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/netease/cloudmusic/Music/", R.id.table_netease),
    new FileScannerUtils.MusicDir("qq", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "/qqmusic/song/", R.id.table_qq),
    new FileScannerUtils.MusicDir("kugou", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/kgmusic/download/", R.id.table_kugou)
);

// 确保 FileScannerUtils 里 scanMusicFiles 用 SAF 遍历，而不是直接读路径
// 补充：在 SearchFragment 里添加 SAF 权限申请逻辑
private void requestStoragePermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (!Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
            startActivityForResult(intent, 1001);
        }
    }
}
