package com.w9jds.glassshare;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.widget.CardScrollView;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.gson.JsonObject;
import com.w9jds.glassshare.Adapters.csaAdapter;
import com.w9jds.glassshare.Classes.StorageApplication;
import com.w9jds.glassshare.Classes.StorageService;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

@SuppressLint("DefaultLocale")
public class MainActivity extends Activity
{
    private final String CAMERA_IMAGE_BUCKET_NAME = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() + "/Camera";
    private final String CAMERA_IMAGE_BUCKET_ID = getBucketId(CAMERA_IMAGE_BUCKET_NAME);

    private ConnectivityManager mcmCon;
    private AudioManager maManager;

    //create member variables for Azure
    private StorageService mStorageService;

    //create member variables for google drive
    private Drive mdService;
    private GoogleAccountCredential mgacCredential;

    //custom adapter
    private csaAdapter mcvAdapter;
    //list for all the paths of the images on google glass
    private ArrayList<String> mlsPaths = new ArrayList<String>();
    //variable for the last selected index
    private int miPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mcmCon = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        maManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (mcmCon.getActiveNetworkInfo().isConnected())
        {
            StorageApplication myApp = (StorageApplication) getApplication();
            mStorageService = myApp.getStorageService();
        }

        //get all the images from the camera folder (paths)
        mlsPaths = getCameraImages();
        //sort the paths of pictures
        sortPaths();

        CreatePictureView();
    }

    @Override
    protected void onStop()
    {
        Log.d("GlassShare", "Closing Application");
        finish();
        super.onStop();
    }

    private void CreatePictureView()
    {
        //create a new card scroll viewer for this context
        CardScrollView csvCardsView = new CardScrollView(this);
        //create a new adapter for the scroll viewer
        mcvAdapter = new csaAdapter(this, mlsPaths);
        //set this adapter as the adapter for the scroll viewer
        csvCardsView.setAdapter(mcvAdapter);
        //activate this scroll viewer
        csvCardsView.activate();
        //add a listener to the scroll viewer that is fired when an item is clicked
        csvCardsView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                maManager.playSoundEffect(Sounds.TAP);


                //save the card index that was selected
                miPosition = position;
                //open the menu
                openOptionsMenu();
            }
        });

        //set the view of this activity
        setContentView(csvCardsView);
    }

    /***
     * Register for broadcasts
     */
    @Override
    protected void onResume()
    {
        IntentFilter filter = new IntentFilter();
        filter.addAction("blob.created");
        registerReceiver(receiver, filter);
        super.onResume();
    }

    /***
     * Unregister for broadcasts
     */
    @Override
    protected void onPause()
    {
        unregisterReceiver(receiver);
        super.onPause();
    }

    /***
     * Sort the file paths so that the images are in order from most resent first
     */
    private void sortPaths()
    {
        java.io.File[] fPics = new java.io.File[mlsPaths.size()];

        for (int i = 0; i < mlsPaths.size(); i++)
            fPics[i] = new java.io.File(mlsPaths.get(i));

        mlsPaths.clear();

        Arrays.sort(fPics, new Comparator<java.io.File>()
        {
            @Override
            public int compare(java.io.File o1, java.io.File o2)
            {
                return Long.valueOf(o1.lastModified()).compareTo(o2.lastModified());
            }
        });

        for (int i = fPics.length - 1; i >= 0; i--)
            mlsPaths.add(fPics[i].getAbsolutePath());
    }

    public String getBucketId(String path)
    {
        return String.valueOf(path.toLowerCase().hashCode());
    }

    /***
     * Get all the image file paths on this device (from the camera folder)
     * @return an arraylist of all the file paths
     */
    public ArrayList<String> getCameraImages()
    {
        final String[] projection = {MediaStore.Images.Media.DATA};
        final String selection = MediaStore.Images.Media.BUCKET_ID + " = ?";
        final String[] selectionArgs = { CAMERA_IMAGE_BUCKET_ID };
        final Cursor cursor = this.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null);
        ArrayList<String> result = new ArrayList<String>(cursor.getCount());

        if (cursor.moveToFirst())
        {
            final int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            do
            {
                final String data = cursor.getString(dataColumn);
                result.add(data);
            } while (cursor.moveToNext());
        }

        cursor.close();
        return result;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem iItem)
    {
        switch (iItem.getItemId())
        {
            case R.id.delete_menu_item:
                //set the text as deleting
                setContentView(R.layout.menu_layout);
                ((ImageView)findViewById(R.id.icon)).setImageResource(R.drawable.ic_delete_50);
                ((TextView)findViewById(R.id.label)).setText("Deleting");

                ProgressBar pbProgress = (ProgressBar)findViewById(R.id.progress);
                pbProgress.setIndeterminate(false);

//                for (int i = 0; i <= 100; i++)
//                {
//
//                    pbProgress.setProgress(i);
//
//                }

                //pull the file from the path of the selected item
                java.io.File fPic = new java.io.File(mlsPaths.get(miPosition));
                //delete the image
                fPic.delete();
                //refresh the folder
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://" + Environment.getExternalStorageDirectory())));
                //remove the selected item from the list of images
                mlsPaths.remove(miPosition);
                //let the adapter know that the list of images has changed
                mcvAdapter.notifyDataSetChanged();
                //handled


                setContentView(R.layout.menu_layout);
                ((ImageView)findViewById(R.id.icon)).setImageResource(R.drawable.ic_done_50);
                ((TextView)findViewById(R.id.label)).setText("Deleted");
                findViewById(R.id.progress).setVisibility(View.GONE);

                maManager.playSoundEffect(Sounds.SUCCESS);

                new Handler().postDelayed(new Runnable()
                {
                    public void run()
                    {
                        CreatePictureView();
                    }
                }, 1000);

                return true;
//            case R.id.upload_menu_item:
//
//                if (mcmCon.getActiveNetworkInfo().isConnected())
//                {
//                    //get google account credentials and store to member variable
//                    mgacCredential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList(DriveScopes.DRIVE));
//                    //get a list of all the accounts on the device
//                    Account[] myAccounts = AccountManager.get(this).getAccounts();
//                    //for each account
//                    for (int i = 0; i < myAccounts.length; i++) {
//                        //if the account type is google
//                        if (myAccounts[i].type.equals("com.google"))
//                            //set this as the selected Account
//                            mgacCredential.setSelectedAccountName(myAccounts[i].name);
//                    }
//                    //get the drive service
//                    mdService = getDriveService(mgacCredential);
//                    //save the selected item to google drive
//                    saveFileToDrive(mlsPaths.get(miPosition));
//                }
//
//                return true;

            case R.id.uploadphone_menu_item:

                if (mcmCon.getActiveNetworkInfo().isConnected())
                {
                    setContentView(R.layout.menu_layout);
                    ((ImageView)findViewById(R.id.icon)).setImageResource(R.drawable.ic_mobile_phone_50);
                    ((TextView)findViewById(R.id.label)).setText("Uploading");

                    String sContainer = "";
                    String[] saImage = mlsPaths.get(miPosition).split("/|\\.");

                    Account[] myAccounts = AccountManager.get(this).getAccounts();
                    //for each account
                    for (int i = 0; i < myAccounts.length; i++)
                    {
                        //if the account type is google
                        if (myAccounts[i].type.equals("com.google"))
                        {
                            //set this as the selected Account
                            String[] saAccount = myAccounts[i].name.split("@|\\.");
                            sContainer = saAccount[0] + saAccount[1] + saAccount[2];
                        }
                    }

                    mStorageService.addContainer(sContainer, false);
                    mStorageService.getSasForNewBlob(sContainer, saImage[saImage.length - 2]);
                }
                else
                    maManager.playSoundEffect(Sounds.DISALLOWED);
                return true;

            default:
                return super.onOptionsItemSelected(iItem);
        }
    }

    /***
     * Broadcast receiver handles blobs being loaded or a new blob being created
     */
    private BroadcastReceiver receiver = new BroadcastReceiver()
    {
        public void onReceive(Context context, android.content.Intent intent)
        {
            String intentAction = intent.getAction();

            if (intentAction.equals("blob.created"))
            {
                //If a blob has been created, upload the image
                JsonObject blob = mStorageService.getLoadedBlob();
                String sasUrl = blob.getAsJsonPrimitive("sasUrl").toString();
                (new ImageUploaderTask(sasUrl, miPosition, mlsPaths)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);;

            }
        }
    };


    public class ImageUploaderTask extends AsyncTask<Void, Void, Boolean>
    {
        private String mUrl;
        private ArrayList<String> mlsPaths;
        private int miPosition;

        public ImageUploaderTask(String url, int iPosition, ArrayList<String> lsPath)
        {
            mUrl = url;
            miPosition = iPosition;
            mlsPaths = lsPath;
        }

        @Override
        protected Boolean doInBackground(Void... params)
        {
            try
            {
                java.io.File fImage = new java.io.File(mlsPaths.get(miPosition));
                FileInputStream fisStream = new FileInputStream(fImage);
                byte[] byteArray = new byte[(int)fImage.length()];
                fisStream.read(byteArray);

                // Post our image data (byte array) to the server
                Log.d("ImageUpload", "Byte Array Finished " + byteArray.length);
                URL url = new URL(mUrl.replace("\"", ""));
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setDoOutput(true);
                urlConnection.setRequestMethod("PUT");
                urlConnection.addRequestProperty("Content-Type", "image/jpeg");
                urlConnection.setRequestProperty("Content-Length", ""+ byteArray.length);
                Log.d("ImageUpload", "Connection Created");
                // Write image data to server
                DataOutputStream wr = new DataOutputStream(urlConnection.getOutputStream());
                wr.write(byteArray);
                Log.d("ImageUpload", "Writing of byte array finished.");
                wr.flush();
                wr.close();
                Log.d("ImageUpload", "DataOutputStream Closed");
                int response = urlConnection.getResponseCode();
                Log.d("ImageUpload", "Got response code " + response);
                urlConnection.disconnect();
                Log.d("ImageUpload", "Disconnected Connection");
                //If we successfully uploaded, return true
                if (response == 201 && urlConnection.getResponseMessage().equals("Created"))
                {
                    Log.d("ImageUpload", "Image uploaded");
                    return true;
                }

            }

            catch (Exception ex)
            {
                Log.e("GlassShareImageUploadTask", ex.getMessage());
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean uploaded)
        {
            if (uploaded)
            {
                setContentView(R.layout.menu_layout);
                ((ImageView)findViewById(R.id.icon)).setImageResource(R.drawable.ic_done_50);
                ((TextView)findViewById(R.id.label)).setText("Uploaded");
                findViewById(R.id.progress).setVisibility(View.GONE);

                maManager.playSoundEffect(Sounds.SUCCESS);
//                mWakeLock.release();


            }

            new Handler().postDelayed(new Runnable()
            {
                public void run()
                {
                    CreatePictureView();
                }
            }, 1000);
        }
    }

    private void saveFileToDrive(String sPath)
    {
        final String msPath = sPath;

        Thread t = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    // File's binary content
                    java.io.File fImage = new java.io.File(msPath);
                    FileContent fcContent = new FileContent("image/jpeg", fImage);

                    // File's metadata.
                    File gdfBody = new File();
                    gdfBody.setTitle(fImage.getName());
                    gdfBody.setMimeType("image/jpeg");

                    File gdfFile = mdService.files().insert(gdfBody, fcContent).execute();
                    if (gdfFile != null)
                    {
                        Log.d("GlassShareUploadTask", "Uploaded");
                    }

                }
                catch (UserRecoverableAuthIOException e) {
                    Log.d("GlassShareUploadTask", e.toString());
//                    startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
                }
                catch (IOException e) {
                    Log.d("GlassShareUploadTask", e.toString());
//                    e.printStackTrace();
                }
                catch (Exception e) {
                    Log.d("GlassShareUploadTask", e.toString());
                }
            }
        });
        t.start();

    }

    private Drive getDriveService(GoogleAccountCredential credential)
    {
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential).build();
    }


}



