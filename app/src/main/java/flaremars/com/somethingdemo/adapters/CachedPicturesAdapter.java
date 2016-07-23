package flaremars.com.somethingdemo.adapters;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.AsyncTask;
import android.util.Log;
import android.util.LruCache;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import flaremars.com.somethingdemo.MainActivity;
import flaremars.com.somethingdemo.R;
import flaremars.com.somethingdemo.bean.BitmapBean;
import flaremars.com.somethingdemo.utils.DiskLruCache;
import flaremars.com.somethingdemo.utils.FileUtils;
import flaremars.com.somethingdemo.utils.NormalUtils;


/**
 * Created by FlareMars on 2015/11/7.
 *
 */
public class CachedPicturesAdapter extends BaseAdapter {

    private static final String TAG = "CachedPicturesAdapter";

    private List<BitmapBean> pictureEntities;

    public static Point screenSize;

//    public static int itemHeight = 0;

    private Set<BitmapWorkerTask> taskCollection;

    private LruCache<String, Bitmap> memoryCache;

    private DiskLruCache diskLruCache;

    private GridView parentView;

    private Activity context;

    public CachedPicturesAdapter(Activity context, List<BitmapBean> pictureEntities, GridView parent) {
        this.pictureEntities = pictureEntities;
        if (screenSize == null) {
            screenSize = new Point();
            WindowManager wm = context.getWindowManager();
            Display d = wm.getDefaultDisplay();
            d.getSize(screenSize);
        }
        this.context = context;
        parentView = parent;

        taskCollection = new HashSet<>();
        // 获取应用程序最大可用内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheSize = maxMemory / 8;
        // 设置图片缓存大小为程序最大可用内存的1/8
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };
        try {
            // 获取图片缓存路径
            File cacheDir = FileUtils.INSTANCE.getDiskCacheDir(context, "thumb");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            // 创建DiskLruCache实例，初始化缓存数据
            diskLruCache = DiskLruCache
                    .open(cacheDir, NormalUtils.INSTANCE.getAppVersion(context), 1, 10 * 1024 * 1024);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getCount() {
        return pictureEntities.size();
    }

    @Override
    public BitmapBean getItem(int position) {
        return pictureEntities.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        BitmapBean item = getItem(position);

        if (convertView == null) {

            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_image_with_text, parent, false);
            viewHolder = new ViewHolder(convertView);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }


        viewHolder.getImageView().setTag(item.getUrl());
        viewHolder.getImageView().setImageResource(R.mipmap.default_ic_pic);
        loadBitmaps(viewHolder.getImageView(), item.getUrl());
        viewHolder.getTextView().setText(item.getInfo());

        return convertView;
    }

    private class ViewHolder  {

        private ImageView imageView;

        private TextView textView;

        public ViewHolder(View itemView) {
            imageView = (ImageView) itemView.findViewById(R.id.img);
            textView = (TextView) itemView.findViewById(R.id.text);
            ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
            layoutParams.width = MainActivity.requiredImageWidth;
            layoutParams.height = MainActivity.requiredImageWidth;
            imageView.setLayoutParams(layoutParams);
        }

        public ImageView getImageView() {
            return imageView;
        }

        public TextView getTextView() {
            return textView;
        }
    }

    /**
     * 将一张图片存储到LruCache中。
     *
     * @param key
     *            LruCache的键，这里传入图片的URL地址。
     * @param bitmap
     *            LruCache的键，这里传入从网络上下载的Bitmap对象。
     */
    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemoryCache(key) == null) {
            memoryCache.put(key, bitmap);
        }
    }

    /**
     * 从LruCache中获取一张图片，如果不存在就返回null。
     *
     * @param key
     *            LruCache的键，这里传入图片的URL地址。
     * @return 对应传入键的Bitmap对象，或者null。
     */
    public Bitmap getBitmapFromMemoryCache(String key) {
        return memoryCache.get(key);
    }

    /**
     * 加载Bitmap对象。此方法会在LruCache中检查所有屏幕中可见的ImageView的Bitmap对象，
     * 如果发现任何一个ImageView的Bitmap对象不在缓存中，就会开启异步线程去下载图片。
     */
    public void loadBitmaps(ImageView imageView, String imageUrl) {
        try {
            Bitmap bitmap = getBitmapFromMemoryCache(imageUrl);
            if (bitmap == null) {
                BitmapWorkerTask task = new BitmapWorkerTask();
                taskCollection.add(task);
                task.execute(imageUrl);
            } else {
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 取消所有正在下载或等待下载的任务。
     */
    public void cancelAllTasks() {
        if (taskCollection != null) {
            for (BitmapWorkerTask task : taskCollection) {
                task.cancel(false);
            }
        }
    }

    /**
     * 将缓存记录同步到journal文件中。
     */
    public void flushCache() {
        if (diskLruCache != null) {
            try {
//                Log.e("TAG","diskLruCache.flush");
                diskLruCache.flush();
                diskLruCache.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {

        /**
         * 图片的URL地址
         */
        private String imageUrl;

        @Override
        protected Bitmap doInBackground(String... params) {
            imageUrl = params[0];
            FileDescriptor fileDescriptor = null;
            FileInputStream fileInputStream = null;
            DiskLruCache.Snapshot snapShot;
            try {
                // 生成图片URL对应的key
                final String key = NormalUtils.INSTANCE.hashKeyForDisk(imageUrl);
                // 查找key对应的缓存
                snapShot = diskLruCache.get(key);
                if (snapShot == null) {
                    // 如果没有找到对应的缓存，则准备从网络上请求数据，并写入缓存
                    DiskLruCache.Editor editor = diskLruCache.edit(key);
                    if (editor != null) {
                        OutputStream outputStream = editor.newOutputStream(0);
                        if (downloadUrlToStream(imageUrl, outputStream)) {
                            editor.commit();
                        } else {
                            editor.abort();
                        }
                    }
                    // 缓存被写入后，再次查找key对应的缓存
                    snapShot = diskLruCache.get(key);
                }
                if (snapShot != null) {
                    fileInputStream = (FileInputStream) snapShot.getInputStream(0);
                    fileDescriptor = fileInputStream.getFD();
                }
                // 将缓存数据解析成Bitmap对象
                Bitmap bitmap = null;
                if (fileDescriptor != null) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = Math.round(MainActivity.screenWidth / MainActivity.requiredImageWidth);
                    bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor,null,options);
                }
                if (bitmap != null) {
                    // 将Bitmap对象添加到内存缓存当中
                    addBitmapToMemoryCache(params[0], bitmap);
                }
                return bitmap;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fileDescriptor == null && fileInputStream != null) {
                    try {
                        fileInputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            // 根据Tag找到相应的ImageView控件，将下载好的图片显示出来。
            ImageView imageView = (ImageView) parentView.findViewWithTag(imageUrl);
            if (imageView != null && bitmap != null) {
                imageView.setImageBitmap(bitmap);
            } else if (imageView != null) {
                imageView.setImageResource(R.mipmap.default_ic_pic_broken);
            }
            taskCollection.remove(this);
        }

        private boolean fromFileToStream(String urlString, OutputStream outputStream) {
            try {
                byte[] bitmapData = FileUtils.INSTANCE.readFile(urlString,context);
                outputStream.write(bitmapData);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }

        private boolean downloadUrlToStream(String urlString, OutputStream outputStream) {
            HttpURLConnection urlConnection = null;
            BufferedOutputStream out = null;
            BufferedInputStream in = null;
            try {
                Log.i("tag",urlString);
                final URL url = new URL(urlString);
                urlConnection = (HttpURLConnection) url.openConnection();
                in = new BufferedInputStream(urlConnection.getInputStream(), 8 * 1024);
                out = new BufferedOutputStream(outputStream, 8 * 1024);
                int b;
                while ((b = in.read()) != -1) {
                    out.write(b);
                }
                return true;
            } catch (final IOException e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                try {
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

    }

}
