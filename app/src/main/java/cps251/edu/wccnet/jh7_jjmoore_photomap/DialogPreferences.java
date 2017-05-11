package cps251.edu.wccnet.jh7_jjmoore_photomap;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

class DialogPreferences extends Dialog {
    private boolean liteMode;
    private boolean animateCamera;
    private boolean showBuildings;
    private boolean deleteCache;
    private ActivityInterface activityInterface;

    DialogPreferences(Context context, ActivityInterface activityInterface,
                      boolean liteMode, boolean animateCamera, boolean showBuildings, boolean deleteCache) {
        super(context);
        this.liteMode = liteMode;
        this.animateCamera = animateCamera;
        this.showBuildings = showBuildings;
        this.deleteCache = deleteCache;
        this.activityInterface = activityInterface;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Set Preferences");
        setContentView(R.layout.dialog_preferences);

        final CheckBox animate = (CheckBox) findViewById(R.id.check_animate);
        animate.setChecked(animateCamera);
        final CheckBox buildings = (CheckBox) findViewById(R.id.check_buildings);
        buildings.setChecked(showBuildings);
        final CheckBox lite_mode = (CheckBox) findViewById(R.id.check_lite_mode);
        lite_mode.setChecked(liteMode);
        final CheckBox delete_cache = (CheckBox) findViewById(R.id.check_delete_cache);
        delete_cache.setChecked(deleteCache);

        final Button button = (Button) findViewById(R.id.save_preferences);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                activityInterface.savePreferences(lite_mode.isChecked(),
                        animate.isChecked(), buildings.isChecked(), delete_cache.isChecked());
                DialogPreferences.this.dismiss();
            }
        });
    }

}
