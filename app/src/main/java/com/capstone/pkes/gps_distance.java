package com.capstone.pkes;

public class gps_distance {

    //haversine formula for distance takes in latitudes and longitudes of car and key and gives distance
    static double calculate_distance(double latitude_key, double longitude_key,double latitude_car,double longitude_car){
        double  diff_in_longitude = longitude_key - longitude_car ,
                diff_in_latitude = latitude_key - latitude_car ;//random 2nd distance instead of car latitude and longitude
        double a = Math.pow(
                    Math.sin(diff_in_latitude / 2), 2)
                    + Math.cos(11)
                    * Math.cos(latitude_key)
                    * Math.pow(Math.sin(diff_in_longitude / 2)
                ,2);
        return 2 * Math.asin(Math.sqrt(a)) * 6371;// 6371 to convert distance to kms
    }
}
