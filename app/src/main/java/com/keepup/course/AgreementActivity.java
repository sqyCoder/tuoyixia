package com.keepup.course;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

/**
 * 用户协议与隐私说明。
 * 首次启动强制弹出：不同意不能使用；设置里可随时回看（只读）。
 */
public class AgreementActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agreement);

        boolean readOnly = getIntent().getBooleanExtra("readOnly", false);
        Button decline = findViewById(R.id.btnDecline);
        Button accept = findViewById(R.id.btnAccept);
        View back = findViewById(R.id.btnBack);

        if (readOnly) {
            findViewById(R.id.agreeBar).setVisibility(View.GONE);
            back.setVisibility(View.VISIBLE);
            back.setOnClickListener(v -> {
                Ui.tap(v);
                finish();
                Ui.pageOut(this);
            });
            return;
        }

        decline.setOnClickListener(v -> {
            Ui.tap(v);
            Toast.makeText(this, "未同意协议，应用将退出", Toast.LENGTH_SHORT).show();
            getWindow().getDecorView().postDelayed(this::finishAffinity, 700);
        });

        accept.setOnClickListener(v -> {
            Ui.tap(v);
            Prefs.setAgreed(this, true);
            Intent next = Prefs.hasCookies(this)
                    ? new Intent(this, MainActivity.class)
                    : new Intent(this, LoginActivity.class);
            startActivity(next);
            Ui.pageIn(this);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // 首次同意前不允许返回绕过协议
        if (Prefs.agreed(this)) {
            super.onBackPressed();
            Ui.pageOut(this);
        }
    }
}
