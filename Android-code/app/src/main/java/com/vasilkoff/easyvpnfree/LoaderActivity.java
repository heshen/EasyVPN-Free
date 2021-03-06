package com.vasilkoff.easyvpnfree;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.Priority;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.DownloadListener;
import com.androidnetworking.interfaces.DownloadProgressListener;
import com.daimajia.numberprogressbar.NumberProgressBar;
import com.j256.ormlite.dao.Dao;
import com.vasilkoff.easyvpnfree.database.VPNGateServerRecordsHelper;
import com.vasilkoff.easyvpnfree.model.VPNGateServerRecord;

import java.io.FileNotFoundException;
import java.io.FileReader;

import au.com.bytecode.opencsv.CSVReader;


public class LoaderActivity extends AppCompatActivity {

    private NumberProgressBar progressBar;
    private TextView commentsText;
    private Handler updateHandler;

    private final int LOAD_ERROR = 0;
    private final int DOWNLOAD_PROGRESS = 1;
    private final int PARSE_PROGRESS = 2;
    private final int LOADING_SUCCESS = 3;
    private final int SWITCH_TO_RESULT = 4;
    private final String CSV_SERVERS_LIST_URL = "http://www.vpngate.net/api/iphone/";
    private final String CSV_FILE_NAME = "vpngate.csv";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loader);

        progressBar = (NumberProgressBar)findViewById(R.id.number_progress_bar);
        commentsText = (TextView)findViewById(R.id.commentsText);
        progressBar.setMax(100);



        updateHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.arg1) {
                    case LOAD_ERROR: {
                        commentsText.setText(msg.arg2);
                        progressBar.setProgress(100);
                    } break;
                    case DOWNLOAD_PROGRESS: {
                        commentsText.setText(R.string.downloading_csv_text);
                        progressBar.setProgress(msg.arg2);
                    } break;
                    case PARSE_PROGRESS: {
                        commentsText.setText(R.string.parsing_csv_text);
                        progressBar.setProgress(msg.arg2);
                    } break;
                    case LOADING_SUCCESS: {
                        commentsText.setText(R.string.successfully_loaded);
                        progressBar.setProgress(100);
                        Message end = new Message();
                        end.arg1 = SWITCH_TO_RESULT;
                        updateHandler.sendMessageDelayed(end,500);
                    } break;
                    case SWITCH_TO_RESULT: {
                        Intent myIntent = new Intent(LoaderActivity.this, ServersListActivity.class);
                        startActivity(myIntent);
                    }
                }
                return true;
            }
        });
        progressBar.setProgress(0);
        downloadCSVFile();
    }


    private void downloadCSVFile() {
        AndroidNetworking.download(CSV_SERVERS_LIST_URL,getCacheDir().getPath(),CSV_FILE_NAME)
                .setTag("downloadCSV")
                .setPriority(Priority.MEDIUM)
                .build()
                .setDownloadProgressListener(new DownloadProgressListener() {
                    @Override
                    public void onProgress(long bytesDownloaded, long totalBytes) {
                        if(totalBytes<0) {
                            // when we dont know the file size, assume it is 1200000 bytes :)
                            totalBytes = 1200000;
                        }
                        Message msg = new Message();
                        msg.arg1 = DOWNLOAD_PROGRESS;
                        msg.arg2 = (int)((100*bytesDownloaded)/totalBytes);
                        updateHandler.sendMessage(msg);
                    }
                })
                .startDownload(new DownloadListener() {
                    @Override
                    public void onDownloadComplete() {
                        parseCSVFile();
                    }
                    @Override
                    public void onError(ANError error) {
                        Message msg = new Message();
                        msg.arg1 = LOAD_ERROR;
                        msg.arg2 = R.string.network_error;
                        updateHandler.sendMessage(msg);
                    }
                });
    }

    private void parseCSVFile() {
        CSVReader reader = null;
        try {
            reader = new CSVReader(new FileReader(getCacheDir().getPath().concat("/").concat(CSV_FILE_NAME)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Message msg = new Message();
            msg.arg1 = LOAD_ERROR;
            msg.arg2 = R.string.csv_file_error;
            updateHandler.sendMessage(msg);
        }
        if (reader!=null) {
            String[] nextLine;
            try {
                // skip first 2 lines
                reader.readNext();
                reader.readNext();

                // Let's insert everyting into DB
                VPNGateServerRecordsHelper helper = new VPNGateServerRecordsHelper(this);
                Dao<VPNGateServerRecord, Long> record = helper.getDao();
                int counter = 0;
                while ((nextLine = reader.readNext()) != null) {
                    // nextLine[] is an array of values from the line
                    if (nextLine.length>1) {
                        System.out.println(nextLine[0] + nextLine[1] + "etc...");
                        record.create(new VPNGateServerRecord(nextLine));
                        counter++;
                    }
                    Message msg = new Message();
                    msg.arg1 = PARSE_PROGRESS;
                    msg.arg2 = counter;// we know that the server returns 100 records
                    updateHandler.sendMessage(msg);
                }
                Message msg = new Message();
                msg.arg1 = PARSE_PROGRESS;
                msg.arg2 = 100;
                updateHandler.sendMessage(msg);
                record.queryForAll();
                Message end = new Message();
                end.arg1 = LOADING_SUCCESS;
                updateHandler.sendMessageDelayed(end,200);
            } catch (Exception e) {
                e.printStackTrace();
                Message msg = new Message();
                msg.arg1 = LOAD_ERROR;
                msg.arg2 = R.string.csv_file_error_parsing;
                updateHandler.sendMessage(msg);
            }
        }
    }
}
