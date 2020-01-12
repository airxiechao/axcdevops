package com.airxiechao.axcdevops.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CmdUtil {

    public static String execute(String cmd) throws Exception {
        Process process = Runtime.getRuntime().exec("cmd /C " + cmd);

        List<String> output = new ArrayList<>();
        try(BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), "GBK"))
        ){

            String line;
            while ((line = outReader.readLine()) != null) {
                output.add(line);
            }

            while ((line = errorReader.readLine()) != null) {
                output.add(line);
            }

            process.waitFor();
        }

        return String.join("\n", output);
    }

}
