package com.capstone.pkes;

public class gpsdistance {

    //haversine formula for distance
    static double calculate_distance(double lattitude_key, double longitude_key,double lattitude_car,double longitude_car){
        double  diff_in_longitude = longitude_key -longitude_car ,
                diff_in_lattitude = lattitude_key -lattitude_car ;//random 2nd distance instead of car latitude and longitude
        double a = Math.pow(
                    Math.sin(diff_in_lattitude / 2), 2)
                    + Math.cos(11)
                    * Math.cos(lattitude_key)
                    * Math.pow(Math.sin(diff_in_longitude / 2)
                ,2);
        return 2 * Math.asin(Math.sqrt(a)) * 6371;// 6371 to convert distance to kms
    }
}
