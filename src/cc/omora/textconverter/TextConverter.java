package cc.omora.textconverter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ListView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import org.jaxen.JaxenException;
import org.jaxen.XPath;
import org.jaxen.dom.DOMXPath;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.DomSerializer;
import android.app.ProgressDialog;

public class TextConverter extends Activity {
	private static final int MENU_ID_SHARE  = (Menu.FIRST + 1);
	private static final int MENU_ID_RELOAD = (Menu.FIRST + 2);

	static final private String WEDATA_URL = "http://wedata.net/databases/Text%20Conversion%20Services/items.json";
	static final private String USER_AGENT = "Text Converter for Android";

	ProgressDialog mLoadingSiteInfoDialog;  
	ProgressDialog mConvertingTextDialog;  
	ListView mListView;
	EditText mEditText;

	int mPosition;
	JSONArray mSiteInfo;
	
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			LinearLayout linearLayout = new LinearLayout(this);
			linearLayout.setOrientation(LinearLayout.VERTICAL);
			mEditText = new EditText(this);
			mListView = new ListView(this);
	        	mListView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
	        		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
	        			mPosition = position;
					convertText();
	        		}
	        	});
			linearLayout.addView(mEditText);
			linearLayout.addView(mListView);
			setContentView(linearLayout);
			loadSiteInfo();
		} catch (Exception e) {
		}
	}
	public void onDestroy() {
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putString("siteinfo", mSiteInfo.toString());
		editor.commit();
	}
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_ID_SHARE,  Menu.NONE, "Share");
		menu.add(Menu.NONE, MENU_ID_RELOAD, Menu.NONE, "Reload");
		return super.onCreateOptionsMenu(menu);
	}
	public boolean onPrepareOptionsMenu(Menu menu) {
		return super.onPrepareOptionsMenu(menu);
	}
	public boolean onOptionsItemSelected(MenuItem item) {
		boolean ret = true;
		switch (item.getItemId()) {
		default:
			ret = super.onOptionsItemSelected(item);
			break;
		case MENU_ID_SHARE:
			Intent intent = new Intent();
			intent.setAction(Intent.ACTION_SEND);
			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_TEXT, mEditText.getText().toString());
			startActivity(intent);
			break;
		case MENU_ID_RELOAD:
			loadSiteInfo();
			break;
		}
		return ret;
	}
	public void convertText() {
		mConvertingTextDialog = new ProgressDialog(this);
		mConvertingTextDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		mConvertingTextDialog.setMessage("Converting Text ...");
		mConvertingTextDialog.setCancelable(true);
		mConvertingTextDialog.show();
		final Handler handler = new Handler() {
			public void handleMessage(Message message) {
				String text = message.getData().get("result").toString();
				mEditText.setText(text);
			}
		};
		new Thread(new Runnable() {
			public void run() {
			    	try {
					JSONObject data = mSiteInfo.getJSONObject(mPosition).getJSONObject("data");
					String input = mEditText.getText().toString();
					String url = data.getString("url").replaceAll("%s", URLEncoder.encode(input));
					String xpath = data.getString("xpath");
	    				String result = xpath.equals("") ? httpGet(url) : httpGetWithXPath(url, xpath);
	    				if(data.getString("action").equals("append")) result = input + result;
	        			Message message = new Message();
	        			Bundle bundle = new Bundle();
	        			bundle.putString("result", result);
	        			message.setData(bundle);
	    				handler.sendMessage(message);
	        			mConvertingTextDialog.dismiss();
				} catch(JSONException e) {
				}
			}
		}).start();
	}
	public void loadSiteInfo() {
		mLoadingSiteInfoDialog = new ProgressDialog(this);
		mLoadingSiteInfoDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		mLoadingSiteInfoDialog.setMessage("Loading SITEINFO ...");
		mLoadingSiteInfoDialog.setCancelable(true);
		mLoadingSiteInfoDialog.show();
		final Handler handler = new Handler();
		new Thread(new Runnable() {
			public void run() {
				String string = PreferenceManager.getDefaultSharedPreferences(TextConverter.this).getString("siteinfo", "[]");
				try {
					if(!string.equals("[]")) {
						mSiteInfo = new JSONArray(string);
					} else {
						mSiteInfo = new JSONArray(httpGet(WEDATA_URL));
					}
				} catch(JSONException e) {
				}
				handler.post(new Runnable() {
					public void run() {
						try {
							List<String> items = new ArrayList<String>();
							for(int i = 0; i < mSiteInfo.length(); i++) {
								items.add(mSiteInfo.getJSONObject(i).getString("name"));
							}
							ArrayAdapter<String> adapter = new ArrayAdapter<String>(
								TextConverter.this,
								android.R.layout.simple_list_item_1,
								items
							);
							mListView.setAdapter(adapter);
							mLoadingSiteInfoDialog.dismiss();
						} catch(JSONException e) {
						}
					}
				});
			}
		}).start();
	}
	// TODO: charset (Shift_JIS, EUC-JP)
	private String httpGet(String sUrl) {
		    StringBuffer buf = new StringBuffer();
		try {
		        URL url = new URL(sUrl);
		        HttpURLConnection http = (HttpURLConnection) url.openConnection();
			    http.setRequestMethod("GET"); http.connect();
			    InputStream in = http.getInputStream();
			    BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			    String line;
			    while ((line = reader.readLine()) != null) { buf.append(line + "\n"); }
		} catch(MalformedURLException e) {
	            Log.v("debug", ""+e);
		} catch(IOException e) {
	            Log.v("debug", ""+e);
		}
		return buf.toString();
	}
	private String getTextContent(Node node) {
		Node child;
		String sContent = node.getNodeValue() != null ? node.getNodeValue() : "";
	
		NodeList nodes = node.getChildNodes();
		for(int i = 0; i < nodes.getLength(); i++) {
			child = nodes.item(i);
			sContent += child.getNodeValue() != null ? child.getNodeValue() : "";
			if(nodes.item(i).getChildNodes().getLength() > 0) {
				sContent += getTextContent(nodes.item(i));
			}
		}
		return sContent;
	}
	public String httpGetWithXPath(String sUrl, String exp) {
		String sContent = "";
		try {
			HtmlCleaner cleaner = new HtmlCleaner();
			CleanerProperties props = cleaner.getProperties();
			TagNode node = cleaner.clean(httpGet(sUrl));
			Document doc = new DomSerializer(props, true).createDOM(node);
			XPath xpath = new DOMXPath(exp);
			Object xResult = xpath.evaluate(doc);
			if(xResult instanceof String) {
				sContent = (String) xResult;
			} else {
				ArrayList<Node> al = (ArrayList<Node>) xResult;
				sContent = getTextContent((Node) al.get(0));
			}
		} catch (JaxenException e) {
		} catch (IOException e) {
		} catch (Exception e) {
		}
		return sContent;
	}
}
