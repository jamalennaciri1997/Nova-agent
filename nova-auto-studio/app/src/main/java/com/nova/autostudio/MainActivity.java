package com.nova.autostudio;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.content.Intent;
import android.net.Uri;
public class MainActivity extends Activity {
 private WebView view;
 private ValueCallback<Uri[]> fileCallback;
 @Override public void onCreate(Bundle state) {
 super.onCreate(state); view = new WebView(this);
 view.getSettings().setJavaScriptEnabled(true);
 view.getSettings().setDomStorageEnabled(true);
 view.getSettings().setAllowFileAccess(true);
 view.setWebViewClient(new WebViewClient());
 view.setWebChromeClient(new WebChromeClient(){
 @Override public boolean onShowFileChooser(WebView w,ValueCallback<Uri[]> callback,FileChooserParams params){
 if(fileCallback!=null)fileCallback.onReceiveValue(null);
 fileCallback=callback;
 try{startActivityForResult(params.createIntent(),201);return true;}catch(Exception e){fileCallback=null;return false;}
 }});
 setContentView(view);view.loadUrl("file:///android_asset/index.html");
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==201&&fileCallback!=null){fileCallback.onReceiveValue(result==RESULT_OK?WebChromeClient.FileChooserParams.parseResult(result,data):null);fileCallback=null;}}
 @Override public void onBackPressed(){if(view.canGoBack())view.goBack();else super.onBackPressed();}
}
