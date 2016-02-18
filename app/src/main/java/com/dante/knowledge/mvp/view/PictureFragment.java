package com.dante.knowledge.mvp.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.view.View;

import com.dante.knowledge.MainActivity;
import com.dante.knowledge.R;
import com.dante.knowledge.mvp.interf.OnLoadDataListener;
import com.dante.knowledge.mvp.model.Image;
import com.dante.knowledge.mvp.other.PictureAdapter;
import com.dante.knowledge.mvp.presenter.PictureFetchService;
import com.dante.knowledge.mvp.presenter.PictureParser;
import com.dante.knowledge.net.API;
import com.dante.knowledge.net.Constants;
import com.dante.knowledge.net.DB;
import com.dante.knowledge.net.Net;
import com.dante.knowledge.utils.Shared;
import com.dante.knowledge.utils.UiUtils;
import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.callback.StringCallback;

import io.realm.RealmResults;
import okhttp3.Call;

/**
 * Gank and DB beauty fragment.
 */
public class PictureFragment extends RecyclerFragment implements OnLoadDataListener<Image> {
    public static final int TYPE_GANK = 0;
    public static final int TYPE_DB_BREAST = 1;
    public static final int TYPE_DB_BUTT = 2;
    public static final int TYPE_DB_SILK = 3;
    public static final int TYPE_DB_LEG = 4;
    public static final int TYPE_DB_RANK = 5;

    public static final int TYPE_H_ASIA = 10;
    public static final int TYPE_H_SELFIE = 11;
    public static final int TYPE_H_SILK = 12;
    public static final int TYPE_H_STREET = 13;

    private static final int LOAD_COUNT_LARGE = 15;
    private static int LOAD_COUNT = 10;
    private static int PRELOAD_COUNT = 10;

    private String url;
    private int page = 1;
    private int type;
    private StaggeredGridLayoutManager layoutManager;
    private PictureAdapter adapter;
    private RealmResults<Image> images;
    private long GET_DURATION = 3000;
    private PictureParser parser;
    private UpdateReceiver updateReceiver;
    private LocalBroadcastManager localBroadcastManager;
    private FragmentActivity context;

    @Override
    public void onDestroyView() {
        OkHttpUtils.getInstance().cancelTag(API.TAG_PICTURE);
        if (null != parser) {
            parser.cancelTask();
        }
        localBroadcastManager.unregisterReceiver(updateReceiver);
        Shared.save(Constants.POSITION + type, layoutManager.findFirstCompletelyVisibleItemPositions(new int[layoutManager.getSpanCount()])[0]);
        Shared.save(Constants.PAGE, page);
        super.onDestroyView();
    }

    public static PictureFragment newInstance(int type) {
        Bundle args = new Bundle();
        args.putInt(Constants.TYPE, type);
        PictureFragment fragment = new PictureFragment();
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    protected void initLayoutId() {
        layoutId = R.layout.fragment_recycler;
    }

    @Override
    protected void initViews() {
        super.initViews();
        updateReceiver = new UpdateReceiver();
        context = getActivity();
        localBroadcastManager = LocalBroadcastManager.getInstance(context);
        localBroadcastManager.registerReceiver(updateReceiver, new IntentFilter(PictureFetchService.ACTION_FETCH));
        layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new PictureAdapter(context) {
            @Override
            protected void onItemClick(View v, int position) {
                startViewerActivity(v, position);
            }
        };
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE){
                    onListScrolled();
                }
            }
        });
        type = getArguments().getInt(Constants.TYPE);
        page = Shared.getInt(Constants.PAGE);
    }

    private void startViewerActivity(View view, int position) {
        Intent intent = new Intent(context, DetailActivity.class);
        intent.putExtra(Constants.MENU_TYPE, MenuTabFragment.MENU_PIC);
        intent.putExtra(Constants.TYPE,type);
        intent.putExtra(Constants.POSITION, position);

        ActivityOptionsCompat options = ActivityOptionsCompat
                .makeSceneTransitionAnimation(context, view, adapter.get(position).getUrl());
        ActivityCompat.startActivity(context, intent, options.toBundle());


    }

    private void onListScrolled() {
        int lastPosition = layoutManager.findLastVisibleItemPositions(
                new int[layoutManager.getSpanCount()])[1];

        if (isFirst) {
            if (lastPosition > images.size() / 3) {
                fetch();
                isFirst = false;
            }

        } else if (lastPosition > layoutManager.getItemCount() - PRELOAD_COUNT) {
            Log.i("test", layoutManager.getItemCount() + ">>>> last:" + lastPosition);

            PRELOAD_COUNT++;
            fetch();
        }


    }

    private void fetch() {
        changeProgress(true);
        initUrl();
        getData();
    }

    private void getData() {
        final long lastGetTime = System.currentTimeMillis();
        StringCallback callback = new StringCallback() {
            @Override
            public void onError(Call call, Exception e) {
                if (System.currentTimeMillis() - lastGetTime < GET_DURATION) {
                    Net.get(url, this, API.TAG_PICTURE);
                    return;
                }
                onFailure("load failed", e);
            }

            @Override
            public void onResponse(String response) {
                PictureFetchService.startActionFetch(getActivity(), type, response);

            }
        };

        Net.get(url, callback, API.TAG_PICTURE);
    }

    private void initUrl() {
        switch (type) {
            case TYPE_DB_BREAST:
                url = API.DB_BREAST + page;
                break;
            case TYPE_DB_BUTT:
                url = API.DB_BUTT + page;
                break;
            case TYPE_DB_LEG:
                url = API.DB_LEG + page;
                break;
            case TYPE_DB_SILK:
                url = API.DB_SILK + page;
                break;
            case TYPE_DB_RANK:
                url = API.DB_RANK + page;
                break;
            default://type = 0, 代表GANK
                if (!isFirst) {
                    //if not first load, we load more (coz user has images to see)
                    LOAD_COUNT = LOAD_COUNT_LARGE;
                }
                url = API.GANK + LOAD_COUNT + "/" + page;
                break;
        }
    }

    @Override
    protected void initData() {
        images = DB.getImages(type);
        if (images.isEmpty()) {
            swipeRefresh.post(new Runnable() {
                @Override
                public void run() {
                    changeProgress(true);
                }
            });
            fetch();
            return;
        }
        recyclerView.scrollToPosition(Shared.getInt(Constants.POSITION + type));
        adapter.addAll(images);
        adapter.notifyDataSetChanged();
    }


    @Override
    public void onRefresh() {
        fetch();
    }

    @Override
    public void onDataSuccess(Image data) {
        changeProgress(false);
        adapter.replaceWith(images);
        page++;
    }

    @Override
    public void onFailure(String msg, Exception e) {
        changeProgress(false);
        adapter.replaceWith(images);
        if (isLive()) {
            UiUtils.showSnackLong(((MainActivity) getActivity()).getDrawerLayout(), R.string.load_fail);
        }
    }

    private class UpdateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra(PictureFetchService.EXTRA_FETCHED_RESULT, false)) {
                onDataSuccess(null);
            }
        }
    }

}
