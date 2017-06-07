package com.yaoyumeng.v2ex.ui.fragment;

import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.yaoyumeng.v2ex.R;
import com.yaoyumeng.v2ex.api.HttpRequestHandler;
import com.yaoyumeng.v2ex.api.V2EXManager;
import com.yaoyumeng.v2ex.model.MemberModel;
import com.yaoyumeng.v2ex.model.NotificationModel;
import com.yaoyumeng.v2ex.ui.adapter.HeaderViewRecyclerAdapter;
import com.yaoyumeng.v2ex.ui.adapter.NotificationsAdapter;
import com.yaoyumeng.v2ex.ui.widget.FootUpdate;
import com.yaoyumeng.v2ex.utils.MessageUtils;

import java.util.ArrayList;

/**
 * 显示单个节点下的话题或最新/最热话题类
 */
public class NotificationFragment extends BaseFragment implements HttpRequestHandler<ArrayList<NotificationModel>>, NotificationsAdapter.OnScrollToLastListener {
    public static final String TAG = "NotificationFragment";
    RecyclerView mRecyclerView;
    HeaderViewRecyclerAdapter mHeaderAdapter;
    NotificationsAdapter mAdapter;
    SwipeRefreshLayout mSwipeLayout;
    TextView mEmptyText;
    boolean mIsLoading;
    RecyclerView.LayoutManager mLayoutManager;
    boolean mNoMore = false;
    int mPage = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new NotificationsAdapter(getActivity(), this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_notification, container, false);

        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.notification_listview);

        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mHeaderAdapter = new HeaderViewRecyclerAdapter(mAdapter);
        mRecyclerView.setAdapter(mHeaderAdapter);

        mEmptyText = (TextView) rootView.findViewById(R.id.txt_fragment_notification_empty);

        mFootUpdate.init(mHeaderAdapter, LayoutInflater.from(getActivity()), new FootUpdate.LoadMore() {
            @Override
            public void loadMore() {
                requestMoreNotifications();
            }
        });

        mSwipeLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipe_container);
        mSwipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mPage = 1;
                requestNotifications();
            }
        });

        mSwipeLayout.setColorScheme(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
        mSwipeLayout.setProgressViewOffset(false, 0,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics()));

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (!mIsLogin) {
            mSwipeLayout.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.GONE);
            mEmptyText.setVisibility(View.VISIBLE);
        } else {
            mEmptyText.setVisibility(View.GONE);
            mSwipeLayout.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.VISIBLE);

            mSwipeLayout.setRefreshing(true);
            requestNotifications();
        }
    }

    @Override
    public void onLogin(MemberModel member) {
        super.onLogin(member);

        //登录,刷新信息
        mEmptyText.setVisibility(View.GONE);
        mSwipeLayout.setVisibility(View.VISIBLE);
        mRecyclerView.setVisibility(View.VISIBLE);

        mSwipeLayout.setRefreshing(true);
        requestNotifications();
    }

    @Override
    public void onSuccess(ArrayList<NotificationModel> data) {

    }

    @Override
    public void onSuccess(ArrayList<NotificationModel> data, int totalPages, int currentPage) {
        mSwipeLayout.setRefreshing(false);
        mIsLoading = false;
        if (data.size() == 0) {
            MessageUtils.showMiddleToast(getActivity(), getString(R.string.notification_message));
            return;
        }

        mAdapter.update(data, currentPage != 1);

        mPage = currentPage;
        mNoMore = currentPage == totalPages;
        if (mNoMore) {
            mFootUpdate.dismiss();
        } else {
            mFootUpdate.showLoading();
        }
    }

    @Override
    public void onFailure(String error) {
        mSwipeLayout.setRefreshing(false);
        mIsLoading = false;
        MessageUtils.showErrorMessage(getActivity(), error);
        if (mAdapter.getItemCount() > 0) {
            mFootUpdate.showFail();
        } else {
            mFootUpdate.dismiss();
        }
    }

    @Override
    public void onLoadMore() {
        if (!mNoMore) requestMoreNotifications();
    }

    private void requestNotifications() {
        if (mIsLoading)
            return;

        mIsLoading = true;
        V2EXManager.getNotifications(getActivity(), 1, this);
    }

    private void requestMoreNotifications() {
        mIsLoading = true;
        V2EXManager.getNotifications(getActivity(), mPage + 1, this);
    }
}
