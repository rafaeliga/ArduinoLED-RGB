package br.com.rubythree.arduinoblinkled;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import br.com.rubythree.arduinoblinkled.HSVColorPickerDialog.OnColorSelectedListener;

import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;

public class MainActivity extends Activity {

	private static final String TAG = "ArduinoAccessory";

	private static final String ACTION_USB_PERMISSION = "com.google.android.DemoKit.action.USB_PERMISSION";

	private UsbManager mUsbManager;
	private PendingIntent mPermissionIntent;
	private boolean mPermissionRequestPending;

	UsbAccessory mAccessory;
	ParcelFileDescriptor mFileDescriptor;
	FileInputStream mInputStream;
	FileOutputStream mOutputStream;
	RelativeLayout relativeLayout;

	private HSVColorWheel colorWheel;
	private HSVValueSlider valueSlider;

	private static final int CONTROL_SPACING_DP = 20;
	private static final int SELECTED_COLOR_HEIGHT_DP = 50;
	private static final int BORDER_DP = 1;
	private static final int BORDER_COLOR = Color.BLACK;

	int selectedColor;

	private View selectedColorView;

	private ShakeListener mShaker;

	int initialColor;
	
	BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    
    volatile boolean stopWorker;
    
    private Handler mHandler;
    
    int redSelected, greenSelected, blueSelected;

	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbAccessory accessory = UsbManager.getAccessory(intent);
					if (intent.getBooleanExtra(
							UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						openAccessory(accessory);
					} else {
						Log.d(TAG, "permission denied for accessory "
								+ accessory);
					}
					mPermissionRequestPending = false;
				}
			} else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				UsbAccessory accessory = UsbManager.getAccessory(intent);
				if (accessory != null && accessory.equals(mAccessory)) {
					closeAccessory();
				}
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mUsbManager = UsbManager.getInstance(this);
		mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);

		if (getLastNonConfigurationInstance() != null) {
			mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
			openAccessory(mAccessory);
		}

		initialColor = 0xFFFFFFFF;

		colorWheel = new HSVColorWheel(this);
		valueSlider = new HSVValueSlider(this);

		this.selectedColor = initialColor;

		int borderSize = (int) (this.getResources().getDisplayMetrics().density * BORDER_DP);
		RelativeLayout layout = new RelativeLayout(this);

		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		lp.bottomMargin = (int) (this.getResources().getDisplayMetrics().density * CONTROL_SPACING_DP);
		colorWheel.setListener(new OnColorSelectedListener() {
			public void colorSelected(Integer color) {
				valueSlider.setColor(color, true);
			}
		});
		colorWheel.setColor(initialColor);
		colorWheel.setId(1);
		layout.addView(colorWheel, lp);

		int selectedColorHeight = (int) (this.getResources()
				.getDisplayMetrics().density * SELECTED_COLOR_HEIGHT_DP);

		FrameLayout valueSliderBorder = new FrameLayout(this);
		valueSliderBorder.setBackgroundColor(BORDER_COLOR);
		valueSliderBorder.setPadding(borderSize, borderSize, borderSize,
				borderSize);
		valueSliderBorder.setId(2);
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT,
				selectedColorHeight + 2 * borderSize);
		lp.bottomMargin = (int) (this.getResources().getDisplayMetrics().density * CONTROL_SPACING_DP);
		lp.addRule(RelativeLayout.BELOW, 1);
		layout.addView(valueSliderBorder, lp);

		valueSlider.setColor(initialColor, false);
		valueSlider.setListener(new OnColorSelectedListener() {
			@Override
			public void colorSelected(Integer color) {
				selectedColor = color;
				selectedColorView.setBackgroundColor(color);

				int red = (color >> 16) & 0xFF;
				int green = (color >> 8) & 0xFF;
				int blue = (color >> 0) & 0xFF;

				setColor(red, green, blue);
			}
		});
		valueSliderBorder.addView(valueSlider);

		FrameLayout selectedColorborder = new FrameLayout(this);
		selectedColorborder.setBackgroundColor(BORDER_COLOR);
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT,
				selectedColorHeight + 2 * borderSize);
		selectedColorborder.setPadding(borderSize, borderSize, borderSize,
				borderSize);
		lp.addRule(RelativeLayout.BELOW, 2);
		layout.addView(selectedColorborder, lp);

		selectedColorView = new View(this);
		selectedColorView.setBackgroundColor(selectedColor);
		selectedColorborder.addView(selectedColorView);

		setContentView(layout);

		final Vibrator vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

		mShaker = new ShakeListener(this);
		mShaker.setOnShakeListener(new ShakeListener.OnShakeListener() {
			public void onShake() {
				vibe.vibrate(100);
				setColor(255, 255, 255);

				valueSlider.setColor(initialColor, false);
				colorWheel.setColor(initialColor);
			}
		});
		
		/* BLUETOOTH TESTS */
//		findBT();
//		
//		try {
//			openBT();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

	protected void setColor(int red, int green, int blue) {
		byte[] buffer = new byte[3];

		buffer[0] = (byte) red;
		buffer[1] = (byte) green;
		buffer[2] = (byte) blue;

		/*		BLUETOOTH TESTS     */
		
//		try {
//			sendData("62625256325636251");
//		} catch (IOException e1) {
//			Log.i(TAG, "ERRROOOOOOOOOOOOOOORRR");
//			e1.printStackTrace();
//		}

		/*	    Serial cable Tests  */ 
		if (mOutputStream != null) {
			try {
				mOutputStream.write(buffer);
			} catch (IOException e) {
				Log.e(TAG, "write failed", e);
			}
		}
		
//		redSelected = red;
//		greenSelected = green;
//		blueSelected = blue;
		
//		mHandler = new Handler();
//		mHandler.postDelayed(postHand, 1000);
	}
	
	private Runnable postHand = new Runnable() {
        public void run() {
        	PostAsync post = new PostAsync();
        	post.execute(redSelected+"", greenSelected+"", blueSelected+""); 
        }
     };
     

	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mAccessory != null) {
			return mAccessory;
		} else {
			return super.onRetainNonConfigurationInstance();
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		if (mInputStream != null && mOutputStream != null) {
			return;
		}

		UsbAccessory[] accessories = mUsbManager.getAccessoryList();
		UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if (mUsbManager.hasPermission(accessory)) {
				openAccessory(accessory);
			} else {
				synchronized (mUsbReceiver) {
					if (!mPermissionRequestPending) {
						mUsbManager.requestPermission(accessory,
								mPermissionIntent);
						mPermissionRequestPending = true;
					}
				}
			}
		} else {
			Log.d(TAG, "mAccessory is null");
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		closeAccessory();
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(mUsbReceiver);
		super.onDestroy();
	}

	private void openAccessory(UsbAccessory accessory) {
		mFileDescriptor = mUsbManager.openAccessory(accessory);
		if (mFileDescriptor != null) {
			mAccessory = accessory;
			FileDescriptor fd = mFileDescriptor.getFileDescriptor();
			mInputStream = new FileInputStream(fd);
			mOutputStream = new FileOutputStream(fd);
			Log.d(TAG, "accessory opened");
		} else {
			Log.d(TAG, "accessory open fail");
		}
	}

	private void closeAccessory() {
		try {
			if (mFileDescriptor != null) {
				mFileDescriptor.close();
			}
		} catch (IOException e) {
		} finally {
			mFileDescriptor = null;
			mAccessory = null;
		}
	}
	
	void findBT()
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            Log.i(TAG, "No bluetooth adapter available");
        }
        
        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }
        
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals("SeeedBTSlave")) 
                {
                    mmDevice = device;
                    break;
                }
            }
        }
        
        Log.i(TAG, "Bluetooth Device Found");
    }
    
    void openBT() throws IOException
    {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);        
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();
        
        beginListenForData();
        
        Log.i(TAG, "Bluetooth Opened: "+mmDevice);
    }
    
    void beginListenForData()
    {
        final Handler handler = new Handler(); 
        final byte delimiter = 10; //This is the ASCII code for a newline character
        
        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {                
               while(!Thread.currentThread().isInterrupted() && !stopWorker)
               {
                    try 
                    {
                        int bytesAvailable = mmInputStream.available();                        
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;
                                    
                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            Log.i(TAG, "-------------"+data+"----------------");
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } 
                    catch (IOException ex) 
                    {
                        stopWorker = true;
                    }
               }
            }
        });

        workerThread.start();
    }
    
    void sendData(String msg) throws IOException
    {
    	Log.i(TAG, "Data Sent: "+msg);
    	Log.i(TAG, "Device: "+mmDevice);
    	mmOutputStream.write(msg.getBytes());
    }
    
    void closeBT() throws IOException
    {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
        Log.i(TAG, "Bluetooth Closed");
    }

}