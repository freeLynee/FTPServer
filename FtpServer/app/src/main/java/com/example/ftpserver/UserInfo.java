package com.example.ftpserver;

import java.util.ArrayList;
import java.util.List;

public class UserInfo {
    String username;
    String password;



    UserInfo(String username,String password){
        this.username=username;
        this.password=password;
    }

    public String getUsername(){
        return username;
    }

    public String getPassword(){
        return password;
    }

}
