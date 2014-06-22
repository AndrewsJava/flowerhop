package co.harlequinmettle.flowerhopper;

import java.lang.reflect.Field;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

public class FlowerHopper extends Activity implements OnTouchListener,
		SensorEventListener, PaintingNameStrings {

	Bitmap bg, b, wd, wl;
	int Nflowers;

	boolean[] visited;
	Bitmap[] fl;
	Matrix[] flMod;
	float[] flX;
	float[] flY;

	InnerView mIV;
	
	Matrix wingAnim = new Matrix();
	Matrix wingAnim2 = new Matrix();
	Matrix bodyAnim = new Matrix();
	Matrix bgMod = new Matrix();
	Paint buttonPaint = new Paint(); 
	float finalScaleX = 1, finalScaleY = 1;
	float finalTX = 0, finalTY = 0;
	float animScaleX = 1, animScaleY = 1;
	float animTX = 0, animTY = 0;
	float lastX, lastY; 
	float airborn = 20;
	private DisplayMetrics metrics = new DisplayMetrics();
	float sw, sh;
	float textSize = 25;
	float tX = 200, tY = 150, scaleFactorX = 1, scaleFactorY = 1,
			skewFactorX = 0.5f, skewFactorY = 0, rotation = -20;
	float bgX = -40, bgY = -40;
	float velX = 0, velY = 0;
	final float gravity = 0.07f;
	final float terminalVelocity = 14.9f;
	double flapOscillator = 1.129987113341;
	float flapSpeed = 0.008f;
	double buttonOscillator = 1.129987113341;
	float buttonOscSpd = 0.00051f;
	long lastTime = System.currentTimeMillis();
	public boolean facingLeft = true;

	private static final float shakeThresholdInGForce = 1.65F;
	private static final float gravityEarth = SensorManager.GRAVITY_EARTH;
	private SensorManager mSensorManager;

	long flapTime = 0;
	boolean flapping = false;
	private float scalingFactor;
	private int[] seenRecently = new int[8];
	private int cIndex = 0;
	private String INDEX = "current bg painting";
	private String ALREADY_SEEN = "last 8 bg's";
	// private String FLOWER_POSITIONS = "ratio to bg";
	private String FLOWER_COUNT = "how many flowers";
	private String BEEN_TO = "which flowers have been to";
	private boolean autoFly;
	public boolean flyRight;
	public boolean animateFinal;
	public boolean finishedScene;
	public boolean doneAnimation;
	private long finishTime;
	private float FLOAT_FLAP_SPEED = 0.008F;
 
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		// always set metrics for screen width and height
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		sw = metrics.widthPixels;
		sh = metrics.heightPixels;

		// stuff? needed for sense detector
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mSensorManager.registerListener(this,
				mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_NORMAL);

		mIV = new InnerView(this);

		if (savedInstanceState != null) {
			cIndex = savedInstanceState.getInt(INDEX) + 1;
			Nflowers = savedInstanceState.getInt(FLOWER_COUNT);
		} else { // start at sun
			cIndex = (int) (paintingNames.length * Math.random());

			Nflowers = 2 + (int) (Math.random() * 3);

		}

		reset();
		// setting up background and flowers
		// visible background index

		setContentView(mIV);

		mIV.setOnTouchListener(this);

		// register canvas to receive events as defined in this
		mIV.setOnTouchListener(this);
	}

	public void reset() {
		buttonPaint.setARGB(0, 200, 160, 250);
		visited = new boolean[Nflowers];
		fl = new Bitmap[Nflowers];
		flMod = new Matrix[Nflowers];
		flX = new float[Nflowers];
		flY = new float[Nflowers];
		bg = getBitmapFromImageName(paintingNames[cIndex]);
		scalingFactor = 2 * (sw > sh ? sh / bg.getHeight() : 2 * sw
				/ bg.getWidth());
		float startPt = 20;
		float increment = (bg.getWidth() * scalingFactor - startPt) / Nflowers;
		for (int i = 0; i < Nflowers; i++) {
			fl[i] = getBitmapFromImageName(flowerNames[(int) (Math.random() * flowerNames.length)]);
			flMod[i] = new Matrix();

			flX[i] = (float) (startPt + Math.random() * (increment));
			startPt = flX[i] +fl[i].getWidth();
			increment = (bg.getWidth() * scalingFactor - startPt)
					/ (Nflowers - i-1);
			// GETTING OCCASIONAL NULL POINTER EXCEPTION
			flY[i] = bg.getHeight() * scalingFactor - fl[i].getHeight();
		}

		b = getBitmapFromImageName("bb");
		wd = getBitmapFromImageName("bwd");
		wl = getBitmapFromImageName("bwl");

		tX = sw / 2 - wd.getWidth() / 2;
		tY = sh / 2 - wd.getHeight() / 2;
		velX = 0;
		velY = 0;

		bgX = (sw - scalingFactor * bg.getWidth()) / 2;
		bgY = (sh - scalingFactor * bg.getHeight()) / 2;

		animateFinal = false;
		finishedScene = false;
		doneAnimation = false;
	}

	public class InnerView extends View {
		RectF center = new RectF((int) sw / 2 - 10, (int) sh / 2 - 10,
				(int) sw / 2 + 10, (int) sh / 2 + 10);
		long prevTime;
		private float lastTX;
		private float lastTY;
		private RectF buttonRect = new RectF(sw - 100, 0, sw, 100);
		private Paint textPaint = new Paint();

		public InnerView(Context context) {
			super(context);
			textPaint.setTextSize(35); 
			textPaint.setARGB(190, 100, 60, 150);
			prevTime = System.currentTimeMillis(); 
		}

		public void updatePhysics(long time) {

			float dt = time - prevTime;

			if (flapping) {
				flapTime += dt;
				if (flapTime > 800) {
					flapSpeed = FLOAT_FLAP_SPEED;
					flapping = false;
				}
			}

			if (autoFly) {

				flapSpeed = 2 * FLOAT_FLAP_SPEED;
				airborn = 25;
				if (Math.random() < 0.25) {
					if (flyRight)
						velX = terminalVelocity;
					else
						velX = -terminalVelocity;
					velY = -terminalVelocity;
				}
			}
			flapOscillator += flapSpeed * dt;
			scaleFactorY = (float) Math.cos(flapOscillator);

			velX *= 0.98;
			if (velY < terminalVelocity)
				velY += dt * gravity;
			if (velY > terminalVelocity)
				velY = terminalVelocity;
			if (velY < -terminalVelocity)
				velY += dt * gravity;
			if (velX > 2 * terminalVelocity)
				velX = 2 * terminalVelocity;
			if (velX < -2 * terminalVelocity)
				velX = -2 * terminalVelocity;

			// if(bgX>-5)bgX = -7;

			float dX = velX * dt / 100;
			float dY = velY * dt / 100;
			movePosition(dX, dY);

			facingLeft = velX < 0;
			prevTime = time;

		}

		public void movePosition(float dX, float dY) {
			for (int i = 0; i < Nflowers; i++) {
				if (tX + wd.getWidth() / 2 > bgX + flX[i]
						&& tX + wd.getWidth() / 2 < bgX + flX[i]
								+ fl[i].getWidth() && velY > 0
						&& velX < terminalVelocity && tY > bgY + flY[i]
						&& tY < bgY + flY[i] + fl[i].getHeight() / 7) {
					if (!visited[i]) {
						visited[i] = true;

					} else if (velY < 0) {
						continue;
					}
					boolean end = true;
					for (boolean been : visited) {
						if (!been)
							end = false;
					}
					if (end) {
						animateFinal = true;
						finalTX = -bgX;
						finalTY = -bgY;
						finalScaleX = sw / (bg.getWidth() * scalingFactor);
						finalScaleY = sh / (bg.getHeight() * scalingFactor);
						// nextScene();
						finishedScene = true;
						finishTime = System.currentTimeMillis();
					}
					flapSpeed = FLOAT_FLAP_SPEED / 4;
					airborn = 0;
					// if(!doneAnimation)
					return;
				}
			}

			// if(lastTX>)

			int buffer = 10;
			// initialy assume move butterfly
			tX += dX;
			tY += dY;
			// if butterfly is near border and border of bg outside screen
			// undo original move and move bg
			if (tX + wd.getWidth() / 2 < 0.25 * sw && bgX < -buffer
					&& !animateFinal) {
				bgX -= dX;// move background instead
				tX -= dX;// undo origial move
			}
			if (tX + wd.getWidth() / 2 > 0.75 * sw
					&& bgX - buffer > sw - scalingFactor * bg.getWidth()
					&& !animateFinal) {
				bgX -= dX;
				tX -= dX;
				// if(bgX-buffer< metrics.widthPixels - scalingFactor *
				// bg.getWidth())bgX+=dX;
			}

			if (tY < 0.25 * sh && bgY < -buffer && !animateFinal) {
				bgY -= dY;
				tY -= dY;
				autoFly = false;
				flapSpeed = FLOAT_FLAP_SPEED;
				// if (tY < -wd.getHeight()) tY += dY;
			}
			if (tY + wd.getHeight() > 0.75 * sh
					&& bgY - buffer > sh - scalingFactor * bg.getHeight()) {
				bgY -= dY;
				tY -= dY;
				if (tY > sw + wd.getHeight()) {
					tY += dY;
				}
			}

			if (tY > sh - fl[0].getHeight() / 2) {
				autoFly = true;

				flapSpeed = 2 * FLOAT_FLAP_SPEED;
				if (tX + wd.getWidth() / 2 > sw / 2)
					flyRight = false;
				else
					flyRight = true;
			}

			if (animateFinal) {
				if ((animTY + tY) * animScaleY < -wd.getHeight())
					tY -= dY;

				if ((animTY + tY) * animScaleY > sh)
					tY -= dY;

				if ((animTX + tX) * animScaleX < 0)// -wd.getWidth()
					tX -= dX;

				if ((animTX + tX) * animScaleX > sw)
					tX -= dX;
			}
			lastTX = tX;
			lastTY = tY;
			// if(bgX>0)bgX=-1;
			// if(bgY>0)bgY=-1;
			// if(bgX<
			// metrics.heightPixels-scalingFactor*bg.getHeight())bgY=metrics.heightPixels-scalingFactor*bg.getHeight()+1;

		}

		private void nextScene() {
			// TODO Auto-generated method stub
			animScaleX = 1;
			animScaleY = 1;
			animTX = 0;
			animTY = 0;
			finishedScene = false;
			animateFinal = false;
			cIndex = (int) (paintingNames.length * Math.random());

			reset();

		}

		public void onDraw(Canvas c) {
			// c.drawARGB(0, 200, 160, 250);
			updatePhysics(System.currentTimeMillis());
			if (animateFinal) {
				final float animationFactor = 0.005f;
				if (animScaleX > finalScaleX) {
					animScaleX -= (1 - finalScaleX) * animationFactor;
					// animTX-=(1-finalScaleX )*animationFactor*bg.getWidth()
					// /2;

				} else {
					animScaleX = finalScaleX;
				}
				if (animScaleY > finalScaleY) {
					animScaleY -= (1 - finalScaleY) * animationFactor;
					// animTY-=(1-finalScaleY )*animationFactor*bg.getHeight()
					// /2;

				} else {
					animScaleY = finalScaleY;
				}

				animTX += animationFactor * finalTX;
				animTY += animationFactor * finalTY;

				if (animTX > finalTX)
					animTX = finalTX;
				if (animTY > finalTY)
					animTY = finalTY;
				if (System.currentTimeMillis() - finishTime > 2000
						|| animTX == finalTX && animTY == finalTY
						&& animScaleX == finalScaleX
						&& animScaleY == finalScaleY)
					doneAnimation = true;

			}
			wingAnim.reset();
			wingAnim2.reset();
			bodyAnim.reset();
			if (facingLeft) {
				flipButterflyParts();// skew*=-1?

				wingAnim.postSkew(skewFactorX, 0);
				wingAnim.postScale(1, scaleFactorY);

				wingAnim2.postSkew(-skewFactorX, 0);
				wingAnim2.postScale(1, scaleFactorY);

			} else {

				wingAnim.postSkew(-skewFactorX, 0);
				wingAnim.postScale(1, scaleFactorY);

				wingAnim2.postSkew(skewFactorX, 0);
				wingAnim2.postScale(1, scaleFactorY);

			}

			allRotate();
			wingAnim.postTranslate(tX, tY + b.getHeight() / 3 - airborn
					* scaleFactorY);
			wingAnim2.postTranslate(tX, tY + b.getHeight() / 3 - airborn
					* scaleFactorY);

			bodyAnim.postTranslate(tX, tY - airborn * scaleFactorY);

			wingAnim.postTranslate(animTX, animTY);
			wingAnim2.postTranslate(animTX, animTY);
			bodyAnim.postTranslate(animTX, animTY);

			wingAnim.postScale(animScaleX, animScaleY);
			wingAnim2.postScale(animScaleX, animScaleY);
			bodyAnim.postScale(animScaleX, animScaleY);

			bgMod.reset();
			bgMod.postScale(scalingFactor, scalingFactor);
			bgMod.postTranslate(bgX, bgY);

			bgMod.postTranslate(animTX, animTY);
			bgMod.postScale(animScaleX, animScaleY);

			c.drawBitmap(bg, bgMod, null);

			c.drawBitmap(b, bodyAnim, null);
			if (scaleFactorY > 0) {
				c.drawBitmap(wd, wingAnim, null);

				for (int i = 0; i < Nflowers; i++) {
					flMod[i].reset();
					flMod[i].postTranslate(bgX, bgY);
					flMod[i].postTranslate(flX[i], flY[i]);

					flMod[i].postTranslate(animTX, animTY);
					flMod[i].postScale(animScaleX, animScaleY);

					c.drawBitmap(fl[i], flMod[i], null);
				}
				c.drawBitmap(wl, wingAnim2, null);
				
			} else {

				c.drawBitmap(wl, wingAnim, null);

				for (int i = 0; i < Nflowers; i++) {
					flMod[i].reset();
					flMod[i].postTranslate(bgX, bgY);
					flMod[i].postTranslate(flX[i], flY[i]);

					flMod[i].postTranslate(animTX, animTY);
					flMod[i].postScale(animScaleX, animScaleY);

					c.drawBitmap(fl[i], flMod[i], null);
				}
				c.drawBitmap(wd, wingAnim2, null);

			}
			if (doneAnimation) {
				long time = System.currentTimeMillis();
				float dt = time - lastTime;
				buttonOscillator += dt * buttonOscSpd;
				double sinsq = Math.sin(buttonOscillator)
						* Math.sin(buttonOscillator);
				double sinsq2 = Math.sin(buttonOscillator+Math.PI/3)
						* Math.sin(buttonOscillator+Math.PI/3);
				buttonPaint.setAlpha((int) (180 * sinsq));
				textPaint.setAlpha((int) (180 * sinsq));
				c.drawText("NEXT", sw - 95, 60, textPaint);
				lastTime = time;
				c.drawOval(buttonRect, buttonPaint);
			} 
			mIV.invalidate();
		} 
		private void flipButterflyParts() {  
			// wingAnim.reset();
			wingAnim.postTranslate(-wd.getWidth() / 2, -wd.getHeight() / 2);
			wingAnim.postScale(-1, 1);
			wingAnim.postTranslate(wd.getWidth() / 2, wd.getHeight() / 2);

			// wingAnim2.reset();
			wingAnim2.postTranslate(-wd.getWidth() / 2, -wd.getHeight() / 2);
			wingAnim2.postScale(-1, 1);
			wingAnim2.postTranslate(wd.getWidth() / 2, wd.getHeight() / 2);

			// bodyAnim.reset();
			bodyAnim.postTranslate(-wd.getWidth() / 2, -wd.getHeight() / 2);
			bodyAnim.postScale(-1, 1);
			bodyAnim.postTranslate(wd.getWidth() / 2, wd.getHeight() / 2);

		}

		private void allRotate() {
			// TODO Auto-generated method stub
			// wingAnim.reset();
			float rotationFactor = rotation;
			if (facingLeft)
				rotationFactor *= -1;
			wingAnim.postTranslate(-wd.getWidth() / 2, -wd.getHeight() / 2);
			wingAnim.postRotate(rotationFactor);
			wingAnim.postTranslate(wd.getWidth() / 2, wd.getHeight() / 2);

			// wingAnim2.reset();
			wingAnim2.postTranslate(-wd.getWidth() / 2, -wd.getHeight() / 2);
			wingAnim2.postRotate(rotationFactor);
			wingAnim2.postTranslate(wd.getWidth() / 2, wd.getHeight() / 2);

			// bodyAnim.reset();
			bodyAnim.postTranslate(-wd.getWidth() / 2, -wd.getHeight() / 2);
			bodyAnim.postRotate(rotationFactor / 2);
			bodyAnim.postTranslate(wd.getWidth() / 2, wd.getHeight() / 2);

		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		// TODO Auto-generated method stub
		if (event.getX() > sw - 100 && event.getY() < 100 && finishedScene
				&& doneAnimation)
			mIV.nextScene();
		autoFly = false;
		int velocityFactorX = 50;
		int velocityFactorY = 80;
		int dzero = 30;
		float distance = 0;
		if (airborn == 0) {
			airborn = 25;
			flapSpeed = 2 * FLOAT_FLAP_SPEED;
		}
		switch (event.getActionMasked()) {
		case MotionEvent.ACTION_DOWN:
			lastX = event.getX();
			lastY = event.getY();
			// (animTY+tY)*animScaleY
			distance = this.distance(lastX, lastY, tX + wd.getWidth() / 2, tY);
			if (animScaleX * (animTX + tX + wd.getWidth() / 2) > 0
					&& animScaleX * (animTX + tX + wd.getWidth() / 2) < sw) {
				velX = velocityFactorX
						* (animScaleX * (animTX + tX + wd.getWidth() / 2) - lastX)
						/ (dzero + distance);
			} else {
				if (bgX > -30)
					velX = Math.abs(velocityFactorX
							* (tX + wd.getWidth() / 2 - lastX)
							/ (dzero + distance));
				else
					velX = -Math.abs(velocityFactorX
							* (tX + wd.getWidth() / 2 - lastX)
							/ (dzero + distance));
			}
			if (animScaleY * (animTY + tY) > 0
					&& animScaleY * (animTY + tY) < sh - wd.getHeight()) {
				velY = velocityFactorY * (animScaleY * (animTY + tY) - lastY)
						/ (dzero + distance);
			} else {
				velY = -Math.abs(velocityFactorY
						* (animScaleY * (animTY + tY) - lastY)
						/ (dzero + distance));
			}
			if(doneAnimation)velY = -1.3f*terminalVelocity;
			if (velY > terminalVelocity)
				velY = terminalVelocity * 1.5f;
			if (!flapping) {
				flapSpeed = 3 * FLOAT_FLAP_SPEED;
				flapping = true;
				flapTime = 0;
			}
			break;
		case MotionEvent.ACTION_UP:
			break;
		case MotionEvent.ACTION_MOVE:
			int moveDiff = 5;
			if (event.getX() - lastX > moveDiff) {
				velX = Math
						.abs(velocityFactorX
								* (animScaleX
										* (animTX + tX + wd.getWidth() / 2) - lastX)
								/ (dzero + distance));
			}
			if (event.getX() - lastX < -moveDiff) {
				velX = -Math
						.abs(velocityFactorX
								* (animScaleX
										* (animTX + tX + wd.getWidth() / 2) - lastX)
								/ (dzero + distance));
			}
			if (event.getY() - lastY > moveDiff) {
				velY = Math
						.abs(velocityFactorY
								* (animScaleY
										* (animTY + tY + wd.getHeight() / 2) - lastY)
								/ (dzero + distance));
			}

			if(doneAnimation)velY = -1.3f*terminalVelocity;
			lastX = event.getX();
			lastY = event.getY();
			break;
		}
		return true;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// ignore

	}

	// works with setup in onCreate
	// snagged code from stackoverflow submission
	@Override
	public void onSensorChanged(SensorEvent event) {
		float x = event.values[0];
		float y = event.values[1];
		float z = event.values[2];

		float gX = x / gravityEarth;
		float gY = y / gravityEarth;
		float gZ = z / gravityEarth;
		// good to know, thanks
		// G-Force will be 1 when there is no movement. (gravity)
		double gForce = Math.sqrt(gX * gX + gY * gY + gZ * gZ);

		if (gForce > shakeThresholdInGForce) {

			tX = sw / 2 - wd.getWidth() / 2;
			tY = sh / 2 - wd.getHeight() / 2;
			if (animScaleY * (bgY + animTY) < -50)
				bgY = -50;
			velY = -terminalVelocity;
			// myImV.jumpPoint();
			// myImV.invalidate();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		// Save the user's state

		savedInstanceState.putInt(INDEX, cIndex);
		savedInstanceState.putIntArray(ALREADY_SEEN, seenRecently);
		// savedInstanceState.putFloatArray(FLOWER_POSITIONS, );
		savedInstanceState.putInt(FLOWER_COUNT, Nflowers);
		savedInstanceState.putBooleanArray(BEEN_TO, visited);

		// if you say so ...
		// Always call the superclass so it can save the view hierarchy state
		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mSensorManager.registerListener(this,
				mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	protected void onPause() {
		mSensorManager.unregisterListener(this);
		super.onPause();
	}

	// takes in string and calculates the R file int from res
	private Bitmap getBitmapFromImageName(String resName) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inScaled = false;

		try {
			@SuppressWarnings("rawtypes")
			Class res = R.drawable.class;
			Field field = res.getField(resName);
			int drawableId = field.getInt(null);

			return BitmapFactory.decodeResource(getResources(), drawableId,
					options);

		} catch (Exception e) {
			e.printStackTrace();
		}
		// should return a default image r.id
		return null;// possible to cause a problem
	}

	public float distance(float a1, float a2, float b1, float b2) {
		float dist = (float) (Math.sqrt((a1 - b1) * (a1 - b1) + (a2 - b2)
				* (a2 - b2)));

		if (dist != dist)
			return 600;
		return dist;
	}

	public float sigFig(float orig, int sigfigs) {
		float easytoread = orig;
		easytoread *= Math.pow(10, sigfigs);
		int round = (int) (easytoread);
		return (float) ((float) round * Math.pow(10, -sigfigs));

	}
}
