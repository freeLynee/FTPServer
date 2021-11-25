package com.example.ftpserver;


import android.text.Editable;

import com.blankj.utilcode.util.PathUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;


public class Appserver implements Runnable {

    //文件传输格式
    private final int TYPE_BINARY = 0;
    private final int TYPE_ASCII = 1;


    private ServerSocket control_server, pasvServer;//命令连接，被动数据连接的server
    private Socket control_socket = null;//命令连接socket

    private BufferedReader control_br = null;
    private PrintWriter control_pw = null;

    private Socket data_port, data_pasv;//主动和被动的数据连接
    private OutputStream data_os = null;
    private InputStream data_is = null;

    public static String path = "/storage/emulated/0/Android/data/com.example.ftpserver/files";//服务器在安卓本地存放文件的位置：/storage/emulated/0/Android/data/com.example.ftpserver/files
    private boolean isConnected = false;
    private String username = "";
    private String target;
    private boolean login, anonymous, pasvFlag, portFlag;

    private String currentPath = path;
    private int control_port, pasv_port, type;//pasv_port为被动模式服务器的随机端口，type为文件格式


    private  List<UserInfo> Users = new ArrayList<UserInfo>();
    private  List<UserInfo> BlackList = new ArrayList<UserInfo>();


    Appserver() {
        this.login = false;//登陆相关
        this.anonymous = false;//用于控制匿名用户权限
        this.pasvFlag = false;
        this.portFlag = false;
        this.control_port = 8080;//默认的21命令连接端口

        this.Users.add(new UserInfo("test","test"));//默认用户
        this.BlackList.add(new UserInfo("pikachu","pikachu"));//非法用户


//        try {
//            control_server = new ServerSocket(control_port);
//            System.out.println("Server等待连接中.....");
//            control_socket = control_server.accept();//服务器套接字（接受登录信息，命令等）
//            System.out.println("Server连接成功");
//            System.out.println(path);
//            isConnected=true;
//            control_pw=new PrintWriter(new OutputStreamWriter(control_socket.getOutputStream()));//输出流
//            control_br=new BufferedReader(new InputStreamReader(control_socket.getInputStream()));//输入流
//
//        }catch (IOException e){
//            e.printStackTrace();
//        }
//        control_pw.println("200 successful connection");//表示连接成功返回码为200
//        control_pw.flush();
    }



    public void createFile(String path) throws IOException {//在本地创建文件的方法，参数为新文件路径
        File f = new File(path);
        if (!f.exists()) {
            f.createNewFile();
        } else {
            System.out.println("文件已存在");
        }

    }

    public void transferType(String str) {
        if (str.equals("I")) {
            type = TYPE_BINARY;
            control_pw.println("200 Type set to I.");//type设置成功的返回码
        } else if (str.equals("A")) {
            type = TYPE_ASCII;
            control_pw.println("200 Type set to A.");
        } else {
            control_pw.println("500 Error Type");
        }
        control_pw.flush();
    }


    public String getFilename(String str) {//从路径中抽取文件名
        int i = 0, pos = 0;
        while (true) {
            if (str.charAt(i) == '/') {
                pos = i;
            }
            i++;
            if (i == str.length()) break;
        }
        return str.substring(pos);
    }


    public void PORT(String parm) throws IOException {//来自客户端的命令形式为：PORT h1,h2,h3,h4,p1,p2
        try {

            StringTokenizer st = new StringTokenizer(parm, ",");//按逗号分隔
            String[] parmArray = new String[st.countTokens()];
            int i = 0;
            char[] temp;
            while (st.hasMoreTokens()) {
                parmArray[i] = st.nextToken();
                temp = parmArray[i].toCharArray();
                for (int j = 0; j < parmArray[i].length(); j++) {
                    if (!Character.isDigit(temp[j])) {
                        control_pw.println("530 wrong format");
                        control_pw.flush();
                        return;
                    }
                }
                i++;
            }

            if (i != 6) {//传输的参数不为6个
                control_pw.println("530 wrong format");
                control_pw.flush();
                return;
            }

            String portIp = parmArray[0] + "." + parmArray[1] + "." + parmArray[2] + "." + parmArray[3];//客户端ip
            int portPort = Integer.parseInt(parmArray[4]) * 256 + Integer.parseInt(parmArray[5]);//客户端随机分配的数据连接端口
            System.out.println(portIp + " " + portPort);
            if (pasvFlag) {
                data_pasv.close();
                pasvServer.close();
                pasvFlag = false;
            }
            if (portFlag) {
                data_port.close();
                portFlag = false;
            }
            try {
                //Thread.sleep(3000);//此时服务器sleep等待客户端accept后再进行连接
                control_pw.println("200 receiveOK.");
                control_pw.flush();
                data_port = new Socket(portIp, portPort);
                portFlag = true;
                System.out.println("PORT successfully");
            } catch (Exception e) {
                control_pw.println("425 fail to start port mode");
                portFlag = false;
                control_pw.flush();
                data_port.close();
                return;
            }

        } catch (Exception e) {
            e.printStackTrace();
            data_pasv.close();
            data_port.close();
            pasvFlag = false;
            portFlag = false;
            pasvServer.close();
        }

    }

    public void PASV() throws IOException {
        if (pasvFlag) pasvServer.close();

        pasv_port = 7890;//被动模式服务器自动分配端口,会从ui界面输入要分配的随机端口，目前值暂定

        pasvServer = new ServerSocket(pasv_port);

        String localIp = getIP();
        StringTokenizer st = new StringTokenizer(localIp, ".");
        String[] parmArray = new String[st.countTokens()];
        int i = 0;
        while (st.hasMoreTokens()) {
            parmArray[i] = st.nextToken();
            i++;
        }
        int p1 = pasv_port / 256;
        int p2 = pasv_port % 256;
        control_pw.println("227 " + parmArray[0] + "," + parmArray[1] + "," + parmArray[2] + "," + parmArray[3] + "," + p1 + "," + p2);
        control_pw.flush();
        System.out.println("return OK");

        try {
            data_pasv = pasvServer.accept();
        } catch (IOException e) {
            return;
        }
        System.out.println("connect OK");
        pasvFlag = true;
    }


    public void login(String username, String password) {
        System.out.println("登陆中......");
        if(login){
            System.out.println("230 already login");
//            control_pw.println("already login");
//            control_pw.flush();
            return;
        }

        if (username == null || password == null) {
            System.out.println("username or password empty");
//            control_pw.println("username or password empty");
//            control_pw.flush();
            return;
        }

        if (username.trim().equals("anonymous") && password.trim().equals("anonymous")) {//匿名用户
            anonymous = true;
            login=true;
//            control_pw.println("230 anonymous login");
//            control_pw.flush();
            return;
        }

        for(UserInfo u : BlackList){//非法用户判断
            if (username.trim().equals(u.getUsername())) {
                if (password.trim().equals(u.getPassword())) {
                    login = false;
                    System.out.println("530 illegal user");
//                    control_pw.println("530 illegal user");
//                    control_pw.flush();
                    return;
                }
            }
        }

        for(UserInfo u : Users){

            if (username.trim().equals(u.getUsername())) {
                if (password.trim().equals(u.getPassword())) {
                    login = true;
                    System.out.println("200 login successfully");
//                        control_pw.println("200 login successfully");
//                        control_pw.flush();
                } else {
                    login = false;
                    System.out.println("wrong password");
//                    control_pw.println("wrong password");
//                    control_pw.flush();
                    return;
                }
            } else {
                System.out.println("没有此用户哦");
                login = false;
//                control_pw.println("no this user");
//                control_pw.flush();
                return;
            }
        }

    }



    public void LIST(String listPath){

        String tempPath = path + listPath;
        File f =new File(tempPath);
        if( ! f.exists()){
            control_pw.println("450");//找不到对应文件或文件夹
            control_pw.flush();
        }

        if(f.isFile()){//如果是文件返回 “300 文件名”
            control_pw.println("300 "+f.getName());
            control_pw.flush();
            return;

        }else { //如果是文件夹返回 “200 文件名1 文件名2 文件名3”
            File[] files = f.listFiles();
            StringBuilder result =new StringBuilder();
            result.append("200 ");

            for(int i =0; i<files.length;i++){
                result.append(files[i].getName()).append(" ");
            }
            control_pw.println(result.toString());
            control_pw.flush();
        }

    }


    public static String getIP() {//得到服务器的IP
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface networkInterface = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddress = networkInterface.getInetAddresses(); enumIpAddress.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddress.nextElement();
                    if (!inetAddress.isLoopbackAddress() && (inetAddress instanceof Inet4Address)) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public void QUIT() throws IOException {
        control_pw.println("disconnect");
        control_pw.flush();
        data_port.close();
        data_port.close();
        pasvFlag = false;
        portFlag = false;
        pasvServer.close();
    }



    public void CWD(String newpath){//需要传入目录地址

        currentPath =  path + newpath;//path为服务器主目录
        File f = new File(currentPath);

        if (f.isDirectory() || f.exists() && !f.isFile()) {
            control_pw.println("250 new path is" + currentPath);
            control_pw.flush();
        }else {
            control_pw.println("550");
            control_pw.flush();//如果新路径指向文件，或者找不到路径会返回550
        }
    }

    public void CDUP() {
        currentPath = path;//切换到服务器主目录
        control_pw.println("250 current path is" + currentPath);
        control_pw.flush();

    }

    public void DELE(String filename){
        File f =new File(currentPath + "/" + filename);
        //一些判断
        if(!f.exists()){
            System.out.println("550 no such file");//要删除的文件不存在
        }
        if (f.isDirectory()){
            System.out.println("550 is a directory");//要路径指向文件夹
        }

        //开始删除
        if(f.delete()) {
            System.out.println("250 successfully deleted");
        }else {
            System.out.println("deleted failed");
        }
    }

    public int createFolder(String folderPath) {//在安卓手机本地创建文件夹
        File appDir = new File(path + folderPath);
        System.out.println("创建文件在 " + appDir);

        if (!appDir.exists()) {
            boolean isSuccess = appDir.mkdirs();
            System.out.println("isSuccess:" + isSuccess);
            return 0;
        }
        return -1;
    }

    public void MKD(String newFolderPath){//创建folder，参数为路径

        if(createFolder(newFolderPath) == 0){
            control_pw.println("257 folder successfully created");
            control_pw.flush();
        }else {
            control_pw.println("550 folder failed to be created");
            control_pw.flush();
        }
    }

    public void RMD(String folderPath){

        File f =new File(path+folderPath);

        if(!f.isDirectory() || !f.exists()){
            System.out.println("550 no such dir or is not dir");
            control_pw.println("550 no such dir or is not dir");
            control_pw.flush();
            return;
        }

        File [] files = f.listFiles();
        for(int i=0;i < files.length;i++){
            if(files[i].isFile()){
                files[i].delete();
                System.out.println(files[i].getAbsolutePath());
            }else {
                RMD(folderPath +"/"+ files[i].getName());
                System.out.println(files[i].getPath());
            }
        }
        if(f.delete()){
            System.out.println("文件夹删除成功");
            control_pw.println("250 successfully delete dir");
            control_pw.flush();
        }else {
            System.out.println("文件夹删除失败");
            control_pw.println("delete dir failure");
            control_pw.flush();
        }

    }

    public void NOOP(){
        control_pw.println("200 OK");
        control_pw.flush();
    }


    public void RNFR(String targetFile){
        File target = new File(currentPath + targetFile);
        if(!target.exists() || target.isDirectory()){
            System.out.println("这是文件夹或者该文件不存在");
            return;
        }

        this.target = currentPath +"/"+ targetFile;
        System.out.println("重命名第一步，已经瞄准好文件" + target);
    }

    public void RNTO(String newFileName){
        File target = new File(this.target);
        if(!target.exists() || target.isDirectory()){
            System.out.println("这是文件夹或者该文件不存在");
            return;
        }
        target.renameTo(new File(currentPath+"/"+newFileName));
        System.out.println("名字修改成功");

    }

    public void handleCommand(String command) throws IOException {
        String [] com = command.trim().split(" ");
        System.out.println(command);

        if(!login){
            control_pw.println("331 no Login");
            control_pw.flush();
            return;
        }

        switch (com[0]) {

            case "USER":
                if(com.length != 2){
                    control_pw.println("500 wrong format");
                    control_pw.flush();
                    break;
                }
                this.username=com[1];
                control_pw.println("200 OK");//得到用户名返回200表示收到
                control_pw.flush();
                System.out.println("user");
                break;

            case "PASS":
                if(com.length != 2){
                    control_pw.println("500 wrong format");
                    control_pw.flush();
                    break;
                }
                login(username,com[1]);
                System.out.println("login successfully");
                break;

            case "STOR":
                System.out.println("STOR处理中");
                //receiveSingleFile(com[1]);
                break;

            case "PORT":
                if(com.length != 2){
                    control_pw.println("500 wrong format");
                    control_pw.flush();
                    break;
                }
                System.out.println("port处理中");
                PORT(com[1]);
                break;

            case "PASV":
                System.out.println("pasv处理中");
                PASV();
                break;

            case "TYPE":
                if(com.length != 2){
                    control_pw.println("500 wrong format");
                    control_pw.flush();
                    break;
                }
                transferType(com[1]);
                control_pw.println("200 OK");
                control_pw.flush();
                break;

            case "STRU":
                control_pw.println("200 OK");
                control_pw.flush();
                break;

            case "MODE":
                System.out.println("mode处理中");
                control_pw.println("200 OK");
                control_pw.flush();
                break;

            case "LIST":
                if(com.length != 2){
                    control_pw.println("500 wrong format");
                    control_pw.flush();
                    break;
                }
                LIST(com[1]);
                control_pw.println("200 OK");
                control_pw.flush();
                break;

            case "CDUP":
                CDUP();
                break;

            case "CWD":
                if(com.length != 2){
                    control_pw.println("500 wrong format");
                    control_pw.flush();
                    break;
                }
                CWD(com[1]);
                break;

            case "RNFR":
                if(com.length != 2){
                    control_pw.println("500 wrong format");
                    control_pw.flush();
                    break;
                }
                RNFR(com[1]);
                break;

            case "RNTO":
                if(com.length != 2){
                    control_pw.println("500 wrong format");
                    control_pw.flush();
                    break;
                }
                RNTO(com[1]);
                break;

            case "NOOP":
                NOOP();
                break;
            case "DELE":
                if(com.length != 2){
                    control_pw.println("500 wrong format");
                    control_pw.flush();
                    break;
                }
                DELE(com[1]);
                break;
            case "RMD":
                if(com.length != 2){
                    control_pw.println("500 wrong format");
                    control_pw.flush();
                    break;
                }
                RMD(com[1]);
                break;

            case "MKD":
                if(com.length != 2){
                    control_pw.println("500 wrong format");
                    control_pw.flush();
                    break;
                }
                MKD(com[1]);
                break;

            case "QUIT":
            case "ABOR":
                QUIT();
                break;

            default:break;

        }
    }


    public void run(){
        while (true){
            String command = null;
            try {

                command = control_br.readLine();
                handleCommand(command);

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

}
