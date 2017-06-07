package com.yaoyumeng.v2ex.api;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.PersistentCookieStore;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.TextHttpResponseHandler;
import com.yaoyumeng.v2ex.Application;
import com.yaoyumeng.v2ex.R;
import com.yaoyumeng.v2ex.model.MemberModel;
import com.yaoyumeng.v2ex.model.NodeModel;
import com.yaoyumeng.v2ex.model.NotificationModel;
import com.yaoyumeng.v2ex.model.PersistenceHelper;
import com.yaoyumeng.v2ex.model.ReplyModel;
import com.yaoyumeng.v2ex.model.TopicListModel;
import com.yaoyumeng.v2ex.model.TopicModel;

import org.apache.http.Header;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class V2EXManager {
    private static Application mApp = Application.getInstance();
    private static AsyncHttpClient sClient = null;
    private static final String TAG = "V2EXManager";

    private static final String HTTP_API_URL = "http://www.v2ex.com/api";
    private static final String HTTPS_API_URL = "https://www.v2ex.com/api";
    public static final String HTTP_BASE_URL = "http://www.v2ex.com";
    public static final String HTTPS_BASE_URL = "https://www.v2ex.com";

    private static final String API_LATEST = "/topics/latest.json";
    private static final String API_HOT = "/topics/hot.json";
    private static final String API_ALL_NODE = "/nodes/all.json";
    private static final String API_REPLIES = "/replies/show.json";
    private static final String API_TOPIC = "/topics/show.json";
    private static final String API_USER = "/members/show.json";

    public static final String SIGN_UP_URL = HTTPS_BASE_URL + "/signup";
    public static final String SIGN_IN_URL = HTTPS_BASE_URL + "/signin";

    public static String getBaseAPIUrl() {
        return mApp.isHttps() ? HTTPS_API_URL : HTTP_API_URL;
    }

    public static String getBaseUrl() {
        return mApp.isHttps() ? HTTPS_BASE_URL : HTTP_BASE_URL;
    }

    //获取最热话题
    public static void getHotTopics(Context cxt, boolean refresh,
                                    HttpRequestHandler<ArrayList<TopicModel>> handler) {
        getTopics(cxt, getBaseAPIUrl() + API_HOT, refresh, handler);
    }

    //获取最新话题
    public static void getLatestTopics(Context ctx, boolean refresh,
                                       HttpRequestHandler<ArrayList<TopicModel>> handler) {
        getTopics(ctx, getBaseAPIUrl() + API_LATEST, refresh, handler);
    }

    //根据节点ID获取其话题
    public static void getTopicsByNodeId(Context ctx, final int nodeId, boolean refresh,
                                         final HttpRequestHandler<ArrayList<TopicModel>> handler) {
        getTopics(ctx, getBaseAPIUrl() + API_TOPIC + "?node_id=" + nodeId, refresh, handler);
    }

    //根据节点名获取其话题
    public static void getTopicsByNodeName(Context ctx, final String nodeName, boolean refresh,
                                           final HttpRequestHandler<ArrayList<TopicModel>> handler) {
        getTopics(ctx, getBaseAPIUrl() + API_TOPIC + "?node_name=" + nodeName, refresh, handler);
    }

    //根据话题ID获取其内容
    public static void getTopicByTopicId(Context cxt, int topicId, boolean refresh,
                                         final HttpRequestHandler<ArrayList<TopicModel>> handler) {
        getTopics(cxt, getBaseAPIUrl() + API_TOPIC + "?id=" + topicId, refresh, handler);
    }

    //根据用户名获取其发表过的话题
    public static void getTopicsByUsername(Context ctx, final String username, boolean refresh,
                                           HttpRequestHandler<ArrayList<TopicModel>> handler) {
        getTopics(ctx, getBaseAPIUrl() + API_TOPIC + "?username=" + username, refresh, handler);
    }

    /**
     * 获取各类话题列表
     *
     * @param ctx
     * @param urlString URL地址
     * @param refresh   是否从缓存中读取
     * @param handler   结果处理
     */
    public static void getTopics(Context ctx, String urlString, boolean refresh,
                                 final HttpRequestHandler<ArrayList<TopicModel>> handler) {
        Uri uri = Uri.parse(urlString);
        String path = uri.getLastPathSegment();
        String param = uri.getEncodedQuery();
        String key = path;
        if (param != null)
            key += param;

        if (!refresh) {
            //尝试从缓存中加载
            ArrayList<TopicModel> topics = PersistenceHelper.loadModelList(ctx, key);
            if (topics != null && topics.size() > 0) {
                SafeHandler.onSuccess(handler, topics);
                return;
            }
        }

        new AsyncHttpClient().get(ctx, urlString,
                new WrappedJsonHttpResponseHandler<TopicModel>(ctx, TopicModel.class, key, handler));
    }

    /**
     * 获取首页分类话题列表
     *
     * @param ctx
     * @param tab     tab名称
     * @param refresh
     * @param handler
     */
    public static void getTopicsByTab(Context ctx, String tab, boolean refresh,
                                      final HttpRequestHandler<ArrayList<TopicModel>> handler) {
        getCategoryTopics(ctx, getBaseUrl() + "/?tab=" + tab, refresh, handler);
    }

    /**
     * 获取首页分类话题列表 (包括技术,创意,好玩,Apple,酷工作,交易,城市,问与答,R2)
     *
     * @param ctx
     * @param urlString
     * @param refresh
     * @param handler
     */
    public static void getCategoryTopics(final Context ctx, String urlString, boolean refresh,
                                         final HttpRequestHandler<ArrayList<TopicModel>> handler) {
        final String key = Uri.parse(urlString).getEncodedQuery();
        if (!refresh) {
            //尝试从缓存中加载
            ArrayList<TopicModel> topics = PersistenceHelper.loadModelList(ctx, key);
            if (topics != null && topics.size() > 0) {
                SafeHandler.onSuccess(handler, topics);
                return;
            }
        }

        final AsyncHttpClient client = getClient(ctx, false);
        client.addHeader("Referer", getBaseUrl());
        client.addHeader("Content-Type", "application/x-www-form-urlencoded");
        client.get(urlString, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                SafeHandler.onFailure(handler, V2EXErrorType.errorMessage(ctx, V2EXErrorType.ErrorGetTopicListFailure));
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseBody) {
                TopicListModel topics = new TopicListModel();
                topics.parse(responseBody);
                if(topics.size() > 0)
                    PersistenceHelper.saveModelList(ctx, topics, key);
                SafeHandler.onSuccess(handler, topics);
            }
        });
    }

    //获取所有节点
    public static void getAllNodes(Context ctx, boolean refresh,
                                   final HttpRequestHandler<ArrayList<NodeModel>> handler) {
        final String key = "allnodes";
        if (!refresh) {
            //尝试从缓存中加载
            ArrayList<NodeModel> nodes = PersistenceHelper.loadModelList(ctx, key);
            if (nodes != null && nodes.size() > 0) {
                SafeHandler.onSuccess(handler, nodes);
                return;
            }
        }

        new AsyncHttpClient().get(ctx, getBaseAPIUrl() + API_ALL_NODE,
                new WrappedJsonHttpResponseHandler<NodeModel>(ctx, NodeModel.class, key, handler));
    }

    //根据话题ID获取其所有回复内容
    public static void getRepliesByTopicId(Context ctx, int topicId, boolean refresh,
                                           final HttpRequestHandler<ArrayList<ReplyModel>> handler) {
        final String key = "replies_id=" + topicId;
        if (!refresh) {
            //尝试从缓存中加载
            ArrayList<ReplyModel> replies = PersistenceHelper.loadModelList(ctx, key);
            if (replies != null && replies.size() > 0) {
                SafeHandler.onSuccess(handler, replies);
                return;
            }
        }

        new AsyncHttpClient().get(ctx, getBaseAPIUrl() + API_REPLIES + "?topic_id=" + topicId,
                new WrappedJsonHttpResponseHandler<ReplyModel>(ctx, ReplyModel.class, key, handler));
    }

    //获取用户基本信息
    public static void getMemberInfoByUsername(Context ctx, String username, boolean refresh,
                                               final HttpRequestHandler<ArrayList<MemberModel>> handler) {
        final String key = "username=" + username;
        if (!refresh) {
            //尝试从缓存中加载
            ArrayList<MemberModel> member = PersistenceHelper.loadModelList(ctx, key);
            if (member != null && member.size() > 0) {
                SafeHandler.onSuccess(handler, member);
                return;
            }
        }

        new AsyncHttpClient().get(ctx, getBaseAPIUrl() + API_USER + "?username=" + username,
                new WrappedJsonHttpResponseHandler<MemberModel>(ctx, MemberModel.class, key, handler));
    }

    private static AsyncHttpClient getClient(Context context, boolean mobile) {
        if (sClient == null) {
            sClient = new AsyncHttpClient();
            sClient.setEnableRedirects(false);
            sClient.setCookieStore(new PersistentCookieStore(context));
            sClient.addHeader("Cache-Control", "max-age=0");
            sClient.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            sClient.addHeader("Accept-Charset", "utf-8, iso-8859-1, utf-16, *;q=0.7");
            sClient.addHeader("Accept-Language", "zh-CN, en-US");
            sClient.addHeader("X-Requested-With", "com.android.browser");
            sClient.addHeader("Host", "www.v2ex.com");
        }

        if (mobile)
            sClient.setUserAgent("Mozilla/5.0 (Linux; U; Android 4.2.1; en-us; M040 Build/JOP40D) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30");
        else
            sClient.setUserAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; Trident/7.0; rv:11.0) like Gecko");

        return sClient;
    }

    private static AsyncHttpClient getClient(Context context) {
        return getClient(context, true);
    }

    private static String getOnceStringFromHtmlResponseObject(String content) {
        Pattern pattern = Pattern.compile("<input type=\"hidden\" value=\"([0-9]+)\" name=\"once\" />");
        final Matcher matcher = pattern.matcher(content);
        if (matcher.find())
            return matcher.group(1);
        return null;
    }

    private static void requestOnceWithURLString(final Context cxt, String url,
                                                 final HttpRequestHandler<String> handler) {
        AsyncHttpClient client = getClient(cxt);
        client.addHeader("Referer", getBaseUrl());
        client.get(url, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                String content = new String(responseBody);
                String once = getOnceStringFromHtmlResponseObject(content);
                if (once != null)
                    SafeHandler.onSuccess(handler, once);
                else
                    SafeHandler.onFailure(handler, V2EXErrorType.errorMessage(cxt, V2EXErrorType.ErrorNoOnceAndNext));
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                SafeHandler.onFailure(handler, V2EXErrorType.errorMessage(cxt, V2EXErrorType.ErrorNoOnceAndNext));
            }
        });
    }

    private static String getProblemFromHtmlResponse(Context cxt, String response) {
        Pattern errorPattern = Pattern.compile("<div class=\"problem\">(.*)</div>");
        Matcher errorMatcher = errorPattern.matcher(response);
        String errorContent;
        if (errorMatcher.find()) {
            errorContent = errorMatcher.group(1).replaceAll("<[^>]+>", "");
        } else {
            errorContent = cxt.getString(R.string.error_unknown);
        }
        return errorContent;
    }

    /**
     * 使用用户名密码登录
     *
     * @param cxt
     * @param username 用户名
     * @param password 密码
     * @param handler  返回结果处理
     */
    public static void loginWithUsername(final Context cxt, final String username, final String password,
                                         final HttpRequestHandler<Integer> handler) {
        requestOnceWithURLString(cxt, SIGN_IN_URL, new HttpRequestHandler<String>() {
            @Override
            public void onSuccess(String data, int totalPages, int currentPage) {

            }

            @Override
            public void onSuccess(String data) {
                final String once = data;
                AsyncHttpClient client = getClient(cxt);
                client.addHeader("Origin", HTTPS_BASE_URL);
                client.addHeader("Referer", SIGN_IN_URL);
                client.addHeader("Content-Type", "application/x-www-form-urlencoded");
                RequestParams params = new RequestParams();
                params.put("next", "/");
                params.put("u", username);
                params.put("once", once);
                params.put("p", password);
                client.post(SIGN_IN_URL, params, new TextHttpResponseHandler() {
                    @Override
                    public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                        if (statusCode == 302) {
                            SafeHandler.onSuccess(handler, 200);
                        } else {
                            SafeHandler.onFailure(handler, V2EXErrorType.errorMessage(cxt, V2EXErrorType.ErrorLoginFailure));
                        }
                    }

                    @Override
                    public void onSuccess(int statusCode, Header[] headers, String responseBody) {
                        String errorContent = getProblemFromHtmlResponse(cxt, responseBody);
                        SafeHandler.onFailure(handler, errorContent);
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                SafeHandler.onFailure(handler, error);
            }
        });
    }

    private static String getUsernameFromResponse(String content) {
        Pattern userPattern = Pattern.compile("<a href=\"/member/([^\"]+)\" class=\"top\">");
        Matcher userMatcher = userPattern.matcher(content);
        if (userMatcher.find())
            return userMatcher.group(1);
        return null;
    }

    private static int getNotificationCountFromResponse(String content) {
        Pattern pattern = Pattern.compile("<a href=\"/notifications\"([^>]*)>([0-9]+) 条未读提醒</a>");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(2));
        }
        return -1;
    }


    private static ArrayList<NodeModel> getNodeModelsFromResponse(String content) {
        Pattern pattern = Pattern.compile("<a class=\"grid_item\" href=\"/go/([^\"]+)\" id=([^>]+)><div([^>]+)><img src=\"([^\"]+)([^>]+)><([^>]+)></div>([^<]+)");
        Matcher matcher = pattern.matcher(content);
        ArrayList<NodeModel> collections = new ArrayList<NodeModel>();
        while (matcher.find()) {
            NodeModel node = new NodeModel();
            node.name = matcher.group(1);
            node.title = matcher.group(7);
            node.url = matcher.group(4);
            if (node.url.startsWith("//"))
                node.url = "http:" + node.url;
            else
                node.url = HTTP_BASE_URL + node.url;
            collections.add(node);
        }
        return collections;
    }

    /**
     * 获取自己的收藏的节点
     *
     * @param context
     * @param handler
     */
    public static void getFavoriteNodes(final Context context,
                                        final HttpRequestHandler<ArrayList<NodeModel>> handler) {
        getClient(context).get(getBaseUrl() + "/my/nodes", new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                SafeHandler.onFailure(handler, V2EXErrorType.errorMessage(context, V2EXErrorType.ErrorFavNodeFailure));
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseBody) {
                ArrayList<NodeModel> collections = getNodeModelsFromResponse(responseBody);
                SafeHandler.onSuccess(handler, collections);
            }
        });
    }

    /**
     * 获取登陆用户的用户基本资料
     *
     * @param context
     * @param handler 用户基本资料的结果处理
     */
    public static void getProfile(final Context context,
                                  final HttpRequestHandler<ArrayList<MemberModel>> handler) {
        getClient(context).get(getBaseUrl(), new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                SafeHandler.onFailure(handler, V2EXErrorType.errorMessage(context, V2EXErrorType.ErrorGetProfileFailure));
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseBody) {
                String username = getUsernameFromResponse(responseBody);
                if (username != null) {
                    getMemberInfoByUsername(context, username, true, handler);
                }
            }
        });
    }

    /**
     * 获取未读提醒数目(只在>0的情况下才唤醒处理事件)
     *
     * @param cxt
     * @param handler
     */
    public static void getNotificationCount(final Context cxt, final HttpRequestHandler<Integer> handler) {
        getClient(cxt, false).get(getBaseUrl(), new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                SafeHandler.onFailure(handler, V2EXErrorType.errorMessage(cxt, V2EXErrorType.ErrorGetNotificationFailure));
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseBody) {
                int count = getNotificationCountFromResponse(responseBody);
                if (count > 0) {
                    SafeHandler.onSuccess(handler, count);
                }
            }
        });
    }

    /**
     * 回复话题
     *
     * @param cxt
     * @param topicId 话题Id
     * @param content 评论内容
     * @param handler 结果处理事件
     */
    public static void replyCreateWithTopicId(final Context cxt, final int topicId, final String content,
                                              final HttpRequestHandler<Integer> handler) {
        final String urlString = getBaseUrl() + "/t/" + topicId;
        requestOnceWithURLString(cxt, urlString, new HttpRequestHandler<String>() {
            @Override
            public void onSuccess(String data, int totalPages, int currentPage) {
            }

            @Override
            public void onSuccess(String data) {
                String once = data;
                AsyncHttpClient client = getClient(cxt);
                client.addHeader("Origin", getBaseUrl());
                client.addHeader("Referer", urlString);
                client.addHeader("Content-Type", "application/x-www-form-urlencoded");
                RequestParams params = new RequestParams();
                params.put("content", content);
                params.put("once", once);
                client.post(urlString, params, new AsyncHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                        String errorContent = getProblemFromHtmlResponse(cxt, new String(responseBody));
                        SafeHandler.onFailure(handler, errorContent);
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                        if (statusCode == 302) {
                            SafeHandler.onSuccess(handler, 200);
                        } else {
                            SafeHandler.onFailure(handler, V2EXErrorType.errorMessage(cxt, V2EXErrorType.ErrorCommentFailure));
                        }
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                SafeHandler.onFailure(handler, error);
            }
        });
    }

    /**
     * 创建新的话题
     *
     * @param cxt
     * @param nodeName 节点名称
     * @param title    话题的主标题
     * @param content  话题的正文内容
     * @param handler  结果处理事件
     */
    public static void topicCreateWithNodeName(final Context cxt, final String nodeName,
                                               final String title, final String content,
                                               final HttpRequestHandler<Integer> handler) {

        final String urlString = getBaseUrl() + "/new/" + nodeName;
        requestOnceWithURLString(cxt, urlString, new HttpRequestHandler<String>() {
            @Override
            public void onSuccess(String data, int totalPages, int currentPage) {
            }

            @Override
            public void onSuccess(String once) {
                AsyncHttpClient client = getClient(cxt);
                client.addHeader("Origin", getBaseUrl());
                client.addHeader("Referer", urlString);
                client.addHeader("Content-Type", "application/x-www-form-urlencoded");
                RequestParams params = new RequestParams();
                params.put("once", once);
                params.put("content", content);
                params.put("title", title);
                client.post(urlString, params, new AsyncHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                        String errorContent = getProblemFromHtmlResponse(cxt, new String(responseBody));
                        SafeHandler.onFailure(handler, errorContent);
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                        if (statusCode == 302) {
                            SafeHandler.onSuccess(handler, 200);
                        } else {
                            SafeHandler.onFailure(handler, V2EXErrorType.errorMessage(cxt, V2EXErrorType.ErrorCreateNewFailure));
                        }
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                SafeHandler.onFailure(handler, error);
            }
        });
    }

    private static String getFavUrlStringFromResponse(String response) {
        Pattern pattern = Pattern.compile("<a href=\"(.*)\">加入收藏</a>");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find())
            return matcher.group(1);

        pattern = Pattern.compile("<a href=\"(.*)\">取消收藏</a>");
        matcher = pattern.matcher(response);
        if (matcher.find())
            return matcher.group(1);

        return "";
    }

    /**
     * 某个节点加入收藏或者取消收藏
     *
     * @param context
     * @param nodeName 节点名称
     * @param handler  结果处理事件
     */
    public static void favNodeWithNodeName(final Context context, String nodeName,
                                           final HttpRequestHandler<Integer> handler) {
        String urlString = getBaseUrl() + "/go/" + nodeName;
        final AsyncHttpClient client = getClient(context, false);
        client.addHeader("Referer", getBaseUrl());
        client.addHeader("Content-Type", "application/x-www-form-urlencoded");
        client.get(urlString, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                SafeHandler.onFailure(handler, V2EXErrorType.errorMessage(context, V2EXErrorType.ErrorFavNodeFailure));
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseBody) {
                String favUrl = getFavUrlStringFromResponse(responseBody);
                if (favUrl.isEmpty()) {
                    SafeHandler.onFailure(handler, context.getString(R.string.error_unknown));
                    return;
                }

                final boolean fav = !favUrl.contains("unfavorite");
                client.get(context, getBaseUrl() + favUrl, new TextHttpResponseHandler() {
                    @Override
                    public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                        if (statusCode == 302) {
                            SafeHandler.onSuccess(handler, fav ? 200 : 201);
                        } else {
                            SafeHandler.onFailure(handler, V2EXErrorType.errorMessage(context, V2EXErrorType.ErrorFavNodeFailure));
                        }
                    }

                    @Override
                    public void onSuccess(int statusCode, Header[] headers, String responseString) {
                        SafeHandler.onFailure(handler, getProblemFromHtmlResponse(context, responseString));
                    }
                });
            }
        });
    }

    /**
     * 将某个话题加入收藏或者取消收藏
     *
     * @param context
     * @param topicId 话题ID号
     * @param handler 结果处理
     */
    public static void favTopicWithTopicId(final Context context, int topicId,
                                           final HttpRequestHandler<Integer> handler) {
        String urlString = getBaseUrl() + "/t/" + topicId;
        final AsyncHttpClient client = getClient(context, false);
        client.addHeader("Referer", getBaseUrl());
        client.addHeader("Content-Type", "application/x-www-form-urlencoded");
        client.get(urlString, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                SafeHandler.onFailure(handler, V2EXErrorType.errorMessage(context, V2EXErrorType.ErrorFavTopicFailure));
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseBody) {
                String favUrl = getFavUrlStringFromResponse(responseBody);
                Log.i(TAG, favUrl);
                if (favUrl.isEmpty()) {
                    SafeHandler.onFailure(handler, context.getString(R.string.error_unknown));
                    return;
                }

                favUrl = favUrl.replace("\" class=\"tb", "");
                final boolean fav = !favUrl.contains("unfavorite");
                client.get(context, getBaseUrl() + favUrl, new TextHttpResponseHandler() {
                    @Override
                    public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                        if (statusCode == 302) {
                            SafeHandler.onSuccess(handler, fav ? 200 : 201);
                        } else {
                            SafeHandler.onFailure(handler, V2EXErrorType.errorMessage(context, V2EXErrorType.ErrorFavTopicFailure));
                        }
                    }

                    @Override
                    public void onSuccess(int statusCode, Header[] headers, String responseString) {
                        SafeHandler.onFailure(handler, getProblemFromHtmlResponse(context, responseString));
                    }
                });
            }
        });
    }

    /**
     * 获取用户的未读提醒消息
     *
     * @param context
     * @param handler
     */
    public static void getNotifications(final Context context, int page,
                                        final HttpRequestHandler<ArrayList<NotificationModel>> handler) {
        String urlString = getBaseUrl() + "/notifications";
        if (page > 1) urlString += "?p=" + page;
        final AsyncHttpClient client = getClient(context, false);
        client.addHeader("Referer", getBaseUrl());
        client.addHeader("Content-Type", "application/x-www-form-urlencoded");
        client.get(urlString, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                SafeHandler.onFailure(handler, V2EXErrorType.errorMessage(context, V2EXErrorType.ErrorGetNotificationFailure));
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseBody) {
                ArrayList<NotificationModel> notifications = new ArrayList<NotificationModel>();
                Document doc = Jsoup.parse(responseBody);
                Element body = doc.body();
                Elements elements = body.getElementsByAttributeValue("class", "cell");
                for (Element el : elements) {
                    NotificationModel notification = new NotificationModel();
                    if (notification.parse(el))
                        notifications.add(notification);
                }

                elements = body.getElementsByAttributeValue("class", "inner");
                int total = 1, current = 1;
                for (Element el : elements) {
                    Elements tds = el.getElementsByTag("td");
                    if (tds.size() != 3) continue;

                    String pageString = el.getElementsByAttributeValue("align", "center").text();
                    String[] arrayString = pageString.split("/");
                    if (arrayString.length != 2) continue;

                    try {
                        total = Integer.parseInt(arrayString[1]);
                        current = Integer.parseInt(arrayString[0]);
                    } catch (Exception e) {
                    }
                    break;
                }
                SafeHandler.onSuccess(handler, notifications, total, current);
            }
        });
    }


    /**
     * 退出登录
     *
     * @param context
     */
    public static void logout(Context context) {
        PersistentCookieStore cookieStore = new PersistentCookieStore(context);
        cookieStore.clear();
    }


}
