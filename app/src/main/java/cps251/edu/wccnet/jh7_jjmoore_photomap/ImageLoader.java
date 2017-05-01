package cps251.edu.wccnet.jh7_jjmoore_photomap;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.ImageView;

import java.io.FileDescriptor;

import static android.graphics.BitmapFactory.decodeFileDescriptor;

class ImageLoader extends AsyncTask<String, Integer, Bitmap> {
    private ImageView imageView;
    private Activity activity;
    private ActivityInterface activityInterface;
    private String uriString;
    private BitmapFactory.Options options;
    private FileDescriptor fileDescriptor;
    private int maxImageWidth, maxImageHeight;
    private boolean displayImage;

    ImageLoader(Activity activity, ActivityInterface activityInterface, ImageView imageView, boolean displayImage) {
        this.activity = activity;
        this.activityInterface = activityInterface;
        this.imageView = imageView;
        this.displayImage = displayImage;
    }

    protected Bitmap doInBackground(String... strings) {
        try {
            uriString = strings[0];
            Uri uri = Uri.parse(uriString);
            fileDescriptor = activity.getContentResolver().openFileDescriptor(uri, "r").getFileDescriptor();
            options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            this.setMaxDims();
            decodeFileDescriptor(fileDescriptor, null, options);
            int sampleSize = this.calcSampleSize(options);

            return this.getScaledImage(sampleSize);
        } catch (Exception e) {
            Log.d("Jeremy", "onActivityResult: FileNotFoundException: " + e.toString());
            return null;
        }
    }

    // PHOTO WILL TAKE UP 100% OF WIDTH AND 50% OF HEIGHT IN PORTRAIT MODE (REVERSE IN LANDSCAPE).
    private void setMaxDims() {
        final float imageScale = 0.5F;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int displayWidth = displayMetrics.widthPixels;
        int displayHeight = displayMetrics.heightPixels;

        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            maxImageWidth = displayWidth;
            maxImageHeight = (int) (displayHeight * imageScale);
        } else {
            maxImageWidth = (int) (displayWidth * imageScale);
            maxImageHeight = displayHeight;
        }
    }

    // CALCULATE THE SAMPLE SIZE TO FIT THE MAX DIMENSIONS AND USE THE NEXT SMALLEST SAMPLE SIZE
    private int calcSampleSize(BitmapFactory.Options options) {
        int originalImageWidth = options.outWidth;
        int originalImageHeight = options.outHeight;
        int sampleSize = 1;

        while (originalImageWidth > maxImageWidth || originalImageHeight > maxImageHeight) {
            sampleSize *= 2;
            originalImageWidth /= 2;
            originalImageHeight /= 2;
        }
        sampleSize /= 2;
        return sampleSize;
    }

    // FIRST SHRINK THE IMAGE USING SAMPLE SIZE AND THEN SCALE TO FIT.
    private Bitmap getScaledImage(int sampleSize) {
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        Bitmap temp = decodeFileDescriptor(fileDescriptor, null, options);

        float widthScaling = (float) maxImageWidth / temp.getWidth();
        float heightScaling = (float) maxImageHeight / temp.getHeight();
        float scale = widthScaling > heightScaling ? heightScaling : widthScaling;
        int finalWidth = (int) (temp.getWidth() * scale);
        int finalHeight = (int) (temp.getHeight() * scale);

        Bitmap scaled = Bitmap.createScaledBitmap(temp, finalWidth, finalHeight, false);
        temp.recycle();
        return scaled;
    }

    // ADD IMAGE TO CACHE. DISPLAY THE IMAGE IF REQUIRED. REMOVE RECORD IF IMAGE NOT FOUND.
    protected void onPostExecute(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            activityInterface.addImageToCache(uriString, bitmap);
            if (displayImage) {
                imageView.setImageBitmap(bitmap);
            }
        } else {
            activityInterface.removeDeletedRecord();
        }
    }
}
