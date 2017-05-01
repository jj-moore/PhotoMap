package cps251.edu.wccnet.jh7_jjmoore_photomap;


import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;

class DialogPreferences extends Dialog implements View.OnClickListener {
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

        // SET RADIO BUTTONS WITH CURRENT SETTINGS
        int[] radioButton = {R.id.lite_mode_on, R.id.lite_mode_off, R.id.animate_map_on, R.id.animate_map_off,
                R.id.show_buildings_on, R.id.show_buildings_off};
        for (int index = 0; index < radioButton.length; index++) {
            RadioButton preference = (RadioButton) findViewById(radioButton[index]);
            switch (index) {
                case 0:
                    preference.setChecked(liteMode);
                    break;
                case 1:
                    preference.setChecked(!liteMode);
                    break;
                case 2:
                    preference.setChecked(animateCamera);
                    break;
                case 3:
                    preference.setChecked(!animateCamera);
                    break;
                case 4:
                    preference.setChecked(showBuildings);
                    break;
                case 5:
                    preference.setChecked(!showBuildings);
                    break;
            }
            Button button = (Button) findViewById(R.id.save_preferences);
            button.setOnClickListener(this);
        }
    }

    // RECORD PREFERENCES AND UPDATE VIEW ACCORDINGLY
    public void onClick(View view) {
        int[] radioButton = {R.id.lite_mode_on, R.id.lite_mode_off, R.id.animate_map_on, R.id.animate_map_off,
                R.id.show_buildings_on, R.id.show_buildings_off};
        for (int index = 0; index < radioButton.length; index++) {
            RadioButton preference = (RadioButton) findViewById(radioButton[index]);
            switch (index) {
                case 0:
                    liteMode = preference.isChecked();
                    break;
                case 2:
                    animateCamera = preference.isChecked();
                    break;
                case 4:
                    showBuildings = preference.isChecked();
                    break;
            }
            activityInterface.savePreferences(liteMode, animateCamera, showBuildings);
        }
        this.dismiss();
    }

}
