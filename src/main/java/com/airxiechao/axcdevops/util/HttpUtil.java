package com.airxiechao.axcdevops.util;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class HttpUtil {

    private static OkHttpClient client = new OkHttpClient();

    public static void download(String url, String dir, String fileName) throws IOException {
        File saveDir = new File(dir);
        if(!saveDir.exists()){
            saveDir.mkdirs();
        }

        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Failed to download file: " + url);
        }

        File saveFile = new File(dir, fileName);
        FileOutputStream fos = new FileOutputStream(saveFile);
        fos.write(response.body().bytes());
        fos.close();
    }

}
