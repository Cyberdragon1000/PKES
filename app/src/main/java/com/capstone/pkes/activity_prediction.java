package com.capstone.pkes;

import android.annotation.SuppressLint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class activity_prediction {
    static final int N_SAMPLES = 200;
    static List<Float> x;//list of x coordinates got per change
    static List<Float> y;//list of y coordinates got per change
    static List<Float> z;//list of z coordinates got per change
    static TensorFlowClassifier classifier;

    //conv list to float array
    static float[] toFloatArray(List<Float> list) {
        int i = 0;
        float[] array = new float[list.size()];

        for (Float f : list) {
            array[i++] = (f != null ? f : Float.NaN);
        }
        return array;
    }

    //round off data to two decimal place precision
    static float round(float d) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

    //predicts activity using xyz array samples and returns probability array of predictions
    @SuppressLint("SetTextI18n")
    static float[] activityPrediction() {
        float[] results=null;
        if (x.size() >= N_SAMPLES && y.size() >= N_SAMPLES && z.size() >= N_SAMPLES) {
            List<Float> data = new ArrayList<>();
            data.addAll(x);
            data.addAll(y);
            data.addAll(z);
        //array of "Downstairs", "Jogging", "Sitting", "Standing", "Upstairs", "Walking"
            results = classifier.predictProbabilities(toFloatArray(data));
            //empty samples
            x.clear();
            y.clear();
            z.clear();
        }

        return  results;

    }
}
