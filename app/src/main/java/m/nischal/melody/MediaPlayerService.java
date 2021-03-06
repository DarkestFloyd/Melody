package m.nischal.melody;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;

import java.io.IOException;
import java.util.List;

import m.nischal.melody.Helper.NotificationHelper;
import m.nischal.melody.Util.BusEvents;
import m.nischal.melody.Util.RxBus;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

import static m.nischal.melody.Helper.GeneralHelpers.DebugHelper.LumberJack;

public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener {

    public static final String RX_BUS_PLAYER_STATE = "m.nischal.melody.PLAYER_STATE_CHANGED";
    public static final String BROADCAST_INTENT = "m.nischal.melody.MediaPlayerService.BROADCAST_INTENT";
    public static final String BROADCAST_TYPE = "m.nischal.melody.MediaPlayerService.BROADCAST_TYPE";
    public static final String NOTIFICATION_ACTION_NEXT = "m.nischal.melody.NOTIFICATION_ACTION_NEXT";
    public static final String NOTIFICATION_ACTION_PREV = "m.nischal.melody.NOTIFICATION_ACTION_PREV";
    public static final String NOTIFICATION_PLAY_COMPLETED = "m.nischal.melody.SONG_COMPLETED";
    public static final int STATE_PLAYING = 0;
    public static final int STATE_PAUSED = 1;
    public static final int STATE_COMPLETED = 2;

    private final static MediaPlayer mPlayer = new MediaPlayer();
    private final Intent broadcastIntent = new Intent(BROADCAST_INTENT);

    private RxBus rxBus;
    private NotificationHelper notificationHelper;
    private CompositeSubscription subscriptions = new CompositeSubscription();

    private final IMelodyPlayer.Stub mBinder = new IMelodyPlayer.Stub() {

        @Override
        public void setDataSource(List<String> details, Bitmap bitmap, int color) throws RemoteException {

            startForeground(1, notificationHelper.buildNormal(details, bitmap, color));


            /*LumberJack.d("path: " + details.get(0));
            LumberJack.d("title: " + details.get(1));
            LumberJack.d("album: " + details.get(2));
            LumberJack.d("artist: " + details.get(3));*/
            setSourceForPlayer(details);
        }

        @Override
        public void play() throws RemoteException {
            changePlayerState();
        }

        @Override
        public void killService() throws RemoteException {

            if (mPlayer.isPlaying()) return;

            mPlayer.reset();
            stopSelf();
        }

        @Override
        public boolean isPlaying() throws RemoteException {
            return mPlayer.isPlaying();
        }
    };

    private void setSourceForPlayer(List<String> details) {

        mPlayer.reset();

        Subscription sc = Observable.just(details.get(0))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getPathObserver());
        subscriptions.add(sc);
    }

    private Observer<String> getPathObserver() {
        return new Observer<String>() {
            @Override
            public void onCompleted() {
                LumberJack.d("onComplete called/in service");
                try {
                    mPlayer.prepare();
                } catch (IOException e) {
                    LumberJack.e(e);
                } finally {
                    mPlayer.start();
                    playerStateChanged(STATE_PLAYING);
                }
            }

            @Override
            public void onError(Throwable e) {
                LumberJack.e(e);
            }

            @Override
            public void onNext(String s) {
                LumberJack.d("onNext called/in service with path: " + s);
                try {
                    mPlayer.setDataSource(getApplicationContext(), Uri.parse(s));
                } catch (Exception e) {
                    LumberJack.e(e);
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();

        if (action != null) {
            actionPerformed(action);
        } else {
            mPlayer.setOnCompletionListener(this);
            rxBus = RxBus.getBus();
            notificationHelper = NotificationHelper.getInstance(getApplicationContext());
            subscribeToBus();
        }
        return START_STICKY;
    }

    private void subscribeToBus() {
        Subscription sc = rxBus
                .toObservable()
                .subscribe(new Observer<BusEvents>() {
                    @Override
                    public void onCompleted() {
                        LumberJack.d("onComplete called/Service#subscribe");
                    }

                    @Override
                    public void onError(Throwable e) {
                        LumberJack.e("onError called/Service#subscribe");
                        LumberJack.e(e);
                    }

                    @Override
                    public void onNext(BusEvents busEvents) {
                        LumberJack.d("onNext called/Service#subscribe");
                        LumberJack.d("boolean: ", busEvents instanceof BusEvents.NewSongAddedToQueue && !mPlayer.isPlaying());
//                        if (busEvents instanceof BusEvents.NewSongAddedToQueue && !mPlayer.isPlaying())
//                            setSourceForPlayer(nextSongPath());
                    }
                });
        subscriptions.add(sc);
    }

    private void changePlayerState() {
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
            stopForeground(false);
            rxBus.putValue(RX_BUS_PLAYER_STATE, STATE_PAUSED);
        } else {
            mPlayer.start();
            startForeground(1, notificationHelper.getNotification());
            rxBus.putValue(RX_BUS_PLAYER_STATE, STATE_PLAYING);
        }
        playerStateChanged(-1);
    }

    private void playerStateChanged(int newState) {
        if (newState != -1)
            rxBus.putValue(RX_BUS_PLAYER_STATE, newState);
        rxBus.publish(new BusEvents.MediaStateChanged());
    }

    private void actionPerformed(String action) {
        LumberJack.d("action type: " + action);
        if (action.equals(NotificationHelper.ACTION_PLAY_PAUSE))
            changePlayerState();
        else {
            LumberJack.d("else part");
            if (action.equals(NotificationHelper.ACTION_NEXT))
                broadcastIntent.putExtra(BROADCAST_TYPE, NOTIFICATION_ACTION_NEXT);
            else if (action.equals(NotificationHelper.ACTION_PREV))
                broadcastIntent.putExtra(BROADCAST_TYPE, NOTIFICATION_ACTION_PREV);
            sendBroadcast(broadcastIntent);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPlayer.release();
        subscriptions.unsubscribe();
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        LumberJack.d("music completed!");
        stopForeground(false);
        playerStateChanged(STATE_COMPLETED);
        broadcastIntent.putExtra(BROADCAST_TYPE, NOTIFICATION_PLAY_COMPLETED);
        sendBroadcast(broadcastIntent);
    }
}
