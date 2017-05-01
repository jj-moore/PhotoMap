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
    private ActivityInterface activityInterface;

    DialogPreferences(Context context, ActivityInterface activityInterface,
                      boolean liteMode, boolean animateCamera, boolean showBuildings) {
        super(context);
        this.liteMode = liteMode;
        this.animateCamera = animateCamera;
        this.showBuildings = showBuildings;
        this.activityInterface = activityInterface;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Set Preferences");
        setContentView(R.layout.preferences_dialog);

        final CheckBox animate = (CheckBox) findViewById(R.id.check_animate);
        animate.setChecked(animateCamera);
        final CheckBox buildings = (CheckBox) findViewById(R.id.check_buildings);
        buildings.setChecked(showBuildings);
        final CheckBox lite_mode = (CheckBox) findViewById(R.id.check_lite_mode);
        lite_mode.setChecked(liteMode);

        Button button = (Button) findViewById(R.id.save_preferences);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                activityInterface.savePreferences(lite_mode.isChecked(), animate.isChecked(), buildings.isChecked());
                DialogPreferences.this.dismiss();
            }
        });
    }

}
