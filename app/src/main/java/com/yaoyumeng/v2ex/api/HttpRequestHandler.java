package com.yaoyumeng.v2ex.api;

/**
 * Created by yw on 2015/5/22.
 */
public interface HttpRequestHandler<E> {
    void onSuccess(E data);

    void onSuccess(E data, int totalPages, int currentPage);

    void onFailure(String error);
}
