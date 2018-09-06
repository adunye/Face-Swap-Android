package com.tunaemre.opencv.faceswap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.res.ResourcesCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.tunaemre.opencv.faceswap.app.ExtendedCompatActivity;
import com.tunaemre.opencv.faceswap.constant.Constant;

import net.dlib.IFaceSwapper;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.facedetect.IDetectionBasedTracker;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

public class FaceSwapImagesActivity extends ExtendedCompatActivity
{
    private static final String TAG = "FaceSwapImagesActivity";

    static
    {
        if (!OpenCVLoader.initDebug())
        {
            // Handle initialization error
        }
    }

    private boolean mIsRuntimeFilesLoaded = false;
    private boolean mIsTakePictureRequested= false;

    private Mat mRgba, mGray, mTemp;

    private IDetectionBasedTracker mNativeDetector;

    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;

//    private ExtendedCameraView mOpenCvCameraView;

    private File mLastCapturedPhoto = null;

    private AsyncTask<Void, Void, Boolean> mRuntimeFilesLoaderTask = null;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this)
    {
        @Override
        public void onManagerConnected(int status)
        {
            if (status == LoaderCallbackInterface.SUCCESS)
            {
                Log.i("FaceSwapper", "OpenCV loaded successfully");

                new FaceSwapImagesActivity.ExecuteRuntimeFilesLoader().Run();
            }
            else
            {
                super.onManagerConnected(status);
            }
        }
    };

    private CameraBridgeViewBase.CvCameraViewListener2 mCameraViewListener = new CameraBridgeViewBase.CvCameraViewListener2()
    {
        @Override
        public void onCameraViewStopped()
        {
            mGray.release();
            mRgba.release();
        }

        @Override
        public void onCameraViewStarted(int width, int height)
        {
            mGray = new Mat();
            mRgba = new Mat();
        }

        @Override
        public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame)
        {
            mGray = inputFrame.gray();
            mRgba = inputFrame.rgba();


            if (mAbsoluteFaceSize == 0)
            {
                int height = mGray.rows();
                if (Math.round(height * mRelativeFaceSize) > 0)
                {
                    mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
                }
                mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
            }

            MatOfRect faces = new MatOfRect();

            if (mNativeDetector != null) mNativeDetector.detect(mGray, faces);

            Rect[] facesArray = faces.toArray();

            Log.e("OpenCVFaceDetect", "FaceCount:" + facesArray.length);

            if (facesArray.length == 2)
            {

                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        findViewById(R.id.imgFaceSwapMask).setVisibility(View.GONE);
                        findViewById(R.id.imgFaceSwapMaskLeftBorder).setVisibility(View.GONE);
                        findViewById(R.id.imgFaceSwapMaskRightBorder).setVisibility(View.GONE);
                        findViewById(R.id.imgFaceSwapMaskTopBorder).setVisibility(View.GONE);
                        findViewById(R.id.imgFaceSwapMaskBottomBorder).setVisibility(View.GONE);
                    }
                });
            }
            else
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        findViewById(R.id.imgFaceSwapMask).setVisibility(View.VISIBLE);
                        findViewById(R.id.imgFaceSwapMaskLeftBorder).setVisibility(View.VISIBLE);
                        findViewById(R.id.imgFaceSwapMaskRightBorder).setVisibility(View.VISIBLE);
                        findViewById(R.id.imgFaceSwapMaskTopBorder).setVisibility(View.VISIBLE);
                        findViewById(R.id.imgFaceSwapMaskBottomBorder).setVisibility(View.VISIBLE);
                    }
                });
            }

            IFaceSwapper.swapFaces(mRgba.getNativeObjAddr(), faces.getNativeObjAddr());

            if (mIsTakePictureRequested)
            {
                mTemp = mRgba;
                mIsTakePictureRequested = false;

//                runTakePictureTask();
            }

            return mRgba;
        }
    };
    private ImageView actorImageView;
    private ImageView userImageView;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_face_swap_images);

        if (!OpenCVLoader.initDebug())
        {
            Log.d("OpenCVFaceDetect", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        }
        else
        {
            Log.d("OpenCVFaceDetect", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();

//        if (mOpenCvCameraView != null) mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();

//        if (mOpenCvCameraView != null && mIsRuntimeFilesLoaded) mOpenCvCameraView.enableView();
    }

    @Override
    public void onDestroy()
    {
        if (mRuntimeFilesLoaderTask != null)
        {
            mRuntimeFilesLoaderTask.cancel(true);
            mRuntimeFilesLoaderTask = null;
        }

        super.onDestroy();

//        if (mOpenCvCameraView != null) mOpenCvCameraView.disableView();
    }

    @Override
    protected void prepareActivity()
    {
//        mOpenCvCameraView = (ExtendedCameraView) findViewById(R.id.cameraViewOpenCV);
//        mOpenCvCameraView.setCvCameraViewListener(mCameraViewListener);

//        findViewById(R.id.btnFaceSwapSwitchCamera).setOnClickListener(new View.OnClickListener()
//        {
//            @Override
//            public void onClick(View view)
//            {
//                int currentCam = mOpenCvCameraView.switchCamera();
//
//                if (currentCam == JavaCameraView.CAMERA_ID_FRONT) ((ImageButton) view).setImageResource(R.drawable.ic_camera_rear_white_36dp);
//                else
//                    ((ImageButton) view).setImageResource(R.drawable.ic_camera_front_white_36dp);
//            }
//        });

        actorImageView = findViewById(R.id.actorImage);
        userImageView = findViewById(R.id.userImage);

        findViewById(R.id.swap).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                swapFaces();
            }
        });
    }

    private void swapFaces() {
//        IFaceSwapper.swapFaces(mRgba.getNativeObjAddr(), faces.getNativeObjAddr());
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()+"/alex.jpg";
        Bitmap image = loadImage(path);
        getFace(image);
    }

    private void getFace(Bitmap person) {
        Mat mat = new Mat (person.getWidth(), person.getHeight(), CvType.CV_8U);

        Utils.bitmapToMat(person, mat);
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);
        MatOfRect faces = new MatOfRect();

        int height = mat.rows();
        if (Math.round(height * mRelativeFaceSize) > 0)
        {
            mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
        }
        mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);

        if (mNativeDetector != null) mNativeDetector.detect(mat, faces);

        Rect[] facesArray = faces.toArray();
        Log.d(TAG, "faces detected: " + facesArray.length);
    }

    private void getFace(int resourceId) {
        Mat mat = new Mat();
        try {
            mat = Utils.loadResource(this, resourceId);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MatOfRect faces = new MatOfRect();

        int height = mat.rows();
        if (Math.round(height * mRelativeFaceSize) > 0)
        {
            mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
        }
        mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);

        if (mNativeDetector != null) mNativeDetector.detect(mat, faces);

        Rect[] facesArray = faces.toArray();
        Log.d(TAG, "faces detected: " + facesArray.length);

    }

    private Bitmap loadImage(String path) {
        Bitmap image = BitmapFactory.decodeFile(path);
        return image;
    }

    private File takePicture(Mat cameraFrame)
    {
        try
        {
            Bitmap bitmap = Bitmap.createBitmap(cameraFrame.cols(), cameraFrame.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(cameraFrame, bitmap);

            File folderPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/FaceSwap");
            if (!folderPath.exists() && !folderPath.mkdirs()) return null;;

            File filePath = new File(folderPath.getAbsoluteFile(), new Date().getTime() + ".jpg");
            FileOutputStream outputStream = new FileOutputStream(filePath);

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);

            outputStream.flush();
            outputStream.close();

            return filePath;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private class ExecuteRuntimeFilesLoader
    {
        @SuppressLint("NewApi")
        private void Run()
        {
            mRuntimeFilesLoaderTask = new FaceSwapImagesActivity.ExecuteRuntimeFilesLoader.BaseAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }

        private class BaseAsyncTask extends AsyncTask<Void, Void, Boolean>
        {
            protected Boolean doInBackground(Void... args)
            {
                try
                {
                    String poseModelPath = new File(FaceSwapImagesActivity.this.getDir(Constant.FaceLandmarksDownloadPath, Context.MODE_PRIVATE), Constant.FaceLandmarksFileName).getAbsolutePath();

                    if (!IFaceSwapper.loadPoseModel(poseModelPath)) return false;

                    Log.i("FaceSwapper", "PoseModel loaded successfully");

                    String cascadePath = IDetectionBasedTracker.copyCascade(getApplicationContext(), R.raw.haarcascade_frontalface_alt2, "haarcascade_frontalface_alt2.xml");

                    if (cascadePath == null) return false;

                    Log.i("FaceSwapper", "Cascade copied successfully");

                    mNativeDetector = new IDetectionBasedTracker(cascadePath, 0);
                    mNativeDetector.start();

                    return true;
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    return false;
                }
            }

            protected void onPostExecute(Boolean result)
            {
                mRuntimeFilesLoaderTask = null;

                mIsRuntimeFilesLoaded = result;

//                if (result && mOpenCvCameraView != null) mOpenCvCameraView.enableView();
//                else
//                    Toast.makeText(FaceSwapImagesActivity.this, "Runtime error.", Toast.LENGTH_SHORT).show();
//
//                findViewById(R.id.progressBar).setVisibility(View.GONE);
            }
        }
    }
}
