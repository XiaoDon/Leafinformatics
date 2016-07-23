package flaremars.com.somethingdemo.utils.network;

import android.os.Message;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * Created by FlareMars on 2015/10/21.
 */
public enum  HttpUtils {

    INSTANCE;

    public String get(String targetUrl, Map<String,String> params) throws IOException {
        URL url;
        try {
            url = new URL(targetUrl + mapParamsToString(params));
            HttpURLConnection urlConn = (HttpURLConnection)url.openConnection();
            InputStreamReader in = new InputStreamReader(urlConn.getInputStream());
            BufferedReader bufferedReader = new BufferedReader(in);
            String result = "";
            String readLine;
            while ((readLine = bufferedReader.readLine()) != null) {
                result += readLine;
            }
            in.close();
            urlConn.disconnect();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public String post(String targetURL,Map<String,String> params,NetworkHandler handler) {
        URL url;
        HttpURLConnection connection = null;
        try {
            //Create connection
            url = new URL(targetURL);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");
            connection.setRequestProperty("Connection", "Keep-Alive");
            String urlParameters = mapParamsToUrlParams(params);
            byte[] requestStringBytes = urlParameters.getBytes("utf-8");
            connection.setRequestProperty("Content-Length", "" +
                    Integer.toString(requestStringBytes.length));

            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            //Send request
            DataOutputStream wr = new DataOutputStream(
                    connection.getOutputStream ());
            wr.write(requestStringBytes);
            wr.flush();
            wr.close();

            //Get Response
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuilder response = new StringBuilder();

            int total = 0;
            Message msg = null;
            if (handler != null) {
                msg = new Message();
                msg.what = handler.getMSG_WHAT();
            }
            while((line = rd.readLine()) != null) {
                response.append(line);
                total += line.length();
                if (msg != null) {
                    msg.arg1 = total;
                    handler.handleMessage(msg);
                }
            }
            rd.close();
            return response.toString();

        } catch (Exception e) {

            e.printStackTrace();
            return null;

        } finally {

            if(connection != null) {
                connection.disconnect();
            }
        }
    }

    public String uploadFile(String targetUrl,Map<String,String> params,File file,NetworkHandler handler) {

        String PREFIX = "--";
        String BOUNDARY = java.util.UUID.randomUUID().toString();
        String LINEND = "\r\n";
        String MULTIPART_FROM_DATA = "multipart/form-data";
        String CHARSET = "UTF-8";

        try {
            URL uri = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) uri.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("connection", "keep-alive");
            conn.setRequestProperty("Charset", CHARSET);
            conn.setRequestProperty("Content-Type",MULTIPART_FROM_DATA +
                                        "; boundary=" + BOUNDARY);

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String,String> entry : params.entrySet()) {
                sb.append(PREFIX);
                sb.append(BOUNDARY);
                sb.append(LINEND);
                sb.append("Content-Disposition: form-data; name=\"");
                sb.append(entry.getKey());
                sb.append("\"");
                sb.append(LINEND);
                sb.append(LINEND);
                sb.append(entry.getValue());
                sb.append(LINEND);
            }

            DataOutputStream outputStream = new DataOutputStream(conn.getOutputStream());
            outputStream.write(sb.toString().getBytes());

            int total = 0;
            String fileBody = PREFIX + BOUNDARY + LINEND +
                                "Content-Disposition: form-data; name=\"file\"; filename=\"" +
                                    file.getName() + "\"" + LINEND + "Content-Type: application/octet-stream; charset=" +
                                        CHARSET + LINEND + LINEND;
            outputStream.write(fileBody.getBytes());

            InputStream is = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int len = 0;
            while ((len = is.read(buffer)) != -1) {
                outputStream.write(buffer,0,len);
                total += len;
                if (handler != null) {
                    Message msg = new Message();
                    msg.what = handler.getMSG_WHAT();
                    msg.arg1 = total;
                    handler.sendMessage(msg);
                }
            }
            is.close();
            outputStream.write(LINEND.getBytes());

            byte[] endData = (PREFIX + BOUNDARY + PREFIX + LINEND).getBytes();
            outputStream.write(endData);
            outputStream.flush();

            int statusCode = conn.getResponseCode();
            InputStream in = conn.getInputStream();
            if (statusCode == 200) {
                BufferedReader rd = new BufferedReader(new InputStreamReader(in));
                String line;
                StringBuilder response = new StringBuilder();

                while((line = rd.readLine()) != null) {
                    response.append(line);
                }
                rd.close();
                Log.i("tag","upload finished");
                return response.toString();
            }

            outputStream.close();
            conn.disconnect();
            return in.toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    private String mapParamsToString(Map<String,String> params) {
        StringBuilder stringBuilder = new StringBuilder("?");
        for (Map.Entry<String,String> pair : params.entrySet()) {
            stringBuilder.append(pair.getKey());
            stringBuilder.append("=");
            stringBuilder.append(pair.getValue());
            stringBuilder.append("&");
        }
        if (stringBuilder.charAt(stringBuilder.length() - 1) == '&') {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        return stringBuilder.toString();
    }

    private String mapParamsToUrlParams(Map<String,String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String,String> pair : params.entrySet()) {
            sb.append(pair.getKey());
            sb.append("=");
            sb.append(pair.getValue());
            sb.append("&");
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    private String mapParamsToJSONString(Map<String,String> params) {
        JSONObject jsonObject = new JSONObject();
        for (Map.Entry<String,String> pair : params.entrySet()) {
            try {
                jsonObject.put(pair.getKey(),pair.getValue());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jsonObject.toString();
    }

}

