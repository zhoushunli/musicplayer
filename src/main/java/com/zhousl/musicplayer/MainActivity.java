package com.zhousl.musicplayer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.zhousl.musicplayer.adapter.HomePageAdapter;
import com.zhousl.musicplayer.constants.Action;
import com.zhousl.musicplayer.frag.LocalFrag;
import com.zhousl.musicplayer.frag.NetFrag;
import com.zhousl.musicplayer.service.PlayService;
import com.zhousl.musicplayer.util.Preferences;
import com.zhousl.musicplayer.util.UIUtil;
import com.zhousl.musicplayer.view.PlayingActivity;

import java.net.URI;
import java.util.ArrayList;

public class MainActivity extends BaseActivity implements View.OnClickListener, ViewPager.OnPageChangeListener {

    //用于和Playservice进行通信的binder对象
//    private PlayService.MyBinder mBinder;
    private MusicPlayer mPlayer;
    private Intent mService;
    private MyConn mServiceConnection;
    private TextView localTab;
    private TextView remoteTag;
    private ViewPager pagerContent;
    private static final float SCALE_FACTOR = 1.2f;
    private static final long DEFAULT_ANIMATE_DURATION = 200;
    //当前正在播放的音乐
    private Music mMusic;
    private PlayingChangeReceiver mReceiver;
    private LocalFrag mLocalFrag;
    private NetFrag mNetFrag;
    private View mPlay;
    private View mNext;
    private View mLast;
    private TextView mTitle;
    private TextView mArtist;
    private View mController;
    private View more;
    private ImageView cover;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mReceiver = new PlayingChangeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Action.PLAY_NEXT);
        filter.addAction(Action.REMOTE_STOP);
        registerReceiver(mReceiver, filter);
        initView();
        initService();
    }

    @Override
    protected int getRes() {
        return R.layout.activity_main;
    }

    private void initView() {
        localTab = (TextView) findViewById(R.id.local);
        remoteTag = (TextView) findViewById(R.id.remote);
        pagerContent = (ViewPager) findViewById(R.id.pager_content);
        mTitle = (TextView) findViewById(R.id.music_title);
        mArtist = (TextView) findViewById(R.id.artist);
        mController = findViewById(R.id.controller);
        cover = (ImageView) findViewById(R.id.music_cover);
        more = findViewById(R.id.more);
        more.setOnClickListener(this);
        mLast = findViewById(R.id.last);
        mNext = findViewById(R.id.next);
        mPlay = findViewById(R.id.play);
        localTab.setOnClickListener(this);
        remoteTag.setOnClickListener(this);
        mLast.setOnClickListener(this);
        mNext.setOnClickListener(this);
        mPlay.setOnClickListener(this);
        mController.setOnClickListener(this);
        pagerContent.setAdapter(new HomePageAdapter(getSupportFragmentManager(), prepareFrag()));
        pagerContent.addOnPageChangeListener(this);
        localTab.setSelected(true);
        localTab.setScaleX(SCALE_FACTOR);
        localTab.setScaleY(SCALE_FACTOR);
    }

    private ArrayList<Fragment> prepareFrag() {
        ArrayList<Fragment> fragments = new ArrayList<>();
        mLocalFrag = new LocalFrag();
        fragments.add(mLocalFrag);
        mNetFrag = new NetFrag();
        fragments.add(mNetFrag);
        return fragments;
    }

    private void initService() {
        if (mService == null) {
            mService = new Intent(this, PlayService.class);
            startService(mService);
        }
        mServiceConnection = new MyConn();
        bindService(mService, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServiceConnection != null) {
            unbindService(mServiceConnection);
        }
        if (mReceiver != null)
            unregisterReceiver(mReceiver);
    }

    @Override
    public void onClick(View v) {
        if (v == more) {
            showMore();
            return;
        }
        if (v == mController) {
            startActivity(new Intent(this, PlayingActivity.class));
            return;
        }
        if (v == mLast) {
            doPlayPrevious();
        } else if (v == mNext) {
            doPlayNext();
        } else if (v == mPlay) {
            doPlayOrPause();
        }
        mLocalFrag.notifyPlayingChanged();
    }

    private void showMore() {
        PopupWindow moreWindow = new PopupWindow(this);
        moreWindow.setWidth(UIUtil.getScreenDimen(this).first);
        View rootView = View.inflate(this, R.layout.more_layout, null);
        moreWindow.setContentView(rootView);
        rootView.findViewById(R.id.equalizer).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, EqualizerActivity.class));
            }
        });
    }

    private void doPlayOrPause() {
        Music music = mPlayer.getMusic();
        if (music == null) {
            doPlayNew(0);
            return;
        }
        if (music.getState() == Music.MusicState.STATE_PAUSE) {
            doMusicResume();
        } else if (music.getState() == Music.MusicState.STATE_PLAYING) {
            doMusicPause();
        } else {
            doPlayLatestStoppedMusic();
        }
    }

    private void doPlayLatestStoppedMusic() {
        mPlayer.play();
        refreshControllerStatus(getMusic());
    }

    public Music getMusic() {
        return mPlayer.getMusic();
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        if (positionOffset == 0)
            return;
        localTab.setScaleX((SCALE_FACTOR - 1) * (1 - positionOffset) + 1);
        localTab.setScaleY((SCALE_FACTOR - 1) * (1 - positionOffset) + 1);
        remoteTag.setScaleX((SCALE_FACTOR - 1) * positionOffset + 1);
        remoteTag.setScaleY((SCALE_FACTOR - 1) * positionOffset + 1);
    }

    @Override
    public void onPageSelected(int position) {
        if (position == 0) {
            localTab.setSelected(true);
            remoteTag.setSelected(false);
        } else {
            localTab.setSelected(false);
            remoteTag.setSelected(true);
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    private void doScale(View target, float factor) {
        ScaleAnimation sa = new ScaleAnimation(1, factor, 1, factor);
        sa.setDuration(DEFAULT_ANIMATE_DURATION);
        sa.setFillAfter(false);
        target.startAnimation(sa);
    }

    class MyConn implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service == null)
                return;
            if (service instanceof PlayService.MyBinder) {
                MainActivity.this.mPlayer = MusicPlayer.getPlayer();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    }

    public void setMusic(Music music) {
        mPlayer.setMusic(music);
    }

    public void setMusicList(ArrayList<Music> musicList) {
        mPlayer.setMusicList(musicList);
    }

    public void doPlayNew(int index) {
        mPlayer.playIndex(index);
        refreshControllerStatus(mPlayer.getMusic());
    }

    public void doPlayNext() {
        mPlayer.playNext();
        refreshControllerStatus(mPlayer.getMusic());
    }

    public void doPlayPrevious() {
        mPlayer.playPrevious();
        refreshControllerStatus(mPlayer.getMusic());
    }

    public void doMusicPause() {
        mPlayer.pause();
        refreshControllerStatus(mPlayer.getMusic());
    }

    public void doMusicResume() {
        mPlayer.resume();
        refreshControllerStatus(mPlayer.getMusic());
    }

    @Override
    public void onBackPressed() {
        leapToLauncher();
    }

    private void leapToLauncher() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        startActivity(intent);
    }

    public class PlayingChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null)
                return;
            if (action.equals(Action.PLAY_NEXT)) {
                mLocalFrag.notifyPlayingChanged();
                refreshControllerStatus(getMusic());
            } else if (action.equals(Action.REMOTE_STOP)) {
                mLocalFrag.notifyStop();
            }
        }
    }

    public void refreshControllerStatus(Music music) {
        if (music == null) {
            return;
        }
        mTitle.setText(music.getName());
        mArtist.setText(music.getArtist());
        mPlay.setSelected(music.getState() == Music.MusicState.STATE_PLAYING);
        if (music.getAlbum() != null) {
            Log.i("album--", music.getAlbum());
        }
    }
}
