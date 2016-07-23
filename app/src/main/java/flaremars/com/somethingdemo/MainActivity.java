package flaremars.com.somethingdemo;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.GridView;
import android.widget.Toast;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import flaremars.com.somethingdemo.adapters.CachedPicturesAdapter;
import flaremars.com.somethingdemo.bean.BitmapBean;
import flaremars.com.somethingdemo.utils.DisplayUtils;
import flaremars.com.somethingdemo.utils.FileUtils;
import flaremars.com.somethingdemo.utils.network.HttpUtils;
import flaremars.com.somethingdemo.utils.network.INetworkContext;
import flaremars.com.somethingdemo.utils.network.NetworkHandler;

public class MainActivity extends AppCompatActivity implements INetworkContext {

    private static final int TAKE_PHOTO_ACTION_CODE = 1;

    private static final int SUCCESS_CODE = 200;

    private static final SimpleDateFormat PHOTO_NAME_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA);

    private static final String BASE_URL = "http://192.168.1.155:8080/";

    public static int screenWidth = 0;

    public static int requiredImageWidth = 0;

    private MaterialDialog progressDialog;

    private NetworkHandler handler;

    private Executor singleExecutor;

    private String tempPhotoName;

    private String tempPhotoPath;

    private int totalProgress = 0;

    private int currentProgress = 0;

    //结果显示相关
    private GridView contentView;

    private List<BitmapBean> bitmapBeanList;

    private CachedPicturesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Point screenSize = DisplayUtils.INSTANCE.getScreenWidth(this);
        screenWidth = screenSize.x;
        int fiveDpValue = DisplayUtils.INSTANCE.dp2px(this,5.0f);
        requiredImageWidth = (screenWidth - 3 * fiveDpValue) / 2;

        contentView = (GridView) findViewById(R.id.contentView);
        bitmapBeanList = new ArrayList<>();
        adapter = new CachedPicturesAdapter(this,bitmapBeanList,contentView);
        contentView.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflate = getMenuInflater();
        menuInflate.inflate(R.menu.main_menu,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.upload) {
            takePhotoAction();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == TAKE_PHOTO_ACTION_CODE) {

                final File targetFile = new File(tempPhotoPath);
                totalProgress = (int)targetFile.length();
                singleExecutor = Executors.newSingleThreadExecutor();
                handler = new NetworkHandler(this);
                handler.setMSG_WHAT(1);
                new MaterialDialog.Builder(this)
                        .title("图片上传中...")
                        .contentGravity(GravityEnum.CENTER)
                        .progress(false, totalProgress, true)
                        .showListener(new DialogInterface.OnShowListener() {
                            @Override
                            public void onShow(DialogInterface dialogInterface) {
                                final MaterialDialog dialog = (MaterialDialog) dialogInterface;
                                MainActivity.this.progressDialog = dialog;
                                singleExecutor.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        String targetUrl = BASE_URL + "Test/ImageUploadServlet";
                                        Map<String, String> params = new HashMap<>();
                                        params.put("extraData", "something information");

                                        final String response = HttpUtils.INSTANCE.uploadFile(targetUrl, params, targetFile, handler);
                                        Log.i("tag", response);
                                        MainActivity.this.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                //解析返回值
                                                try {
                                                    JSONObject responseObject = new JSONObject(response);
                                                    int statusCode = responseObject.getInt("statusCode");
                                                    String msg = responseObject.getString("message");

                                                    if (statusCode == SUCCESS_CODE) {
                                                        bitmapBeanList.clear();
                                                        JSONArray dataArray = responseObject.getJSONArray("data");
                                                        int size = dataArray.length();
                                                        BitmapBean tempBean;
                                                        JSONObject tempObject;
                                                        for (int i = 0; i < size; i++) {
                                                            tempObject = dataArray.getJSONObject(i);
                                                            tempBean = new BitmapBean(tempObject.getString("url"), tempObject.getString("info"));
                                                            bitmapBeanList.add(tempBean);
                                                        }
                                                        adapter.notifyDataSetChanged();
                                                    } else {
                                                        Toast.makeText(MainActivity.this, "错误：" + msg, Toast.LENGTH_LONG).show();
                                                    }

                                                    if (loadingDialog != null) {
                                                        loadingDialog.dismiss();
                                                        loadingDialog = null;
                                                    }
                                                } catch (JSONException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        });
                                    }
                                });
                            }
                        }).show();
            }
        }
    }

    private void takePhotoAction() {
        final File directory = FileUtils.INSTANCE.getDirectory(this, "photos", false);
        tempPhotoName = PHOTO_NAME_FORMAT.format(new Date()) + ".jpg";
        assert directory != null;
        final String string = directory.getPath() + File.separator + tempPhotoName;
        tempPhotoPath = string;
        final File file = new File(string);
        final Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        intent.putExtra("output", Uri.fromFile(file));
        intent.putExtra("return-data", true);
        startActivityForResult(intent, TAKE_PHOTO_ACTION_CODE);
    }

    private MaterialDialog loadingDialog;

    @Override
    public void invalidate(int progress) {
        Log.i("tag",progress + " " + currentProgress + " " + totalProgress);
        if (progressDialog != null) {
            progressDialog.incrementProgress(progress - currentProgress);
            currentProgress = progress;

            if (progress >= totalProgress) {
                progressDialog.dismiss();
                progressDialog = null;
                currentProgress = 0;
                loadingDialog = new MaterialDialog.Builder(this)
                        .title("等待数据回复")
                        .content("惊奇总是出现在等待之后")
                        .progress(true, 0)
                        .progressIndeterminateStyle(false)
                        .show();
            }
        }
    }
}
