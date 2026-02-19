package mg.rivolink.app.aruco;

import android.app.Activity;
import android.content.Intent;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import mg.rivolink.app.aruco.renderer.Renderer3D;
import mg.rivolink.app.aruco.utils.CameraParameters;
import mg.rivolink.app.aruco.view.PortraitCameraLayout;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.aruco.Aruco;
import org.opencv.aruco.DetectorParameters;
import org.opencv.aruco.Dictionary;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import org.rajawali3d.view.SurfaceView;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2 {

	public static final float SIZE = 0.04f;
	
	private Mat cameraMatrix;
	private MatOfDouble distCoeffs;

	private Mat rgb;

    private List<Mat> corners;
	private Dictionary dictionary;
	private DetectorParameters parameters;

    private CameraBridgeViewBase camera;

	SharedPreferences prefs;
	float originMarkerIndex;
	Mat originMarker0Position;
	String mqttPrefix;

	private TextView mqttStatusText;

	private Mat originTvec;
	private Mat originRvec;

	private boolean originDataAvailable = false;

	private final BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this){
        @Override
        public void onManagerConnected(int status){
			if(status == LoaderCallbackInterface.SUCCESS){
				Activity activity = MainActivity.this;
				
				cameraMatrix = Mat.eye(3, 3, CvType.CV_64FC1);
				distCoeffs = new MatOfDouble(Mat.zeros(5, 1, CvType.CV_64FC1));

				originTvec = new Mat(3, 1, CvType.CV_64F);
				originRvec = new Mat(3, 1, CvType.CV_64F);

				if(!CameraParameters.fileExists(activity)){
					CameraParameters.selectFile(activity);
					return;
				}

				CameraParameters.tryLoad(activity, cameraMatrix, distCoeffs);
				camera.enableView();
			}
			else {
				super.onManagerConnected(status);
			}
        }
    };
	private MqttAndroidClient mqttClient;

	public void openSettingsView(View v) {
		startActivity(
				new Intent(this, PreferencesActivity.class)
		);
	}

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main_layout);

        camera = ((PortraitCameraLayout)findViewById(R.id.camera_layout)).getCamera();
        camera.setVisibility(SurfaceView.VISIBLE);
        camera.setCvCameraViewListener(this);

        Renderer3D renderer = new Renderer3D(this);

		SurfaceView surface = (SurfaceView)findViewById(R.id.main_surface);
		surface.setTransparent(true);
		surface.setSurfaceRenderer(renderer);

		prefs = this.getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);

		this.mqttStatusText = (TextView) findViewById(R.id.text_mqtt_status);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data){
		CameraParameters.onActivityResult(this, requestCode, resultCode, data, cameraMatrix, distCoeffs);
	}

	@Override
    public void onResume(){
        super.onResume();

		if(!OpenCVLoader.initDebug()) {
			Toast.makeText(this, getString(R.string.error_native_lib), Toast.LENGTH_LONG).show();
			return;
		}

		loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
		this.originMarkerIndex = Integer.parseInt(prefs.getString("origin_0_marker_id", "0"));
		this.mqttPrefix = prefs.getString("mqtt_path", "aruco/markers");
		connectMQTT();
    }
	
	@Override
    public void onPause(){
		super.onPause();

		if(mqttClient != null) {
			Log.d("MQTT", "closing connection");
			try {
				if(mqttClient.isConnected()) {
					mqttClient.disconnect();
				}

				mqttClient.unregisterResources();

				mqttClient.close();
				// mqttClient.unregisterResources();
			} catch (MqttException | IllegalArgumentException e) {
				Log.e("MQTT", "Error terminating MQTT conection, but whatever...", e);
				// throw new RuntimeException(e);
			}
		}

		// keep activity open in background otherwise
    }

	@Override
    public void onDestroy(){
        super.onDestroy();

        if (camera != null)
            camera.disableView();
    }

	@Override
	public void onCameraViewStarted(int width, int height){
		rgb = new Mat();
		corners = new LinkedList<>();
		parameters = DetectorParameters.create();
		dictionary = Aruco.getPredefinedDictionary(
				Integer.parseInt(prefs.getString("dictionary_type", "0"))
		);

		this.originMarker0Position = new Mat(3, 1, CvType.CV_64F);
		this.originMarker0Position.put(0, 0, Float.parseFloat(prefs.getString("origin_0_marker_x", "0")));
		this.originMarker0Position.put(1, 0, Float.parseFloat(prefs.getString("origin_0_marker_y", "0")));
		this.originMarker0Position.put(2, 0, Float.parseFloat(prefs.getString("origin_0_marker_z", "0")));
	}

	@Override
	public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame){
		if(!CameraParameters.isLoaded()){
			return inputFrame.rgba();
		}
		
		Imgproc.cvtColor(inputFrame.rgba(), rgb, Imgproc.COLOR_RGBA2RGB);
        Mat gray = inputFrame.gray();

        MatOfInt ids = new MatOfInt();
		corners.clear();

		Aruco.detectMarkers(gray, dictionary, corners, ids, parameters);

		if(corners.isEmpty()) {
			return rgb;
		}

		Aruco.drawDetectedMarkers(rgb, corners, ids);

        Mat rvecs = new Mat();
        Mat tvecs = new Mat();

		Aruco.estimatePoseSingleMarkers(corners, SIZE, cameraMatrix, distCoeffs, rvecs, tvecs);

		boolean originFound = false;

		for(int i = 0; i < ids.rows(); i++) {
			if(ids.get(i, 0)[0] == this.originMarkerIndex) {
				originTvec.put(0, 0, tvecs.get(i, 0));
				originRvec.put(0, 0, rvecs.get(i, 0));

				originFound = originDataAvailable = true;
			}
		}

		if(originDataAvailable) {
			publishMarkers(ids, tvecs, rvecs, originFound);
		}

		for(int i = 0; i< ids.toArray().length; i++){
			draw3dCube(rgb, cameraMatrix, distCoeffs, rvecs.row(i), tvecs.row(i), new Scalar(255, 0, 0));
			Aruco.drawAxis(rgb, cameraMatrix, distCoeffs, rvecs.row(i), tvecs.row(i), SIZE/2.0f);
		}

		return rgb;
	}

	private void publishMarkers(MatOfInt ids, Mat tvecs, Mat rvecs, boolean originFound) {
		Mat R0 = new Mat();
		Calib3d.Rodrigues(
				originRvec,
				R0
		);
		Mat R0_inv = R0.t();

		Mat T0_inv = new Mat();
		Core.gemm(R0_inv, originTvec, -1, new Mat(), 0, T0_inv);

		JSONArray markers = new JSONArray();
		boolean publishMqtt = false;

		for (int i = 0; i < ids.rows(); i++) {
			Mat tvec = new Mat(3, 1, CvType.CV_64F);
			Mat rvec = new Mat(3, 1, CvType.CV_64F);

			tvec.put(0, 0, tvecs.get(i, 0));
			rvec.put(0, 0, rvecs.get(i, 0));

			// Convert to rotation matrix
			Mat R = new Mat();
			Calib3d.Rodrigues(rvec, R);

			// Transform rotation
			Mat R_rel = new Mat();
			Core.gemm(R0_inv, R, 1, new Mat(), 0, R_rel);

			// Transform translation
			Mat t = tvec.t();
			Mat t_diff = new Mat();
			Core.subtract(t, originTvec.t(), t_diff);

			Mat t_rel = new Mat();
			Core.gemm(R0_inv, t_diff.t(), 1, new Mat(), 0, t_rel);

			// Convert back to rvec
			Mat rvec_rel = new Mat();
			Calib3d.Rodrigues(R_rel, rvec_rel);

			Mat t_offset = new Mat();
			Core.add(t_rel, originMarker0Position, t_offset);

			// At this point: rvec_rel and t_rel give pose relative to marker 0
			int markerId = (int) ids.get(i, 0)[0];

			if(mqttClient.isConnected()) {
				try {
					JSONArray position = new JSONArray();
					for(int j = 0; j < 3; j++) {
						position.put(t_offset.get(j, 0)[0]);
					}

					JSONArray rotation = new JSONArray();
					for(int j = 0; j < 3; j++) {
						rotation.put(rvec_rel.get(j, 0)[0]);
					}

					JSONObject object = new JSONObject();
					object.put("id", markerId);
					object.put("position", position);
					object.put("rotation", rotation);

					if(markerId == this.originMarkerIndex) {
						// Log.d("Relative", String.format("XYZ: %f %f %f", t_rel.get(0, 0)[0], t_rel.get(1, 0)[0], t_rel.get(2, 0)[0]));
						object.put("origin", true);
					}else{
						// only publish of any marker apart from origin is found
						publishMqtt = true;
					}

					markers.put(object);
				} catch (JSONException e) {
					throw new RuntimeException(e);
				}
			}
		}

		if(!publishMqtt) {
			return;
		}

		// only publish if at least one marker is available
		try {
			JSONObject payload = new JSONObject();
			payload.put("markers", markers);
			payload.put("timestamp", (new Date()).getTime());
			payload.put("originFound", originFound);
			mqttClient.publish(this.mqttPrefix, payload.toString().getBytes(), 0, false);
		} catch (JSONException | MqttException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void onCameraViewStopped(){
		rgb.release();
	}
	
	public void draw3dCube(Mat frame, Mat cameraMatrix, MatOfDouble distCoeffs, Mat rvec, Mat tvec, Scalar color){
		double halfSize = SIZE/2.0;

		List<Point3> points = new ArrayList<>();
		points.add(new Point3(-halfSize, -halfSize, 0));
		points.add(new Point3(-halfSize,  halfSize, 0));
		points.add(new Point3( halfSize,  halfSize, 0));
		points.add(new Point3( halfSize, -halfSize, 0));
		points.add(new Point3(-halfSize, -halfSize, SIZE));
		points.add(new Point3(-halfSize,  halfSize, SIZE));
		points.add(new Point3( halfSize,  halfSize, SIZE));
		points.add(new Point3( halfSize, -halfSize, SIZE));

		MatOfPoint3f cubePoints = new MatOfPoint3f();
		cubePoints.fromList(points);

		MatOfPoint2f projectedPoints = new MatOfPoint2f();
		Calib3d.projectPoints(cubePoints, rvec, tvec, cameraMatrix, distCoeffs, projectedPoints);

		List<Point> pts = projectedPoints.toList();

	    for(int i=0; i<4; i++){
	        Imgproc.line(frame, pts.get(i), pts.get((i+1)%4), color, 2);
	        Imgproc.line(frame, pts.get(i+4), pts.get(4+(i+1)%4), color, 2);
	        Imgproc.line(frame, pts.get(i), pts.get(i+4), color, 2);
	    }	        
	}

	private void toast(String text) {
		Toast.makeText(this, text, Toast.LENGTH_LONG).show();

	}

	private void connectMQTT() {
		mqttStatusText.setText("MQTT: connecting...");

		String URI = prefs.getString("mqtt_uri", "");
		if (URI.isEmpty() || "tcp://".equals(URI)) {
			toast("No MQTT URI configured");
			mqttStatusText.setText("MQTT not configured");
			mqttStatusText.setTextColor(Color.RED);
			return;
		}

		mqttClient = new MqttAndroidClient(
				getApplicationContext(),
				URI,
				"test"
		);
		mqttClient.setCallback(new MqttCallbackExtended() {
			@Override
			public void connectComplete(boolean reconnect, String serverURI) {
				Log.d("MQTT", "reconnect: " + reconnect);
				if(reconnect){
					toast("MQTT reconnected.");
				}else{
					toast("MQTT connected.");
				}
				mqttStatusText.setText("MQTT: connected");
				mqttStatusText.setTextColor(Color.GREEN);
			}

			@Override
			public void connectionLost(Throwable cause) {
				Log.d("MQTT", "connectionLost: " + cause);
				toast("mqtt connection lost. reconnecting...");

				mqttStatusText.setText("MQTT: disconnected");
				mqttStatusText.setTextColor(Color.RED);
			}

			@Override
			public void messageArrived(String topic, MqttMessage message) {
				Log.d("MQTT", "messageArrived: " + topic);

			}

			@Override
			public void deliveryComplete(IMqttDeliveryToken token) {
				// Log.d("MQTT", "deliveryComplete: " + token);
			}
		});
		MqttConnectOptions options = new MqttConnectOptions();
		options.setAutomaticReconnect(true);
		options.setCleanSession(true);

		try {
			mqttClient.connect(options, new IMqttActionListener() {
				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					Log.d("MQTT", "Connection success");
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					Log.d("MQTT", "Connection failure: ");
					exception.printStackTrace();
					toast("MQTT connection failure. URI correct?");
					mqttStatusText.setText("MQTT: connection failed");
					mqttStatusText.setTextColor(Color.RED);
				}
			});
		} catch (MqttException e) {
			mqttStatusText.setText("MQTT: MQTT error");
			mqttStatusText.setTextColor(Color.RED);
			e.printStackTrace();
		} catch (Exception e) {
			mqttStatusText.setText("MQTT: config?");
			mqttStatusText.setTextColor(Color.RED);
			e.printStackTrace();
			toast("MQTT configuration wrong, probably.");
		}
	}
}


