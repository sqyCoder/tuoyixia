package com.keepup.course;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.work.WorkManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends Activity {
    private CompoundButton cb24, cb2, cb0, cbShowDone, cbAutoSubmit;
    private RadioButton rb15, rb30, rb60, rbFree, rbCustom;
    private EditText etAiBase, etAiKey, etAiModel;
    private AuroraView aurora;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        aurora = findViewById(R.id.aurora);
        findViewById(R.id.btnBackSettings).setOnClickListener(v -> {
            Ui.tap(v);
            finish();
            Ui.pageOut(this);
        });
        Ui.press(findViewById(R.id.btnSave));
        cb24 = findViewById(R.id.cb24);
        cb2 = findViewById(R.id.cb2);
        cb0 = findViewById(R.id.cb0);
        cbShowDone = findViewById(R.id.cbShowDone);
        rb15 = findViewById(R.id.rb15);
        rb30 = findViewById(R.id.rb30);
        rb60 = findViewById(R.id.rb60);
        etAiBase = findViewById(R.id.etAiBase);
        etAiKey = findViewById(R.id.etAiKey);
        etAiModel = findViewById(R.id.etAiModel);
        cbAutoSubmit = findViewById(R.id.cbAutoSubmit);
        rbFree = findViewById(R.id.rbFree);
        rbCustom = findViewById(R.id.rbCustom);

        boolean free = Prefs.aiFree(this);
        rbFree.setChecked(free);
        rbCustom.setChecked(!free);
        rbFree.setOnCheckedChangeListener((b, checked) -> syncAiFields());
        rbCustom.setOnCheckedChangeListener((b, checked) -> syncAiFields());

        etAiBase.setText(Prefs.aiBase(this));
        etAiKey.setText(Prefs.aiKey(this));
        etAiModel.setText(Prefs.aiModel(this));
        cbAutoSubmit.setChecked(Prefs.autoSubmit(this));
        syncAiFields();

        cb24.setChecked(Prefs.lead24(this));
        cb2.setChecked(Prefs.lead2(this));
        cb0.setChecked(Prefs.lead0(this));
        cbShowDone.setChecked(Prefs.showDone(this));
        int itv = Prefs.interval(this);
        if (itv <= 15) rb15.setChecked(true);
        else if (itv >= 60) rb60.setChecked(true);
        else rb30.setChecked(true);

        ((Button) findViewById(R.id.btnSave)).setOnClickListener(v -> save());
        findViewById(R.id.btnClear).setOnClickListener(v -> confirmClear());
        findViewById(R.id.btnAgreement).setOnClickListener(v -> {
            Intent i = new Intent(this, AgreementActivity.class);
            i.putExtra("readOnly", true);
            startActivity(i);
            Ui.pageIn(this);
        });
        findViewById(R.id.btnTestAi).setOnClickListener(v -> testAi());
    }

    private void syncAiFields() {
        boolean custom = rbCustom.isChecked();
        etAiBase.setEnabled(custom);
        etAiKey.setEnabled(custom);
        etAiModel.setEnabled(custom);
        float alpha = custom ? 1f : 0.45f;
        etAiBase.setAlpha(alpha);
        etAiKey.setAlpha(alpha);
        etAiModel.setAlpha(alpha);
    }

    private void saveAi() {
        Prefs.saveAi(this,
                rbCustom.isChecked() ? "custom" : "free",
                etAiBase.getText().toString(),
                etAiKey.getText().toString(),
                etAiModel.getText().toString(),
                cbAutoSubmit.isChecked());
    }

    private void testAi() {
        saveAi();
        AiClient.Config cfg = AiClient.config(this);
        if (!cfg.ready()) {
            Toast.makeText(this, "接口地址、Key、模型名都要填", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在测试…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            String msg;
            try {
                String r = AiClient.chat(this, "只回一个字", "回复 ok", false);
                msg = "连接成功：" + (r.length() > 40 ? r.substring(0, 40) : r);
            } catch (Exception e) {
                msg = "连接失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            final String m = msg;
            runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
        });
    }

    private void save() {
        int interval = rb15.isChecked() ? 15 : (rb60.isChecked() ? 60 : 30);
        Prefs.saveSettings(this, cb24.isChecked(), cb2.isChecked(), cb0.isChecked(), cbShowDone.isChecked(), interval);
        saveAi();
        SyncWorker.schedulePeriodic(this);
        Reminders.reschedule(this, Prefs.tasks(this));
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (aurora != null) aurora.resume();
    }

    @Override
    protected void onPause() {
        if (aurora != null) aurora.pause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Ui.pageOut(this);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_account)
                .setMessage("将清除本机保存的登录状态、作业记录和提醒，学习通账号本身不受影响。")
                .setPositiveButton("清除", (d, w) -> clear())
                .setNegativeButton("取消", null)
                .show();
    }

    private void clear() {
        Prefs.clearAccount(this);
        Reminders.cancelAll(this);
        WorkManager.getInstance(this).cancelUniqueWork("sync_once");
        WorkManager.getInstance(this).cancelUniqueWork("sync");
        Http.reset();
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(null);
        cm.flush();
        Toast.makeText(this, R.string.clear_done, Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
