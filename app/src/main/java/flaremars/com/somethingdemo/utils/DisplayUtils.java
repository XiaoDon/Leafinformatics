package flaremars.com.somethingdemo.utils;

import android.content.Context;
import android.graphics.Point;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.view.WindowManager;

public enum DisplayUtils {
	INSTANCE;
	
	public int dp2px(Context context,float dpValue) {		
		final float scale = context.getResources().getDisplayMetrics().density; 
	    return (int) (dpValue * scale + 0.5f); 
	}
	
	public Point getScreenWidth(Context context) {
		WindowManager wm = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);

		Point screenSize = new Point();
		if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
			wm.getDefaultDisplay().getRealSize(screenSize);
		} else {
			screenSize.x = wm.getDefaultDisplay().getWidth();
			screenSize.y = wm.getDefaultDisplay().getHeight();
		}
		return screenSize;
	}
}
