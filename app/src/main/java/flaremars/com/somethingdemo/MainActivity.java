package flaremars.com.somethingdemo;

import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.CrossProcessCursor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.soundcloud.android.crop.Crop;

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

    private static final int SUCCESS_CODE = 200;

    private static final String BASE_URL = "http://54.223.37.11:33333/";

    public static int screenWidth = 0;

    public static int requiredImageWidth = 0;

    private MaterialDialog progressDialog;

    private NetworkHandler handler;

    private Executor singleExecutor;

    private int totalProgress = 0;

    private int currentProgress = 0;

    //结果显示相关
    private GridView contentView;

    private List<BitmapBean> bitmapBeanList;

    private CachedPicturesAdapter adapter;

    //添加本地文件
    private ImageView resultView;
    private TextView welcome,note;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultView = (ImageView) findViewById(R.id.result_image);
        welcome = (TextView) findViewById(R.id.welcometext);
        note = (TextView) findViewById(R.id.notetext);

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
            Crop.pickImage(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        if(resultCode == RESULT_OK) {
            if (requestCode == Crop.REQUEST_PICK) {
                beginCrop(result.getData());
            } else if (requestCode == Crop.REQUEST_CROP) {
                //上传缓存图片
                resultView.setImageDrawable(null);
                welcome.setText("");note.setText("");
                uploadimg(Crop.getOutput(result));

                //清除Imageview中的缓存图片
                //handleCrop(resultCode, result);
            }else
            {
                Toast.makeText(this, Crop.getError(result).getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    protected void uploadimg(final Uri tempPhotoPath){
                final File targetFile = new File(this.getCacheDir().getAbsoluteFile().toString()+"/cropped");
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
                                                        tempBean = new BitmapBean( BASE_URL+"Test/ImageDownloadServlet?dirIndex=0&fileIndex=0", "原始叶片图像");
                                                        bitmapBeanList.add(tempBean);

                                                        for (int i = 0; i < size; i++) {
                                                            tempObject = dataArray.getJSONObject(i);
                                                            tempBean = new BitmapBean(BASE_URL+tempObject.getString("url"), "相似度TOP."+(i+1)+":\n"+tempObject.getString("info")+"号叶片图像");
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
                        .content("正在全力加载！")
                        .progress(true, 0)
                        .progressIndeterminateStyle(false)
                        .show();
            }
        }
    }

    private void beginCrop(Uri source) {
        Uri destination = Uri.fromFile(new File(getCacheDir(), "cropped"));
        Crop.of(source, destination).asPng(false).start(this);//asSquare()
    }

    private void handleCrop(int resultCode, Intent result) {
        if (resultCode == RESULT_OK) {
            resultView.setImageURI(Crop.getOutput(result));
        } else if (resultCode == Crop.RESULT_ERROR) {
            Toast.makeText(this, Crop.getError(result).getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
