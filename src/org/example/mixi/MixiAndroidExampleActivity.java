
package org.example.mixi;

import java.util.ArrayList;
import java.util.HashMap;

import jp.mixi.android.sdk.CallbackListener;
import jp.mixi.android.sdk.Config;
import jp.mixi.android.sdk.ErrorInfo;
import jp.mixi.android.sdk.MixiContainer;
import jp.mixi.android.sdk.MixiContainerFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

/**
 * mixi Graph API SDK for Android を用いて、ログイン、つぶやきの一覧と投稿を行うサンプルです。
 * 
 * @author yuki.fujisaki
 */
public class MixiAndroidExampleActivity extends Activity {
    private static final String TAG = "MixiAndroidExampleActivity";

    /** Consumer Key (登録したアプリケーションの Consumer Key を設定) */
    private static final String CLIENT_ID = "";

    /** このアプリケーションで認可を求めるパーミッション */
    private static final String[] PERMISSIONS = new String[] {
            "r_profile", // プロフィール情報の取得
            "r_voice", // つぶやきの取得
            "w_voice" // つぶやきの投稿
    };

    /** mixi API SDK で使用する onActivityResult のコールバック識別子(任意の値) */
    private static final int REQUEST_MIXI_API = 3941;

    /** mixi API SDK のインタフェース */
    private MixiContainer mContainer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);

        initMixiContainer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeMixiContainer();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // callback を MixiContainer にも伝搬
        mContainer.authorizeCallback(requestCode, resultCode, data);
    }

    /**
     * main.xml の "ログイン" ボタン押下に対応するコールバック。
     * 
     * @param v ログインボタンの View インスタンス
     */
    public void onLoginClick(View v) {

        // 既に認可済み
        if (mContainer.isAuthorized()) {
            onInitialized();
            return;
        }

        // 認可処理を開始
        mContainer.authorize(this, PERMISSIONS, REQUEST_MIXI_API, new CallbackListener() {
            @Override
            public void onComplete(Bundle values) {
                onInitialized();
            }

            @Override
            public void onFatal(ErrorInfo e) {
                showToast("エラーが発生しました");
            }

            @Override
            public void onError(ErrorInfo e) {
                showToast("エラーが発生しました");
            }

            @Override
            public void onCancel() {
                showToast("中止しました");
            }
        });
    }

    /**
     * mixi API SDK を初期化する。終了 (onDestroy) 前に {@link #closeMixiContainer()}
     * で解放が必要。
     */
    private void initMixiContainer() {
        Config config = new Config();
        config.clientId = CLIENT_ID;
        config.selector = Config.GRAPH_API;
        mContainer = MixiContainerFactory.getContainer(config);
        mContainer.init(this);
    }

    /**
     * mixi API SDK の終了処理を行い、各種リソースを解放する。
     */
    private void closeMixiContainer() {
        mContainer.close(this);
    }

    /**
     * 初期化が完了し、ログイン済みの状態となり、 API コールが可能になった際に呼び出されるコールバック。
     */
    protected void onInitialized() {
        // ログインボタンを無効化
        findViewById(R.id.loginButton).setEnabled(false);

        // 自分のプロフィールを取得する
        loadSelfProfile();

        // ボイスを読み込む
        loadVoice(0);
    }

    /**
     * つぶやきをLogに吐き出す
     * 
     * @param voices MixiVoice のリスト。
     */
    protected void showVoices(ArrayList<MixiVoice> voices) {
        for (MixiVoice voice : voices) {
            Log.v(TAG, voice.screenName + ", " + voice.text);
        }
    }

    /**
     * つぶやき一覧の取得処理を開始する。この処理は非同期で実行されるため、取得完了を待たずに即座に呼び出し元に処理が戻る。
     * 
     * @see <a
     *      href="http://developer.mixi.co.jp/connect/mixi_graph_api/mixi_io_spec_top/voice-api/#toc-3">友人のつぶやき一覧の取得</a>
     */
    protected void loadVoice(int startIndex) {
        HashMap<String, String> options = new HashMap<String, String>();
        options.put("startIndex", String.valueOf(startIndex));
        setProgressBarIndeterminateVisibility(true);

        mContainer.send("/voice/statuses/friends_timeline", options, new CallbackListener() {
            @Override
            public void onComplete(Bundle values) {
                // values の response の中に、つぶやきの取得結果が返ってきます。
                String result = values.getString("response");

                // 結果の JSON を解析してつぶやきを取り出す
                ArrayList<MixiVoice> voices = new ArrayList<MixiVoice>();
                try {
                    JSONArray array = new JSONArray(result);
                    int length = array.length();
                    for (int i = 0; i < length; i++) {
                        JSONObject item = array.getJSONObject(i);
                        JSONObject user = item.getJSONObject("user");

                        // つぶやき1エントリを追加
                        MixiVoice voice = new MixiVoice();
                        voice.text = item.getString("text");
                        voice.screenName = user.getString("screen_name");

                        voices.add(voice);
                    }
                } catch (JSONException e) {
                    showToast("レスポンスのJSONを解析できませんでした");
                }

                // ボイスをログに出す
                showVoices(voices);

                setProgressBarIndeterminateVisibility(false);
            }

            @Override
            public void onFatal(ErrorInfo e) {
                showToast("つぶやきの取得中にエラーが発生しました");
                setProgressBarIndeterminateVisibility(false);
            }

            @Override
            public void onError(ErrorInfo e) {
                showToast("つぶやきの取得中にエラーが発生しました");
                setProgressBarIndeterminateVisibility(false);
            }

            @Override
            public void onCancel() {
                showToast("つぶやきの取得を中止しました");
                setProgressBarIndeterminateVisibility(false);
            }
        });
    }

    /**
     * People API を呼び出し、自分のプロフィール情報を取得する。
     * 
     * @see <a
     *      href="http://developer.mixi.co.jp/connect/mixi_graph_api/mixi_io_spec_top/people-api/">People
     *      API</a>
     */
    private void loadSelfProfile() {
        mContainer.send("/people/@me/@self", new CallbackListener() {
            @Override
            public void onComplete(Bundle values) {
                // response の中に、取得結果の JSON が入っています
                String result = values.getString("response");
                String nickname;

                // 結果の JSON を解析してプロフィール情報を取り出す
                try {
                    JSONObject json = new JSONObject(result);

                    JSONObject entry = json.getJSONObject("entry");
                    nickname = entry.optString("displayName");
                } catch (JSONException e) {
                    showToast("レスポンスのJSONを解析できませんでした");
                    return;
                }

                setTitle(nickname);
            }

            @Override
            public void onFatal(ErrorInfo e) {
                showToast("プロフィールの取得中にエラーが発生しました");
            }

            @Override
            public void onError(ErrorInfo e) {
                showToast("プロフィールの取得中にエラーが発生しました");
            }

            @Override
            public void onCancel() {
                showToast("プロフィールの取得を中止しました");
            }
        });
    }

    /**
     * 画面上に指定されたメッセージの Toast を表示する　
     * 
     * @param text 表示するメッセージ
     */
    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    /**
     * つぶやき 1 エントリを表現する要素。
     * 
     * @author yuki.fujisaki
     */
    public static class MixiVoice {
        public String text;

        public String screenName;
    }
}
