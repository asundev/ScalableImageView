package com.asun.scalableimageview;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.asun.library.ScalableImageView;

public class MainActivity extends AppCompatActivity {

    ScalableImageView mScalableImageView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mScalableImageView = findViewById(R.id.view);
    }
}
