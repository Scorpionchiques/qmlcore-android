package com.pureqml.android.runtime;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.ImageListener;
import com.pureqml.android.ImageLoader;

import java.net.MalformedURLException;
import java.net.URL;

public class Image extends Element implements ImageListener {
    private final static String TAG = "rt.Image";
    URL                         _url;
    ImageLoader.ImageResource   _image;
    V8Function                  _callback;
    Paint                       _paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Image(IExecutionEnvironment env) {
        super(env);
    }

    public void load(String name, V8Function callback) {
        if (name.indexOf("://") < 0)
            name = "file:///" + name;
        _url = null;
        try {
            _url = new URL(name);
        } catch (MalformedURLException e) {
            Log.e(TAG, "invalid url", e);
            V8 v8 = _env.getRuntime();

            V8Array args = new V8Array(v8);
            args.push((Object)null);
            callback.call(null, args); //indicate error
            return;
        }
        //Log.v(TAG, "loading " + url);
        _image = _env.loadImage(_url, this);
        _image.getBitmap();
        _callback = callback;
    }

    protected void setStyle(String name, Object value) {
        super.setStyle(name, value);
    }

    @Override
    public void onImageLoaded(URL url) {
        if (url.equals(_url))
            update();
    }

    @Override
    protected Rect getEffectiveRect() {
        return _rect;
    }

    @Override
    public void paint(Canvas canvas, int baseX, int baseY, float opacity) {
        if (!_visible)
            return;

        if (_image != null) {
            Bitmap bitmap = _image.getBitmap();
            if (bitmap != null) {
                Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                Rect dst = translateRect(_rect, baseX, baseY);
                //Log.i(TAG, "drawing image "  + src + " " + dst + " " + dst.width() + "x" + dst.height());
                canvas.drawBitmap(bitmap, src, dst, patchAlpha(_paint, opacity));
            }
        }
        super.paint(canvas, baseX, baseY, opacity);
    }
}
