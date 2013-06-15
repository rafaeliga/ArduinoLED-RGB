package br.com.rubythree.arduinoblinkled;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.os.AsyncTask;
import android.util.Log;

class PostAsync extends AsyncTask<String, Integer, Void>
{

    @Override
    protected void onPostExecute(Void result) {}

    @Override
    protected void onPreExecute() {}

    @Override
    protected Void doInBackground(String... params) {
    	HttpClient httpclient = new DefaultHttpClient();
	    HttpPost httppost = new HttpPost("http://192.168.1.248/");

	    try {
	        // Add your data
	        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(3);
	        nameValuePairs.add(new BasicNameValuePair("red", params[0]));
	        nameValuePairs.add(new BasicNameValuePair("green", params[1]));
	        nameValuePairs.add(new BasicNameValuePair("blue", params[2]));
	        httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

	        // Execute HTTP Post Request
	        HttpResponse response = httpclient.execute(httppost);
	        Log.i("ASYNC", "RESPONSE: "+nameValuePairs);
	        
	    } catch (ClientProtocolException e) {
	    	e.printStackTrace();
	    } catch (IOException e) {
	    	e.printStackTrace();
	    }
    	
        return null;
    }
}
