package cn.edu.sztu.elevatormonitor.utils;

import java.util.Arrays;
import java.util.HashMap;

public class SightseeingTargetFloor {
    private HashMap<Integer, Integer> hashMap;
    private void iniHashMap() {
        // 二进制：0000 0000 0000 0000 1000 0000 从后往前数
        // 1-7 => 15-21
        for (int i = 1; i <= 7; i++) {
            hashMap.put(i, i + 14);
        }
        // 8 => 21
        hashMap.put(8, 21);
        // 9-16 => 7-14
        for (int i = 9; i <= 16; i++) {
            hashMap.put(i, i - 2);
        }
        // 18 => -1
        hashMap.put(18, -1);
        // 19-24 => 1-6
        for (int i = 19; i <= 24; i++) {
            hashMap.put(i, i - 18);
        }
    }

    public SightseeingTargetFloor() {
        hashMap = new HashMap<>();
        iniHashMap();
    }

    public String setTF(String floor) {
        if(floor.equals("000000")) {
            return "无";
        }

        int arr[] = new int[25];
        int copyArr[] = new int[25];

        // 十六进制转二进制字符串
        Integer decimal = Integer.valueOf(floor, 16);
        String binaryString = Integer.toBinaryString(decimal);
        // 二进制：0000 0000 0000 0000 1000 0000
        for (int i = binaryString.length() - 1, j = 1; i >= 0; i--, j++) {
            if (binaryString.charAt(i) == '1') {
                arr[j] = j;
            }
        }
        iniHashMap();
        for (int i = 0, j = 0; i < arr.length; i++, j++) {
            if (arr[i] != 0) {
                copyArr[j] = hashMap.get(arr[i]);
            }
        }
        Arrays.sort(copyArr);
        String ret = "";
        for (int i = 0; i < copyArr.length; i++) {
            if (copyArr[i] != 0) {
                ret += Integer.toString(copyArr[i]).concat("、");
            }
        }
        return ret = ret.substring(0, ret.length() - 1);
    }
}
