package com.zxu.picturesxiangce.fragment;


import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.dingmouren.layoutmanagergroup.viewpager.OnViewPagerListener;
import com.dingmouren.layoutmanagergroup.viewpager.ViewPagerLayoutManager;
import com.downloader.Error;
import com.downloader.OnCancelListener;
import com.downloader.OnDownloadListener;
import com.downloader.OnPauseListener;
import com.downloader.OnProgressListener;
import com.downloader.OnStartOrResumeListener;
import com.downloader.PRDownloader;
import com.downloader.Progress;
import com.like.LikeButton;
import com.like.OnLikeListener;
import com.qintong.library.InsLoadingView;
import com.zxu.picturesxiangce.MyContext;
import com.zxu.picturesxiangce.R;

import com.zxu.picturesxiangce.avtivity.VideoDetailActivity;
import com.zxu.picturesxiangce.bean.Image;
import com.zxu.picturesxiangce.bean.User;
import com.zxu.picturesxiangce.bean.Video;
import com.zxu.picturesxiangce.gallery.GalleryActivity;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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

import static android.support.constraint.Constraints.TAG;

public class MainFragment extends Fragment {
    private static final String TAG = "MainFragment";
    private RecyclerView mRecyclerView;
    private MyAdapter mAdapter;
    private ViewPagerLayoutManager mLayoutManager;
    SpotsDialog spotsDialog;
    User me = new User();

    List<Video> list = new ArrayList<>();
    List<User> userslist = new ArrayList<>();
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    List<Video> videoList = (List<Video>) msg.obj;
                    mLayoutManager = new ViewPagerLayoutManager(getContext(), OrientationHelper.VERTICAL);
                    mAdapter = new MyAdapter(videoList);
//                    Toast.makeText(getContext(), me.getName(), Toast.LENGTH_SHORT).show();
                    mRecyclerView.setLayoutManager(mLayoutManager);
                    mRecyclerView.setAdapter(mAdapter);

                    initListener();
                    break;
                case 2://点赞结果
                    Toast.makeText(getContext(), msg.obj.toString(), Toast.LENGTH_SHORT).show();
                    break;
                case 3:
                    if (msg.obj.equals("ok")) {
                        Toast.makeText(getContext(), "成功关注", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "关注失败", Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getVideos();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        initView(view);
        return view;
    }
    private void initView(View view) {
        mRecyclerView = view.findViewById(R.id.recycler);
        mLayoutManager = new ViewPagerLayoutManager(getContext(), OrientationHelper.VERTICAL);
    }

    private void initListener(){
        mLayoutManager.setOnViewPagerListener(new OnViewPagerListener() {
            @Override
            public void onPageRelease(boolean isNext,int position) {
                Log.e(TAG,"释放位置:"+position +" 下一页:"+isNext);
                int index = 0;
                if (isNext){
                    index = 0;
                }else {
                    index = 1;
                }
                releaseVideo(index);
            }

            @Override
            public void onPageSelected(int position,boolean isBottom) {
                Log.e(TAG,"选中位置:"+position+"  是否是滑动到底部:"+isBottom);
                playVideo(0);
            }

            @Override
            public void onLayoutComplete() {
                playVideo(0);
            }

        });
    }

    private void playVideo(int position) {
        View itemView = mRecyclerView.getChildAt(0);
        final VideoView videoView = itemView.findViewById(R.id.video_view_v);
        final ImageView imgPlay = itemView.findViewById(R.id.img_play);
        final ImageView imgThumb = itemView.findViewById(R.id.img_thumb);
        final RelativeLayout rootView = itemView.findViewById(R.id.root_view);
        final MediaPlayer[] mediaPlayer = new MediaPlayer[1];
        videoView.start();
        videoView.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                mediaPlayer[0] = mp;
                Log.e(TAG,"onInfo");
                mp.setLooping(true);
                imgThumb.animate().alpha(0).setDuration(200).start();
                return false;
            }
        });
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.e(TAG,"onPrepared");

            }
        });


        imgPlay.setOnClickListener(new View.OnClickListener() {
            boolean isPlaying = true;
            @Override
            public void onClick(View v) {
                if (videoView.isPlaying()){
                    Log.e(TAG,"isPlaying:"+videoView.isPlaying());
                    imgPlay.animate().alpha(1f).start();
                    videoView.pause();
                    isPlaying = false;
                }else {
                    Log.e(TAG,"isPlaying:"+videoView.isPlaying());
                    imgPlay.animate().alpha(0f).start();
                    videoView.start();
                    isPlaying = true;
                }
            }
        });
    }

    private void releaseVideo(int index){
        View itemView = mRecyclerView.getChildAt(index);
        final VideoView videoView = itemView.findViewById(R.id.video_view_v);
        final ImageView imgThumb = itemView.findViewById(R.id.img_thumb);
        final ImageView imgPlay = itemView.findViewById(R.id.img_play);
        videoView.stopPlayback();
        imgThumb.animate().alpha(1).start();
        imgPlay.animate().alpha(0f).start();
    }

    class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder>{
        //private int[] imgs = {R.mipmap.luoli,R.mipmap.luoli};
        int downloadIdOne;
        private List<Video> mVideoList;
        public MyAdapter(List<Video> videoUrlList){
            this.mVideoList = videoUrlList;
//            this.me = me;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_view_pager,parent,false);
            ViewHolder viewHolder = new ViewHolder(view);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, final int position) {

            for (int i = 0; i < userslist.size(); i++) {
                if (mVideoList.get(position).getUser_name().equals(userslist.get(i).getName())) {
                    User realUserr = userslist.get(i);
                    holder.declaration_tv.setText(realUserr.getDeclaration());
                    Glide.with(getContext())
                            .load(realUserr.getBack_img_url())
                            .asBitmap()
                            .centerCrop()
                            .into(new BitmapImageViewTarget(holder.loadingView){
                                @Override
                                protected void setResource(Bitmap resource) {
                                    RoundedBitmapDrawable circularBitmapDrawable =
                                            RoundedBitmapDrawableFactory.create(getResources(), resource);
                                    circularBitmapDrawable.setCircular(true);
                                    holder.loadingView.setImageDrawable(circularBitmapDrawable);
                                }
                            });

                    break;
                }
            }

            //点击头像跳到用户详情页
            holder.loadingView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int i = 0; i < userslist.size(); i++) {
                        if (mVideoList.get(position).getUser_name().equals(userslist.get(i).getName())) {
                            Intent intent = new Intent(getContext(),VideoDetailActivity.class);
                            intent.putExtra("user", userslist.get(i));
                            startActivity(intent);
                            break;
                        }
                    }
                }
            });
            //点击跳到视频详情页(对应的图片)
            holder.video_detail_tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            List<Image> imageList = getImages(mVideoList.get(position).getId_video());
                            ArrayList<String> images = new ArrayList<>();
                            for (int i = 0; i < imageList.size(); i++) {
                                images.add(imageList.get(i).getUrl_image());
                            }
//                            Log.i(TAG, "run: ---------------------->" + images.get(1));
                            Intent intent = new Intent(v.getContext(), GalleryActivity.class);
                            intent.putStringArrayListExtra("imagesUrl", images);
                            v.getContext().startActivity(intent);
                        }
                    }).start();


                }
            });

            holder.comment_num_tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MyContext.currentVideo = mVideoList.get(position).getId_video();
                    showBottomSheetDialog(position);
                }
            });
            int heartNumm = mVideoList.get(position).getHeart_num();
            holder.heart_num_tv.setText(String.valueOf(heartNumm));
            holder.likeButton.setOnLikeListener(new OnLikeListener() {
                @Override
                public void liked(LikeButton likeButton) {
//                    Toast.makeText(getContext(), "", Toast.LENGTH_SHORT).show();
                    mVideoList.get(position).setHeart_num(mVideoList.get(position).getHeart_num()+1);
                    holder.heart_num_tv.setText(String.valueOf(mVideoList.get(position).getHeart_num()));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            dianZan(mVideoList.get(position).getId_video(),"1");
                        }
                    }).start();

                }

                @Override
                public void unLiked(LikeButton likeButton) {
                    mVideoList.get(position).setHeart_num(mVideoList.get(position).getHeart_num()-1);
                    holder.heart_num_tv.setText(String.valueOf(mVideoList.get(position).getHeart_num()));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            dianZan(mVideoList.get(position).getId_video(),"2");
                        }
                    }).start();
                }
            });

            holder.user_name_tv.setText(mVideoList.get(position).getUser_name());

//            holder.videoView.setVideoURI(Uri.parse("android.resource://"+getContext().getPackageName()+"/"+ videos[position%2]));
            holder.videoView.setVideoURI(Uri.parse(mVideoList.get(position%mVideoList.size()).getUrl_video()));
            holder.guan_zhu_btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            putGuanZhu(mVideoList.get(position).getUser_name());
                        }
                    }).start();
                }
            });
            holder.download.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    spotsDialog = new SpotsDialog(getContext(),"下载中......");
                    spotsDialog.show();
                    final String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath()+"/";
                    Log.i(TAG, "onBindViewHolder: "+dirPath);
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss");
                    final Date date = new Date(System.currentTimeMillis());
                    downloadIdOne = PRDownloader.download(mVideoList.get(position).getUrl_video(), dirPath, simpleDateFormat.format(date)+".mp4")
                            .build()
                            .setOnStartOrResumeListener(new OnStartOrResumeListener() {
                                @Override
                                public void onStartOrResume() {

                                }
                            })
                            .setOnPauseListener(new OnPauseListener() {
                                @Override
                                public void onPause() {

                                }
                            })
                            .setOnCancelListener(new OnCancelListener() {
                                @Override
                                public void onCancel() {

                                }
                            })
                            .setOnProgressListener(new OnProgressListener() {
                                @Override
                                public void onProgress(Progress progress) {
                                    long progressPercent = progress.currentBytes * 100 / progress.totalBytes;

                                }
                            })
                            .start(new OnDownloadListener() {
                                @Override
                                public void onDownloadComplete() {
                                    spotsDialog.dismiss();
                                    Toast.makeText(getContext(), "下载完成", Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onError(Error error) {

                                }
                            });
                }
            });
        }

        @Override
        public int getItemCount() {
            return mVideoList.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder{
            ImageView img_thumb;
            VideoView videoView;
            ImageView img_play;
            RelativeLayout rootView;
            TextView video_detail_tv;
            TextView comment_num_tv;
            TextView heart_num_tv;
            LikeButton likeButton;
            TextView declaration_tv;
            InsLoadingView loadingView;
            TextView user_name_tv;
            Button guan_zhu_btn;
            ImageView download;

            public ViewHolder(View itemView) {
                super(itemView);
                img_thumb = itemView.findViewById(R.id.img_thumb);
                videoView = itemView.findViewById(R.id.video_view_v);
                img_play = itemView.findViewById(R.id.img_play);
                rootView = itemView.findViewById(R.id.root_view);
                video_detail_tv = itemView.findViewById(R.id.video_detail_tv);
                comment_num_tv = itemView.findViewById(R.id.comment_num);
                heart_num_tv = itemView.findViewById(R.id.heart_num);
                likeButton = itemView.findViewById(R.id.heart_button);
                loadingView = itemView.findViewById(R.id.loading_view);
                user_name_tv = itemView.findViewById(R.id.user_name);
                guan_zhu_btn = itemView.findViewById(R.id.guan_zhu_btn);
                declaration_tv = itemView.findViewById(R.id.declaration_tv);
                download = itemView.findViewById(R.id.download_video);
            }
        }
    }

    class uiRunable implements Runnable{

        @Override
        public void run() {

        }
    }

    private void getVideos(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                get();
            }
        }).start();
    }

    private void get() {
        HttpPost httpPost = new HttpPost(MyContext.DJANGOSERVER+ MyContext.GETVIDEOS);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        StringBody myName = new StringBody("zxu", ContentType.TEXT_PLAIN);
        HttpEntity reqEntity = MultipartEntityBuilder.create()
                .addPart("myName", myName)
                .build();
        httpPost.setEntity(reqEntity);
        try {
            CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity resEntity = response.getEntity();
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == HttpStatus.SC_OK) {
                JSONObject jsonpObject = JSON.parseObject(EntityUtils.toString(resEntity));
                list = JSON.parseArray(jsonpObject.get("result").toString(),Video.class);
                me = JSON.parseObject(jsonpObject.get("me").toString(), User.class);
                userslist = JSON.parseArray(jsonpObject.get("users").toString(),User.class);
                Message msg = new Message();
                msg.what = 1;
                msg.obj = list;
                mHandler.sendMessage(msg);
//                System.out.println("服务器正常返回的数据: " + EntityUtils.toString(resEntity));// httpclient自带的工具类读取返回数据
//                System.out.println(resEntity.getContent());
            } else if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
//                Toast.makeText(this, "上传文件发生异常，请检查服务端异常问题", Toast.LENGTH_SHORT).show();
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

    private List<Image> getImages(String videoId){
        List<Image> imageList = new ArrayList<>();
        HttpPost httpPost = new HttpPost(MyContext.DJANGOSERVER+ MyContext.GETVIDEOIMAGES);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        StringBody myName = new StringBody(videoId, ContentType.TEXT_PLAIN);
        HttpEntity reqEntity = MultipartEntityBuilder.create()
                .addPart("videoId", myName)
                .build();
        httpPost.setEntity(reqEntity);
        try {
            CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity resEntity = response.getEntity();
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == HttpStatus.SC_OK) {
                JSONObject jsonpObject = JSON.parseObject(EntityUtils.toString(resEntity));
                imageList = JSON.parseArray(jsonpObject.get("videoImages").toString(),Image.class);
                return imageList;
//                System.out.println("服务器正常返回的数据: " + EntityUtils.toString(resEntity));// httpclient自带的工具类读取返回数据
//                System.out.println(resEntity.getContent());
            } else if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
//                Toast.makeText(this, "上传文件发生异常，请检查服务端异常问题", Toast.LENGTH_SHORT).show();
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
        return null;
    }

    private void showBottomSheetDialog(int position) {
        BottomSheetFragment fragment = BottomSheetFragment.newInstance();
        FragmentManager fragmentManager = getFragmentManager();
        fragment.show(fragmentManager,BottomSheetFragment.class.getSimpleName());
    }

    private void putGuanZhu(String targetName){
        HttpPost httpPost = new HttpPost(MyContext.DJANGOSERVER+ MyContext.PUTFOLLOW);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        StringBody tarName = null;
        StringBody myName = null;
        try {
            tarName = new StringBody(targetName, Charset.forName("UTF-8"));
            myName = new StringBody(MyContext.USER, Charset.forName("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        HttpEntity reqEntity = MultipartEntityBuilder.create()
                .addPart("myName", myName )
                .addPart("targetName", tarName)
                .build();
        httpPost.setEntity(reqEntity);
        try {
            CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity resEntity = response.getEntity();
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == HttpStatus.SC_OK) {
                JSONObject jsonpObject = JSON.parseObject(EntityUtils.toString(resEntity));
                String result = jsonpObject.get("result").toString();
                Message msg = new Message();
                msg.what = 3;
                msg.obj = result;
                mHandler.sendMessage(msg);
//                System.out.println("服务器正常返回的数据: " + EntityUtils.toString(resEntity));// httpclient自带的工具类读取返回数据
//                System.out.println(resEntity.getContent());
            } else if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
//                Toast.makeText(this, "上传文件发生异常，请检查服务端异常问题", Toast.LENGTH_SHORT).show();
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

    private void dianZan(String idVideo,String lablee) {
        HttpPost httpPost = new HttpPost(MyContext.DJANGOSERVER+ MyContext.DIANZAN);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        StringBody tarVideo = new StringBody(idVideo, ContentType.TEXT_PLAIN);
        StringBody lable = new StringBody(lablee, ContentType.TEXT_PLAIN);
        HttpEntity reqEntity = MultipartEntityBuilder.create()
                .addPart("targetVideo", tarVideo)
                .addPart("label", lable)
                .build();
        httpPost.setEntity(reqEntity);
        try {
            CloseableHttpResponse response = httpClient.execute(httpPost);
            HttpEntity resEntity = response.getEntity();
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == HttpStatus.SC_OK) {
                JSONObject jsonpObject = JSON.parseObject(EntityUtils.toString(resEntity));
                String result = jsonpObject.get("result").toString();
                Message msg = new Message();
                msg.what = 2;
                msg.obj = result;
                mHandler.sendMessage(msg);
//                System.out.println("服务器正常返回的数据: " + EntityUtils.toString(resEntity));// httpclient自带的工具类读取返回数据
//                System.out.println(resEntity.getContent());
            } else if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
//                Toast.makeText(this, "上传文件发生异常，请检查服务端异常问题", Toast.LENGTH_SHORT).show();
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
}
