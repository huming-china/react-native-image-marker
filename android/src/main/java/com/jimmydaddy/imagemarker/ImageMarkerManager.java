package com.jimmydaddy.imagemarker;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.views.text.ReactFontManager;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


/**
 * Created by jimmydaddy on 2017/3/6.
 */


public class ImageMarkerManager extends ReactContextBaseJavaModule {
    private ReactApplicationContext context;
    private static final String PROP_ICON_URI = "uri";
    private static final String IMAGE_MARKER_TAG = "[ImageMarker]";


    public ImageMarkerManager(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext;
    }

    @Override
    public String getName() {
        return "ImageMarker";
    }

    private Resources getResources() {
        return this.context.getResources();
    }

    private int getDrawableResourceByName(String name) {
        return this.getResources().getIdentifier(
                name,
                "drawable",
                this.context.getPackageName());
    }

//    private Drawable getDrawableByName(String name) {
//        int drawableResId = getDrawableResourceByName(name);
//        if (drawableResId != 0) {
//            return getResources().getDrawable(getDrawableResourceByName(name));
//        } else {
//            return null;
//        }
//    }

    private Boolean isFrescoImg(String uri) {
        String base64Pattern = "^data:(image|img)/(bmp|jpg|png|tif|gif|pcx|tga|exif|fpx|svg|psd|cdr|pcd|dxf|ufo|eps|ai|raw|WMF|webp);base64,(([[A-Za-z0-9+/])*\\s\\S*)*";

        return uri.startsWith("http://") || uri.startsWith("https://") || uri.startsWith("file://") || (uri.startsWith("data:") && uri.contains("base64") && (uri.contains("img") || uri.contains("image")));
    }

    private void markImage(
            final Bitmap bg,
            ReadableMap source,
            final String position,
            final Integer X,
            final Integer Y,
            final Float markerScale,
            final int quality,
            final String dest,
            final Promise promise)
    {
        try {

//            String resultFile = generateCacheFilePathForMarker(imgSavePath, filename);

            final String uri = source.getString(PROP_ICON_URI);

            Log.d(IMAGE_MARKER_TAG, uri);
            Log.d(IMAGE_MARKER_TAG, source.toString());

            if (isFrescoImg(uri)) {
                ImageRequest imageRequest = ImageRequest.fromUri(uri);
                DataSource<CloseableReference<CloseableImage>> dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, null);
                Executor executor = Executors.newSingleThreadExecutor();
                dataSource.subscribe(new BaseBitmapDataSubscriber() {
                    @Override
                    public void onNewResultImpl(@Nullable Bitmap bitmap) {
                        if (bitmap != null) {
                            Bitmap mark = Utils.scaleBitmap(bitmap, markerScale);

                            markImageByBitmap(bg, mark, position, X, Y, quality, dest, promise);
                        } else {
                            promise.reject( "marker error","Can't retrieve the file from the markerpath: " + uri);
                        }
                    }

                    @Override
                    public void onFailureImpl(DataSource dataSource) {
                        promise.reject( "error","Can't request the image from the uri: " + uri, dataSource.getFailureCause());
                    }
                }, executor);
            } else {
                int resId = getDrawableResourceByName(uri);
                if (resId == 0) {
                    Log.d(IMAGE_MARKER_TAG, "cannot find res");
                    promise.reject( "error","Can't get resource by the path: " + uri);
                } else {
                    Log.d(IMAGE_MARKER_TAG, "res：" + resId);

                    Resources r = this.getResources();
//                    InputStream is = r.openRawResource(resId);
                    Bitmap bitmap = BitmapFactory.decodeResource(r, resId);
//                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    Log.d(IMAGE_MARKER_TAG, bitmap.getHeight() + "");
                    Bitmap mark = Utils.scaleBitmap(bitmap, markerScale);
                    Log.d(IMAGE_MARKER_TAG, mark.getHeight() + "");

                    if (bitmap != null && !bitmap.isRecycled() && markerScale != 1) {
                        bitmap.recycle();
                        System.gc();
                    }
                    markImageByBitmap(bg, mark, position, X, Y, quality, dest, promise);
                }
            }
        } catch (Exception e) {
            Log.d(IMAGE_MARKER_TAG, "error：" + e.getMessage());
            e.printStackTrace();
            promise.reject("error", e.getMessage(), e);
        }
    }


    private void markImageByBitmap (
            Bitmap bg,
            Bitmap marker,
            String position,
            Integer X,
            Integer Y,
            int quality,
            String dest,
            final Promise promise
    ) {
        BufferedOutputStream bos = null;
        Bitmap icon = null;
        try {

            // 原图生成 - start



            int height = bg.getHeight();
            int width =  bg.getWidth();


            icon = Utils.getBlankBitmap(width, height);

            //初始化画布 绘制的图像到icon上
            //建立画笔
            Paint photoPaint = new Paint();
            //获取跟清晰的图像采样
            photoPaint.setDither(true);
            //过滤一些

//            if (percent > 1) {
//                prePhoto = Bitmap.createScaledBitmap(prePhoto, width, height, true);
//            }
            Canvas canvas = new Canvas(icon);


            canvas.drawBitmap(bg, 0, 0, photoPaint);

            // 原图生成 - end


            if (position != null) {
                Position pos = getRectFromPosition(position, marker.getWidth(), marker.getHeight(), width, height);
                canvas.drawBitmap(marker, pos.getX(), pos.getY(), photoPaint);
            } else {
                canvas.drawBitmap(marker, X, Y, photoPaint);
            }

            if (bg != null && !bg.isRecycled()) {
                bg.recycle();
                System.gc();
            }

            if (marker != null && !marker.isRecycled()) {
                marker.recycle();
                System.gc();
            }


            // 保存
            // canvas.save(Canvas.ALL_SAVE_FLAG);
            canvas.save();
            // 存储
            canvas.restore();
            bos = new BufferedOutputStream(new FileOutputStream(dest));

//            int quaility = (int) (100 / percent > 80 ? 80 : 100 / percent);
            icon.compress(Bitmap.CompressFormat.PNG, quality, bos);
            bos.flush();
            //保存成功的
            promise.resolve(dest);
        } catch (Exception e) {
            e.printStackTrace();
            promise.reject("error", e.getMessage(), e);
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (icon != null && !icon.isRecycled()) {
                icon.recycle();
                icon = null;
                System.gc();
            }
        }
    }

    /**
     *
     * @param bg
     * @param mark
     * @param position
     * @param color
     * @param fontName
     * @param fontSize
     * @param X
     * @param Y
     * @param quality
     * @param dest
     * @param promise
     */
    private void markImageByText(
            Bitmap bg,
            String mark,
            String position,
            String color,
            String fontName,
            Integer fontSize,
            ShadowLayerStyle shadowLayerStyle,
            Integer X,
            Integer Y,
            int quality,
            String dest,
            final Promise promise
    ) {
        BufferedOutputStream bos = null;
        boolean isFinished;
        Bitmap icon = null;
        try {

            int height = bg.getHeight();
            int width =  bg.getWidth();

            icon = Utils.getBlankBitmap(width, height);
            //初始化画布 绘制的图像到icon上
            Canvas canvas = new Canvas(icon);
            //建立画笔
            Paint photoPaint = new Paint();
            //获取跟清晰的图像采样
            photoPaint.setDither(true);
            //过滤一些
//            if (percent > 1) {
//                prePhoto = Bitmap.createScaledBitmap(prePhoto, width, height, true);
//            }

            canvas.drawBitmap(bg, 0, 0, photoPaint);

            if (bg != null && !bg.isRecycled()) {
                bg.recycle();
                System.gc();
            }

            //设置画笔
            //建立画笔
            TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            textPaint.setAntiAlias(true);
            if (null != shadowLayerStyle) {
                textPaint.setShadowLayer(shadowLayerStyle.radius, shadowLayerStyle.dx,shadowLayerStyle.dy, shadowLayerStyle.color);
            }
            try {
                //设置字体失败时使用默认字体
                textPaint.setTypeface(ReactFontManager.getInstance().getTypeface(fontName, Typeface.NORMAL, this.getReactApplicationContext().getAssets()) );
            } catch (Exception e) {
                textPaint.setTypeface(Typeface.DEFAULT);
            }

            Integer fSize = 14;
            if (fontSize != null){
                fSize = fontSize;
            }

            textPaint.setTextSize(fSize);
            textPaint.setColor(Color.parseColor(color));

            // ALIGN_CENTER, ALIGN_NORMAL, ALIGN_OPPOSITE
            StaticLayout textLayout = new StaticLayout(mark, textPaint, canvas.getWidth(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

            int textHeight = textLayout.getHeight();
            int textWidth = 0;
            int count = textLayout.getLineCount();
            for (int a = 0; a < count; a++) {
                textWidth = (int) Math.ceil(Math.max(textWidth, textLayout.getLineWidth(a) + textLayout.getLineLeft(a)));
            }

            float x = 20;
            float y = 20;

            if (position != null) {
                if("topCenter".equals(position)) {
                    x = (width - textWidth)/2;
                } else if("topRight".equals(position)) {
                    x = (width - textWidth);
                } else if("center".equals(position)) {
                    x = (width - textWidth) / 2;
                    y = (height - textHeight) / 2;
                } else if("bottomLeft".equals(position)) {
                    y = (height - textHeight);
                } else if("bottomCenter".equals(position)) {
                    x = (width - textWidth) / 2;
                    y = (height - textHeight);
                } else if("bottomRight".equals(position)) {
                    x = (width - textWidth);
                    y = (height - textHeight);
                }
            } else {
                if (null != X) {
                    x = X;
                }
                if ( null != Y) {
                    y = Y;
                }
            }

            canvas.save();
            canvas.translate(x, y);
            textLayout.draw(canvas);
            canvas.restore();

            bos = new BufferedOutputStream(new FileOutputStream(dest));

            icon.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            bos.flush();
            //保存成功的
            promise.resolve(dest);
        } catch (Exception e) {
            e.printStackTrace();
            promise.reject("error", e.getMessage(), e);
        } finally {
            isFinished = true;
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (icon != null && !icon.isRecycled()) {
                icon.recycle();
                System.gc();
            }
        }
    }


    /**
     *
     * @param src
     * @param mark
     * @param X
     * @param Y
     * @param color
     * @param fontName
     * @param fontSize
     * @param promise
     */
    @ReactMethod
    public void addText(
            ReadableMap src,
            final String mark,
            final Integer X,
            final Integer Y,
            final String color,
            final String fontName,
            final Integer fontSize,
            ReadableMap shadowStyle,
            final float scale,
            final int quality,
            String filename,
            final Promise promise
    ) {
        if (TextUtils.isEmpty(mark)){
            promise.reject("error", "mark should not be empty");
        }

        try {

            final String uri = src.getString(PROP_ICON_URI);

            final String dest = generateCacheFilePathForMarker(uri, filename);

            final ShadowLayerStyle myShadowStyle  = null != shadowStyle? new ShadowLayerStyle(shadowStyle) : null;

            Log.d(IMAGE_MARKER_TAG, uri);
            Log.d(IMAGE_MARKER_TAG, src.toString());

            if (isFrescoImg(uri)) {
                ImageRequest imageRequest = ImageRequest.fromUri(uri);
                DataSource<CloseableReference<CloseableImage>> dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, null);
                Executor executor = Executors.newSingleThreadExecutor();
                dataSource.subscribe(new BaseBitmapDataSubscriber() {
                    @Override
                    public void onNewResultImpl(@Nullable Bitmap bitmap) {
                        if (bitmap != null) {
                            Bitmap bg = Utils.scaleBitmap(bitmap, scale);
                            markImageByText(bg, mark, null, color, fontName, fontSize, myShadowStyle, X, Y, quality, dest, promise);
                        } else {
                            promise.reject( "marker error","Can't retrieve the file from the src: " + uri);
                        }
                    }

                    @Override
                    public void onFailureImpl(DataSource dataSource) {
                        promise.reject( "error","Can't request the image from the uri: " + uri, dataSource.getFailureCause());
                    }
                }, executor);
            } else {
                int resId = getDrawableResourceByName(uri);
                if (resId == 0) {
                    Log.d(IMAGE_MARKER_TAG, "cannot find res");
                    promise.reject( "error","Can't get resource by the path: " + uri);
                } else {
                    Log.d(IMAGE_MARKER_TAG, "res：" + resId);

                    Resources r = this.getResources();
//                    InputStream is = r.openRawResource(resId);
                    Bitmap bitmap = BitmapFactory.decodeResource(r, resId);
//                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    Log.d(IMAGE_MARKER_TAG, bitmap.getHeight() + "");
                    Bitmap bg = Utils.scaleBitmap(bitmap, scale);
                    Log.d(IMAGE_MARKER_TAG, bg.getHeight() + "");

                    if (bitmap != null && !bitmap.isRecycled() && scale != 1) {
                        bitmap.recycle();
                        System.gc();
                    }
                    markImageByText(bg, mark, null, color, fontName, fontSize, myShadowStyle, X, Y, quality, dest, promise);
                }
            }
        } catch (Exception e) {
            Log.d(IMAGE_MARKER_TAG, "error：" + e.getMessage());
            e.printStackTrace();
            promise.reject("error", e.getMessage(), e);
        }
    }

    /**
     *
     * @param src
     * @param mark
     * @param position
     * @param color
     * @param fontName
     * @param fontSize
     * @param promise
     */
    @ReactMethod
    public void addTextByPostion(
            ReadableMap src,
            final String mark,
            final String position,
            final String color,
            final String fontName,
            final Integer fontSize,
            ReadableMap shadowStyle,
            final float scale,
            final Integer quality,
            String filename,
            final Promise promise
    ) {
        if (TextUtils.isEmpty(mark)){
            promise.reject("error", "mark should not be empty");
        }
        try {

            final String uri = src.getString(PROP_ICON_URI);

            final String dest = generateCacheFilePathForMarker(uri, filename);

            final ShadowLayerStyle myShadowStyle  = null != shadowStyle? new ShadowLayerStyle(shadowStyle) : null;


            Log.d(IMAGE_MARKER_TAG, uri);
            Log.d(IMAGE_MARKER_TAG, src.toString());

            if (isFrescoImg(uri)) {
                ImageRequest imageRequest = ImageRequest.fromUri(uri);
                DataSource<CloseableReference<CloseableImage>> dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, null);
                Executor executor = Executors.newSingleThreadExecutor();
                dataSource.subscribe(new BaseBitmapDataSubscriber() {
                    @Override
                    public void onNewResultImpl(@Nullable Bitmap bitmap) {
                        if (bitmap != null) {
                            Bitmap bg = Utils.scaleBitmap(bitmap, scale);
                            markImageByText(bg, mark, position, color, fontName, fontSize, myShadowStyle,null, null, quality, dest, promise);
                        } else {
                            promise.reject( "marker error","Can't retrieve the file from the src: " + uri);
                        }
                    }

                    @Override
                    public void onFailureImpl(DataSource dataSource) {
                        promise.reject( "error","Can't request the image from the uri: " + uri, dataSource.getFailureCause());
                    }
                }, executor);
            } else {
                int resId = getDrawableResourceByName(uri);
                if (resId == 0) {
                    Log.d(IMAGE_MARKER_TAG, "cannot find res");
                    promise.reject( "error","Can't get resource by the path: " + uri);
                } else {
                    Log.d(IMAGE_MARKER_TAG, "res：" + resId);

                    Resources r = this.getResources();
//                    InputStream is = r.openRawResource(resId);
                    Bitmap bitmap = BitmapFactory.decodeResource(r, resId);
//                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    Log.d(IMAGE_MARKER_TAG, bitmap.getHeight() + "");
                    Bitmap bg = Utils.scaleBitmap(bitmap, scale);
                    Log.d(IMAGE_MARKER_TAG, bg.getHeight() + "");

                    if (bitmap != null && !bitmap.isRecycled() && scale != 1) {
                        bitmap.recycle();
                        System.gc();
                    }
                    markImageByText(bg, mark, position, color, fontName, fontSize, myShadowStyle, null, null, quality, dest, promise);
                }
            }
        } catch (Exception e) {
            Log.d(IMAGE_MARKER_TAG, "error：" + e.getMessage());
            e.printStackTrace();
            promise.reject("error", e.getMessage(), e);
        }
    }

    boolean isOver=false;


    @ReactMethod
    public void markWithObjects(ReadableMap src, final ReadableArray markers, final int quality, String filename, final Promise promise ) {
    try{
        Log.e("SRC", "markWithObjects: "+src.toString() );
        Log.e("SRC", "markers: "+markers.toString() );

        isOver=false;
        final String uri = src.getString(PROP_ICON_URI);

        final String dest =generateCacheFilePathForMarker(uri, filename);

        Log.d(IMAGE_MARKER_TAG, uri);
        Log.d(IMAGE_MARKER_TAG, src.toString());



        final Paint photoPaint = new Paint();
        photoPaint.setDither(true);





        ImageRequest imageRequest = ImageRequest.fromUri(uri);
        DataSource<CloseableReference<CloseableImage>> dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, null);
        Executor executor = Executors.newSingleThreadExecutor();
        dataSource.subscribe(new BaseBitmapDataSubscriber() {
            @Override
            public void onNewResultImpl(@Nullable final Bitmap bg) {
                if (bg != null) {
                    int width = bg.getWidth();
                    int height = bg.getHeight();
                    //创建一个bitmap
                    Bitmap newb = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);// 创建一个新的和SRC长度宽度一样的位图
                    //将该图片作为画布
                    final Canvas canvas = new Canvas(newb);
                    //在画布 0，0坐标上开始绘制原始图片
                    canvas.drawBitmap(bg, 0, 0, null);

                    for (int i=0;i<markers.size();i++){
                        final ReadableMap map= markers.getMap(i);
                        if(map.getInt("type")==1){//icon http网络头像
                            final String photoUri=map.getString("url");
                            ImageRequest imageRequest = ImageRequest.fromUri(photoUri);
                            DataSource<CloseableReference<CloseableImage>> dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, null);
                            Executor executor = Executors.newSingleThreadExecutor();
                            dataSource.subscribe(new BaseBitmapDataSubscriber() {
                                @Override
                                public void onNewResultImpl(@Nullable Bitmap bitmap) {
                                    Log.e("SRC", "头像: "+bitmap );
                                    if (bitmap != null) {
                                        canvas.drawBitmap(bitmap,map.getInt("X"),map.getInt("Y"),photoPaint);
                                        if (bitmap.isRecycled()) {
                                            bitmap.recycle();
                                        }
                                    } else {
                                        Log.e("SRC", "rrrrrrrrr5555555");
                                        promise.reject( "marker error","Can't retrieve the file from the markerpath: " + photoUri);
                                    }
                                    isOver=true;
                                }

                                @Override
                                public void onFailureImpl(DataSource dataSource) {
                                    Log.e("SRC", "rrrrrrrrr444444444444 ");
                                    promise.reject( "error","Can't request the image from the uri: " + photoUri, dataSource.getFailureCause());
                                }
                            }, executor);

                        }else {
                            Log.e("SRC", "文字: "+map.getString("text") );

                            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                            Log.e("", map.getInt("X") + "  xxxxxx " + map.getInt("Y"));
                            Log.e("", "test:   oooo" + width + " " + height);
                            textPaint.setTextSize(map.getInt("fontSize")<=0?36:map.getInt("fontSize"));
                                try {
                                    textPaint.setColor(Color.parseColor(map.getString("color")));
                                    if(map.getString("fontName")!=null) {
                                        //设置字体失败时使用默认字体
                                        textPaint.setTypeface(ReactFontManager.getInstance().getTypeface(map.getString("fontName"), Typeface.BOLD, getReactApplicationContext().getAssets()));
                                    }
                                    } catch (Exception e) {
                                    textPaint.setTypeface(Typeface.DEFAULT);
                                }
                            //textPaint.getTextBounds(paintBean.getText(), 0, paintBean.getText().length(), textBounds);
                            canvas.drawText(map.getString("text"), map.getInt("X"), map.getInt("Y"), textPaint);

                        }
                        Log.e("SRC", "onNewResultImpl: iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii"+i);


                    }
                    canvas.save();

                    canvas.restore();
                    try {
                        BufferedOutputStream bos = null;
                        bos = new BufferedOutputStream(new FileOutputStream(dest));
                        newb.compress(Bitmap.CompressFormat.PNG, quality, bos);
                        bos.flush();
                        //保存成功的
                        Log.e("SRC", "rrrrrrrrr33333333333");
                        promise.resolve(dest);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } else {
                    Log.e("SRC", "rrrrrrrrr111111111111 ");
                    promise.reject( "marker error","Can't retrieve the file from the src: " + uri);
                }
            }

            @Override
            public void onFailureImpl(DataSource dataSource) {
                Log.e("SRC", "rrrrrrrrr222222222222222 ");
                promise.reject( "error","Can't request the image from the uri: " + uri, dataSource.getFailureCause());
            }
        }, executor);







    } catch (Exception e) {
        Log.d(IMAGE_MARKER_TAG, "error：" + e.getMessage());
        e.printStackTrace();
        promise.reject("error", e.getMessage(), e);
    }

}


    @ReactMethod
    public void markWithImage(ReadableMap src, final ReadableMap marker, final Integer X, final Integer Y, final Float scale, final Float markerScale, final int quality, String filename, final Promise promise ) {


        try {

//            String resultFile = generateCacheFilePathForMarker(imgSavePath, filename);


            final String uri = src.getString(PROP_ICON_URI);

            final String dest = generateCacheFilePathForMarker(uri, filename);

            Log.d(IMAGE_MARKER_TAG, uri);
            Log.d(IMAGE_MARKER_TAG, src.toString());

            if (isFrescoImg(uri)) {
                ImageRequest imageRequest = ImageRequest.fromUri(uri);
                DataSource<CloseableReference<CloseableImage>> dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, null);
                Executor executor = Executors.newSingleThreadExecutor();
                dataSource.subscribe(new BaseBitmapDataSubscriber() {
                    @Override
                    public void onNewResultImpl(@Nullable Bitmap bitmap) {
                        if (bitmap != null) {
                            Bitmap bg = Utils.scaleBitmap(bitmap, scale);
                            markImage(bg, marker, null, X, Y, markerScale, quality, dest, promise);
                        } else {
                            promise.reject( "marker error","Can't retrieve the file from the src: " + uri);
                        }
                    }

                    @Override
                    public void onFailureImpl(DataSource dataSource) {
                        promise.reject( "error","Can't request the image from the uri: " + uri, dataSource.getFailureCause());
                    }
                }, executor);
            } else {
                int resId = getDrawableResourceByName(uri);
                if (resId == 0) {
                    Log.d(IMAGE_MARKER_TAG, "cannot find res");
                    promise.reject( "error","Can't get resource by the path: " + uri);
                } else {
                    Log.d(IMAGE_MARKER_TAG, "res：" + resId);

                    Resources r = this.getResources();
//                    InputStream is = r.openRawResource(resId);
                    Bitmap bitmap = BitmapFactory.decodeResource(r, resId);
//                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    Log.d(IMAGE_MARKER_TAG, bitmap.getHeight() + "");
                    Bitmap bg = Utils.scaleBitmap(bitmap, scale);
                    Log.d(IMAGE_MARKER_TAG, bg.getHeight() + "");

                    if (bitmap != null && !bitmap.isRecycled() && scale != 1) {
                        bitmap.recycle();
                        System.gc();
                    }
                    markImage(bg, marker, null, X, Y, markerScale, quality, dest, promise);
                }
            }
        } catch (Exception e) {
            Log.d(IMAGE_MARKER_TAG, "error：" + e.getMessage());
            e.printStackTrace();
            promise.reject("error", e.getMessage(), e);
        }

    }

    @ReactMethod
    public void markWithImageByPosition(ReadableMap src, final ReadableMap marker, final String position, final Float scale, final Float markerScale, final int quality, final String filename, final Promise promise ) {

        try {

            final String uri = src.getString(PROP_ICON_URI);

            final String dest = generateCacheFilePathForMarker(uri, filename);

            Log.d(IMAGE_MARKER_TAG, uri);
            Log.d(IMAGE_MARKER_TAG, src.toString());

            if (isFrescoImg(uri)) {
                ImageRequest imageRequest = ImageRequest.fromUri(uri);
                DataSource<CloseableReference<CloseableImage>> dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, null);
                Executor executor = Executors.newSingleThreadExecutor();
                dataSource.subscribe(new BaseBitmapDataSubscriber() {
                    @Override
                    public void onNewResultImpl(@Nullable Bitmap bitmap) {
                        if (bitmap != null) {
                            Bitmap bg = Utils.scaleBitmap(bitmap, scale);
                            markImage(bg, marker, position, 0, 0, markerScale, quality, dest, promise);
                        } else {
                            promise.reject( "marker error","Can't retrieve the file from the src: " + uri);
                        }
                    }

                    @Override
                    public void onFailureImpl(DataSource dataSource) {
                        promise.reject( "error","Can't request the image from the uri: " + uri, dataSource.getFailureCause());
                    }
                }, executor);
            } else {
                int resId = getDrawableResourceByName(uri);
                if (resId == 0) {
                    Log.d(IMAGE_MARKER_TAG, "cannot find res");
                    promise.reject( "error","Can't get resource by the path: " + uri);
                } else {
                    Log.d(IMAGE_MARKER_TAG, "res：" + resId);

                    Resources r = this.getResources();
//                    InputStream is = r.openRawResource(resId);
                    Bitmap bitmap = BitmapFactory.decodeResource(r, resId);
//                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    Log.d(IMAGE_MARKER_TAG, bitmap.getHeight() + "");
                    Bitmap bg = Utils.scaleBitmap(bitmap, scale);
                    Log.d(IMAGE_MARKER_TAG, bg.getHeight() + "");

                    if (bitmap != null && !bitmap.isRecycled() && scale != 1) {
                        bitmap.recycle();
                        System.gc();
                    }
                    markImage(bg, marker, position, 0, 0, markerScale, quality, dest, promise);
                }
            }
        } catch (Exception e) {
            Log.d(IMAGE_MARKER_TAG, "error：" + e.getMessage());
            e.printStackTrace();
            promise.reject("error", e.getMessage(), e);
        }
    }

    static Position getRectFromPosition(String position, int width, int height, int imageWidth, int imageHeigt){
        Log.d("marker", "getRectFromPosition: "+position +" width:" +width+" height: "+height + " imageWidth: " + imageHeigt+" imageHeigt:" + imageHeigt);

        int left = 20;
        int top = 40;
        int right = imageWidth - width;
        Position pos = new Position(left, top);
        switch (position) {
            case "topLeft":
                Log.d("marker", "getRectFromPosition: "+position);
                break;
            case "topCenter":
                left = (imageWidth)/2-width/2;
                pos.setX(left);
                break;
            case "topRight":
                pos.setX(right-20);
                break;
            case "center":
                left = (imageWidth)/2 - width/2;
                top = (imageHeigt)/2 - height/2;
                pos.setX(left);
                pos.setY(top);
                break;
            case "bottomLeft":
                top = imageHeigt - height;
                pos.setY(top-20);
                break;
            case "bottomRight":
                top = imageHeigt - height;
                left = imageWidth - width - 20;
                pos.setX(left-20);
                pos.setY(top-20);
                break;
            case "bottomCenter":
                top = imageHeigt - height;
                left = (imageWidth)/2 - width/2;
                pos.setX(left-20);
                pos.setY(top-20);

        }
        return pos;
    }

    private String generateCacheFilePathForMarker(String imgSavePath, String filename){
        String cacheDir = this.getReactApplicationContext().getCacheDir().getAbsolutePath();

        if (null != filename) {
            if (filename.endsWith(".jpg") || filename.endsWith(".png"))
                return cacheDir + "/" + filename;
            else
                return cacheDir + "/" + filename + ".jpg";
        } else {
            String name = UUID.randomUUID().toString()+"imagemarker";
            return cacheDir+"/"+name+".jpg";
        }
    }
}
