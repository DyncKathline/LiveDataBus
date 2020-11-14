package com.kathline.livedatabus;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.kathline.library.LiveDataBus;

public class SecondActivity extends AppCompatActivity {

    private TextView text;
    private Button send;
    private TextView textOnce;
    private Button sendOnce;
    private Button open;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);
        text = findViewById(R.id.text);
        send = findViewById(R.id.send);
        textOnce = findViewById(R.id.textOnce);
        sendOnce = findViewById(R.id.sendOnce);
        open = findViewById(R.id.open);
        LiveDataBus.get().<String>subscribe("event1").observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                Log.i("kath", s);
                text.setText(s);
            }
        });
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LiveDataBus.get().postValue("event1", "我是SecondActivity数据");
            }
        });
        LiveDataBus.get().<String>subscribe("event2").observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                Log.i("kath", s);
                textOnce.setText(s);
            }
        });
        sendOnce.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LiveDataBus.get().postValue("event2", "我是SecondActivity数据", true);
            }
        });
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });
    }
}
