package flaremars.com.somethingdemo.bean;

/**
 * Created by FlareMars on 2015/11/7.
 */
public class BitmapBean {

    private String url;

    private String info;

    public BitmapBean() {
        url = "";
        info = "";
    }

    public BitmapBean(String url, String info) {
        this.url = url;
        this.info = info;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }
}
