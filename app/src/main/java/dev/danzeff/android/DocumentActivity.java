package dev.danzeff.android;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import java.nio.charset.StandardCharsets;

/** Bundled documentation is rendered natively, with no WebView or network permission. */
public final class DocumentActivity extends ThemedActivity {
    public static void open(Context context, String title, String asset) {
        context.startActivity(new Intent(context, DocumentActivity.class)
                .putExtra("title", title).putExtra("asset", asset));
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(KeyboardAppearance.background(this));
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(getIntent().getStringExtra("title")); toolbar.setTitleTextColor(AppUi.ink(this));
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationIconTint(AppUi.ink(this)); toolbar.setNavigationContentDescription("Back");
        toolbar.setNavigationOnClickListener(v -> finish()); layout.addView(toolbar);
        ScrollView scroll = new ScrollView(this);
        TextView text = new TextView(this); text.setTextColor(AppUi.ink(this)); text.setTextSize(16);
        text.setTypeface(getResources().getFont(R.font.google_sans_regular));
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad, pad, pad); text.setTextIsSelectable(true);
        scroll.addView(text); layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(layout);
        String asset = getIntent().getStringExtra("asset");
        if (!"licenses/credits.md".equals(asset) && !"docs/help.md".equals(asset)) { finish(); return; }
        try (java.io.InputStream input = getAssets().open(asset)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            Markwon.builder(this).usePlugin(TablePlugin.create(this)).build()
                    .setMarkdown(text, new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (java.io.IOException error) { text.setText("This document could not be loaded."); }
    }
}
