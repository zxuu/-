package com.zxu.picturesxiangce.avtivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bumptech.glide.Glide;
import com.tencent.liteav.demo.common.utils.FileUtils;
import com.tencent.liteav.demo.common.utils.TCConstants;
//import com.tencent.liteav.demo.videoupload.TXUGCPublish;
//import com.tencent.liteav.demo.videoupload.TXUGCPublishTypeDef;
import com.tencent.liteav.demo.videoediter.TemImgUtil;
import com.tencent.rtmp.ITXVodPlayListener;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXVodPlayConfig;
import com.tencent.rtmp.TXVodPlayer;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.zxu.picturesxiangce.MyContext;
import com.zxu.picturesxiangce.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpStatus;
import cz.msebera.android.httpclient.client.methods.CloseableHttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.entity.ContentType;
import cz.msebera.android.httpclient.entity.mime.MultipartEntityBuilder;
import cz.msebera.android.httpclient.entity.mime.content.FileBody;
import cz.msebera.android.httpclient.entity.mime.content.StringBody;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClients;
import cz.msebera.android.httpclient.util.EntityUtils;
import dmax.dialog.SpotsDialog;

/**
 * 录制完成后的预览界面
 * Created by carolsuo on 2017/3/21.
 */

public class TCVideoPreviewActivity extends Activity implements View.OnClickListener, ITXVodPlayListener {
    public static final String TAG = "TCVideoPreviewActivity";

    private int mVideoSource; // 视频来源

    ImageView mStartPreview;
    boolean mVideoPlay = false;
    boolean mVideoPause = false;
    boolean mAutoPause = false;

    private ImageView mIvPublish;
    private ImageView mIvToEdit;
    private String mVideoPath;
    private String mCoverImagePath;
    ImageView mImageViewBg;
    private TXVodPlayer mTXVodPlayer = null;
    private TXVodPlayConfig mTXPlayConfig = null;
    private TXCloudVideoView mTXCloudVideoView;
    private SeekBar mSeekBar;
    private TextView mProgressTime;
    private long mTrackingTouchTS = 0;
    private boolean mStartSeek = false;
    //错误消息弹窗
    private ErrorDialogFragment mErrDlgFragment;
    //视频时长（ms）
    private long mVideoDuration;
    //录制界面传过来的视频分辨率
    private int mVideoResolution;
    private Button mButtonThumbnail;
    private SpotsDialog spotsDialog;
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    spotsDialog.dismiss();
                    Toast.makeText(TCVideoPreviewActivity.this, "上传成功", Toast.LENGTH_SHORT).show();
                    break;
                case 1:
                    spotsDialog.dismiss();
                    Toast.makeText(TCVideoPreviewActivity.this, "上传失败", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mErrDlgFragment = new ErrorDialogFragment();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_video_preview);

        mStartPreview = (ImageView) findViewById(R.id.record_preview);
//        mIvToEdit = (ImageView) findViewById(R.id.record_to_edit);
//        mIvToEdit.setOnClickListener(this);

//        mIvPublish = (ImageView) findViewById(R.id.video_publish);

        mVideoSource = getIntent().getIntExtra(TCConstants.VIDEO_RECORD_TYPE, TCConstants.VIDEO_RECORD_TYPE_EDIT);
        mVideoPath = getIntent().getStringExtra(TCConstants.VIDEO_RECORD_VIDEPATH);
        mCoverImagePath = getIntent().getStringExtra(TCConstants.VIDEO_RECORD_COVERPATH);
        mVideoDuration = getIntent().getLongExtra(TCConstants.VIDEO_RECORD_DURATION, 0);
        Toast.makeText(this, mVideoPath, Toast.LENGTH_SHORT).show();
        mVideoResolution = getIntent().getIntExtra(TCConstants.VIDEO_RECORD_RESOLUTION, -1);
        Log.i(TAG, "onCreate: mVideoPath = " + mVideoPath + ",mVideoDuration = " + mVideoDuration);
        mImageViewBg = (ImageView) findViewById(R.id.cover);
        if (mCoverImagePath != null && !mCoverImagePath.isEmpty()) {
            Glide.with(this).load(Uri.fromFile(new File(mCoverImagePath)))
                    .into(mImageViewBg);
        }

        mTXVodPlayer = new TXVodPlayer(this);
        mTXPlayConfig = new TXVodPlayConfig();
        mTXCloudVideoView = (TXCloudVideoView) findViewById(R.id.video_view);
        findViewById(R.id.video_upload).setOnClickListener(this);
        mTXCloudVideoView.disableLog(true);

        mSeekBar = (SeekBar) findViewById(R.id.seekbar);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean bFromUser) {
                if (mProgressTime != null) {
                    mProgressTime.setText(String.format(Locale.CHINA, "%02d:%02d/%02d:%02d", (progress) / 60, (progress) % 60, (seekBar.getMax()) / 60, (seekBar.getMax()) % 60));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mStartSeek = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mTXVodPlayer != null) {
                    mTXVodPlayer.seek(seekBar.getProgress());
                }
                mTrackingTouchTS = System.currentTimeMillis();
                mStartSeek = false;
            }
        });
        mProgressTime = (TextView) findViewById(R.id.progress_time);

//        mIvPublish.setVisibility(View.GONE);

        if (mVideoSource == TCConstants.VIDEO_RECORD_TYPE_UGC_RECORD) {
//            mIvToEdit.setVisibility(View.VISIBLE);
        }

        mButtonThumbnail = (Button) findViewById(R.id.button);
        mButtonThumbnail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                List<Long> list = new ArrayList<>();
//                list.add(10000L);
//                list.add(12000L);
//                list.add(13000L);
//                list.add(14000L);
//                list.add(15000L);
//                list.add(16000L);
//                list.add(17000L);
//                list.add(18000L);
//                list.add(19000L);
//                list.add(20000L);
//
//                TXVideoEditer txVideoEditer = new TXVideoEditer(TCVideoPreviewActivity.this);
//                txVideoEditer.setVideoPath(mVideoPath);
//                txVideoEditer.getThumbnail(list, 200, 200, false, mThumbnailListener);
            }
        });
    }

//    private TXVideoEditer.TXThumbnailListener mThumbnailListener = new TXVideoEditer.TXThumbnailListener() {
//        @Override
//
//        public void onThumbnail(int index, long timeMs, final Bitmap bitmap) {
//            Log.i(TAG, "bitmap:" + bitmap + ",timeMs:" + timeMs);
//            saveBitmap(bitmap, timeMs);
//        }
//    };

    public static void saveBitmap(Bitmap bitmap, long time) {
        File dir = new File("/sdcard/txrtmp/");
        if (!dir.exists())
            dir.mkdirs();
        File f = new File(dir, String.valueOf(time) + ".jpg");
        if (f.exists()) {
            f.delete();
        }
        try {
            FileOutputStream out = new FileOutputStream(f);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.flush();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.record_delete:
                deleteVideo();
                FileUtils.deleteFile(mCoverImagePath);
                break;
            case R.id.record_download:
                downloadRecord();
                break;
            case R.id.record_preview:
                if (mVideoPlay) {
                    if (mVideoPause) {
                        mTXVodPlayer.resume();
                        mStartPreview.setBackgroundResource(R.drawable.icon_record_pause);
                        mVideoPause = false;
                    } else {
                        mTXVodPlayer.pause();
                        mStartPreview.setBackgroundResource(R.drawable.icon_record_start);
                        mVideoPause = true;
                    }
                } else {
                    startPlay();
                }
                break;
            case R.id.video_upload:
                spotsDialog = new SpotsDialog(TCVideoPreviewActivity.this,"上传中.......");
                spotsDialog.show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        uploadVideo();
                    }
                }).start();

                break;
//            case R.id.video_publish:
//                //publish();
//                break;
//            case R.id.record_to_edit:
//                startEditVideo();
//                break;
            default:
                break;
        }

    }

    private void startEditVideo() {
        // 播放器版本没有此activity
        Intent intent = new Intent();
        intent.setAction("com.tencent.liteav.demo.videopreprocess");
        intent.putExtra(TCConstants.VIDEO_EDITER_PATH, mVideoPath);
        intent.putExtra(TCConstants.VIDEO_RECORD_COVERPATH, mCoverImagePath);
        intent.putExtra(TCConstants.VIDEO_RECORD_TYPE, mVideoSource);
        intent.putExtra(TCConstants.VIDEO_RECORD_RESOLUTION, mVideoResolution);
        startActivity(intent);
        finish();
    }

//    private void publish() {
//        stopPlay(false);
//        TXUGCPublish txugcPublish = new TXUGCPublish(this.getApplicationContext(), "customID");
//        txugcPublish.setListener(new TXUGCPublishTypeDef.ITXVideoPublishListener() {
//            @Override
//            public void onPublishProgress(long uploadBytes, long totalBytes) {
//                TXLog.d(TAG, "onPublishProgress [" + uploadBytes + "/" + totalBytes + "]");
//            }
//
//            @Override
//            public void onPublishComplete(TXUGCPublishTypeDef.TXPublishResult result) {
//                TXLog.d(TAG, "onPublishComplete [" + result.retCode + "/" + (result.retCode == 0 ? result.videoURL : result.descMsg) + "]");
//
//            }
//        });
//
//        TXUGCPublishTypeDef.TXPublishParam param = new TXUGCPublishTypeDef.TXPublishParam();
//        // signature计算规则可参考 https://www.qcloud.com/document/product/266/9221
//        param.signature = "";
//        param.videoPath = mVideoPath;
//        param.coverPath = mCoverImagePath;
//        txugcPublish.publishVideo(param);
//        finish();
//    }

    private boolean startPlay() {
        mStartPreview.setBackgroundResource(R.drawable.icon_record_pause);
        mTXVodPlayer.setPlayerView(mTXCloudVideoView);
        mTXVodPlayer.setVodListener(this);

        mTXVodPlayer.enableHardwareDecode(false);
        mTXVodPlayer.setRenderRotation(TXLiveConstants.RENDER_ROTATION_PORTRAIT);
        mTXVodPlayer.setRenderMode(TXLiveConstants.RENDER_MODE_ADJUST_RESOLUTION);

        mTXVodPlayer.setConfig(mTXPlayConfig);

        int result = mTXVodPlayer.startPlay(mVideoPath); // result返回值：0 success;  -1 empty url; -2 invalid url; -3 invalid playType;
        if (result != 0) {
            mStartPreview.setBackgroundResource(R.drawable.icon_record_start);
            return false;
        }

        mVideoPlay = true;
        return true;
    }

    private static ContentValues initCommonContentValues(File saveFile) {
        ContentValues values = new ContentValues();
        long currentTimeInSeconds = System.currentTimeMillis();
        values.put(MediaStore.MediaColumns.TITLE, saveFile.getName());
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, saveFile.getName());
        values.put(MediaStore.MediaColumns.DATE_MODIFIED, currentTimeInSeconds);
        values.put(MediaStore.MediaColumns.DATE_ADDED, currentTimeInSeconds);
        values.put(MediaStore.MediaColumns.DATA, saveFile.getAbsolutePath());
        values.put(MediaStore.MediaColumns.SIZE, saveFile.length());

        return values;
    }

    private void downloadRecord() {
        File file = new File(mVideoPath);
        if (file.exists()) {
            try {
                File newFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + File.separator + file.getName());
//                if (!newFile.exists()) {
//                    newFile = new File(getExternalFilesDir(Environment.DIRECTORY_DCIM).getPath() + File.separator + file.getName());
//                }

                file.renameTo(newFile);
                mVideoPath = newFile.getAbsolutePath();

                ContentValues values = initCommonContentValues(newFile);
                values.put(MediaStore.Video.VideoColumns.DATE_TAKEN, System.currentTimeMillis());
                values.put(MediaStore.MediaColumns.MIME_TYPE, "login/mp4");
                values.put(MediaStore.Video.VideoColumns.DURATION, mVideoDuration);//时长
                this.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

                insertVideoThumb(newFile.getPath(), mCoverImagePath);
            } catch (Exception e) {
                e.printStackTrace();
            }
//            finish();
        }
    }

    /**
     * 插入视频缩略图
     *
     * @param videoPath
     * @param coverPath
     */
    private void insertVideoThumb(String videoPath, String coverPath) {
        //以下是查询上面插入的数据库Video的id（用于绑定缩略图）
        //根据路径查询
        Cursor cursor = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Video.Thumbnails._ID},//返回id列表
                String.format("%s = ?", MediaStore.Video.Thumbnails.DATA), //根据路径查询数据库
                new String[]{videoPath}, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                String videoId = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Thumbnails._ID));
                //查询到了Video的id
                ContentValues thumbValues = new ContentValues();
                thumbValues.put(MediaStore.Video.Thumbnails.DATA, coverPath);//缩略图路径
                thumbValues.put(MediaStore.Video.Thumbnails.VIDEO_ID, videoId);//video的id 用于绑定
                //Video的kind一般为1
                thumbValues.put(MediaStore.Video.Thumbnails.KIND,
                        MediaStore.Video.Thumbnails.MINI_KIND);
                //只返回图片大小信息，不返回图片具体内容
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                Bitmap bitmap = BitmapFactory.decodeFile(coverPath, options);
                if (bitmap != null) {
                    thumbValues.put(MediaStore.Video.Thumbnails.WIDTH, bitmap.getWidth());//缩略图宽度
                    thumbValues.put(MediaStore.Video.Thumbnails.HEIGHT, bitmap.getHeight());//缩略图高度
                    if (!bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                }
                this.getContentResolver().insert(
                        MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI, //缩略图数据库
                        thumbValues);
            }
            cursor.close();
        }
    }

    private void deleteVideo() {
        stopPlay(true);
        //删除文件
        FileUtils.deleteFile(mVideoPath);
        finish();
    }

    private void uploadVideo() {
        HttpPost httpPost = new HttpPost(MyContext.DJANGOSERVER+ MyContext.UPLOADFILE);
        CloseableHttpClient httpClient = HttpClients.createDefault();

//        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/pada.mp4";
        FileBody upLoadVideo = new FileBody(new File(mVideoPath));

        StringBody myName = new StringBody("zxu", ContentType.TEXT_PLAIN);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        for (String imgPath : TemImgUtil.imgPathList) {
            FileBody fileBody = new FileBody(new File(imgPath));
            builder.addPart("files",fileBody);
        }
        builder.addPart("files", upLoadVideo);
        builder.addPart("myName", myName);
        HttpEntity httpEntity = builder.build();

//        HttpEntity reqEntity = MultipartEntityBuilder.create()
//                .addPart("file",upLoadVideo)
//                .addPart("myName", myName)
//                .build();

        httpPost.setEntity(httpEntity);
        try {
            CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity resEntity = response.getEntity();
            int statusCode = response.getStatusLine().getStatusCode();
            Message msg = new Message();
            if (statusCode == HttpStatus.SC_OK) {
                JSONObject jsonpObject = JSON.parseObject(EntityUtils.toString(resEntity));
                String result = (String) jsonpObject.get("result");
                msg.what = 0;
                msg.obj = result;
                mHandler.sendMessage(msg);
//              System.out.println(resEntity.getContent());

            } else if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                msg.what = 1;
                mHandler.sendMessage(msg);
//                Toast.makeText(this, "上传文件发生异常，请检查服务端异常问题", Toast.LENGTH_SHORT).show();
//                System.out.println("上传文件发生异常，请检查服务端异常问题");
            }
            EntityUtils.consume(resEntity);
            response.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            httpClient.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTXCloudVideoView.onResume();
        if (mVideoPlay && mAutoPause) {
            mTXVodPlayer.resume();
            mAutoPause = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mTXCloudVideoView.onPause();
        if (mVideoPlay && !mVideoPause) {
            mTXVodPlayer.pause();
            mAutoPause = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTXCloudVideoView.onDestroy();
        stopPlay(true);
        mTXVodPlayer = null;
        mTXPlayConfig = null;
        mTXCloudVideoView = null;
        if (mSeekBar != null) {
            mSeekBar.setOnSeekBarChangeListener(null);
        }
    }

    protected void stopPlay(boolean clearLastFrame) {
        if (mTXVodPlayer != null) {
            mTXVodPlayer.setVodListener(null);
            mTXVodPlayer.stopPlay(clearLastFrame);
            mVideoPlay = false;
        }
    }

    @Override
    public void onPlayEvent(TXVodPlayer player, int event, Bundle param) {
        if (mTXCloudVideoView != null) {
            mTXCloudVideoView.setLogText(null, param, event);
        }
        if (event == TXLiveConstants.PLAY_EVT_PLAY_PROGRESS) {
            if (mStartSeek) {
                return;
            }
            if (mImageViewBg.isShown()) {
                mImageViewBg.setVisibility(View.GONE);
            }
            int progress = param.getInt(TXLiveConstants.EVT_PLAY_PROGRESS);
            int duration = param.getInt(TXLiveConstants.EVT_PLAY_DURATION);//单位为s
            long curTS = System.currentTimeMillis();
            // 避免滑动进度条松开的瞬间可能出现滑动条瞬间跳到上一个位置
            if (Math.abs(curTS - mTrackingTouchTS) < 500) {
                return;
            }
            mTrackingTouchTS = curTS;

            if (mSeekBar != null) {
                mSeekBar.setProgress(progress);
            }
            if (mProgressTime != null) {
                mProgressTime.setText(String.format(Locale.CHINA, "%02d:%02d/%02d:%02d", (progress) / 60, progress % 60, (duration) / 60, duration % 60));
            }

            if (mSeekBar != null) {
                mSeekBar.setMax(duration);
            }
        } else if (event == TXLiveConstants.PLAY_ERR_NET_DISCONNECT) {

            showErrorAndQuit(TCConstants.ERROR_MSG_NET_DISCONNECTED);

        } else if (event == TXLiveConstants.PLAY_EVT_PLAY_END) {
            mTXVodPlayer.resume(); // 播放结束后，可以直接resume()，如果调用stop和start，会导致重新播放会黑一下
        }
    }

    @Override
    public void onNetStatus(TXVodPlayer player, Bundle bundle) {

    }

    public static class ErrorDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.ConfirmDialogStyle)
                    .setCancelable(true)
                    .setTitle(getArguments().getString("errorMsg"))
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();

                            getActivity().finish();
                        }
                    });
            AlertDialog alertDialog = builder.create();
            alertDialog.setCancelable(false);
            alertDialog.setCanceledOnTouchOutside(false);
            return alertDialog;
        }
    }

    protected void showErrorAndQuit(String errorMsg) {

        if (!mErrDlgFragment.isAdded() && !this.isFinishing()) {
            Bundle args = new Bundle();
            args.putString("errorMsg", errorMsg);
            mErrDlgFragment.setArguments(args);
            mErrDlgFragment.setCancelable(false);

            //此处不使用用.show(...)的方式加载dialogfragment，避免IllegalStateException
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.add(mErrDlgFragment, "loading");
            transaction.commitAllowingStateLoss();
        }
    }
}
