package com.example.ftpserver;


import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.text.Editable;

import com.blankj.utilcode.util.PathUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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


public class Appserver implements Runnable{

    //文件传输格式
    private final int TYPE_BINARY=0;
    private final int TYPE_ASCII=1;
    public String operation ="在操作";


    private ServerSocket control_server,pasvServer;//命令连接，被动数据连接的server
    private Socket control_socket=null;//命令连接socket

    private BufferedReader control_br=null;
    private PrintWriter control_pw=null;

    private Socket data_port,data_pasv;//主动和被动的数据连接
    private OutputStream data_os=null;
    private InputStream data_is=null;

    public static String path = "/storage/emulated/0/AB";//服务器在安卓本地存放文件的位置：/storage/emulated/0/Android/data/com.example.ftpserver/files
    private boolean isConnected=false;
    private String username="";
    private String target;
    private boolean login,anonymous,pasvFlag,portFlag;

    private String currentPath=path;
    private int control_port,pasv_port,type;//pasv_port为被动模式服务器的随机端口，type为文件格式

    private List<UserInfo> Users = new ArrayList<UserInfo>();
    private  List<UserInfo> BlackList = new ArrayList<UserInfo>();

    Appserver(){
        this.login=false;//登陆相关
        this.anonymous=false;//用于控制匿名用户权限
        this.pasvFlag=false;
        this.portFlag=false;
        this.control_port=8080;//默认的21命令连接端口

        this.Users.add(new UserInfo("test","test"));//默认用户
        this.BlackList.add(new UserInfo("pikachu","pikachu"));//非法用户

        try {
            control_server = new ServerSocket(control_port);
            System.out.println("Server等待连接中.....");
            control_socket = control_server.accept();//服务器套接字（接受登录信息，命令等）
            System.out.println("Server连接成功");
            System.out.println(path);
            isConnected=true;
            control_pw=new PrintWriter(new OutputStreamWriter(control_socket.getOutputStream()));//输出流
            control_br=new BufferedReader(new InputStreamReader(control_socket.getInputStream()));//输入流

        }catch (IOException e){
            e.printStackTrace();
        }
        control_pw.println("200 successful connection");//表示连接成功返回码为200
        control_pw.flush();
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
        if (str.equals("Binary")) {
            type = TYPE_BINARY;
            control_pw.println("200 Binary.");//type设置成功的返回码
        } else if (str.equals("ASCII")) {
            type = TYPE_ASCII;
            control_pw.println("200 ASCII.");
        } else {
            control_pw.println("500 Error Type");
        }
        control_pw.flush();
    }

    public String getFilename(String str) {
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


public void sendSingleFile(String client_filepath) throws IOException {
    String filePath = currentPath + client_filepath;
    System.out.println("文件地址："+filePath);

    File file = new File(filePath);
    if(!file.exists()){
        control_pw.println("550 Can't open " + getFilename(filePath)  +'\n');
        control_pw.flush();
        return;
    }


    try {
        BufferedReader br;
        BufferedWriter bw;
        if (portFlag) {
            data_os = data_port.getOutputStream();
        }
        if (pasvFlag) {
            data_os = data_pasv.getOutputStream();
        }
        try {
            br = new BufferedReader(new FileReader(filePath));
            control_pw.println("150 Opening ASCII mode data connection for " + getFilename(filePath));
            control_pw.flush();
        } catch (FileNotFoundException e) {
            control_pw.println("550 Can't open " + getFilename(filePath) + ' ' + e.getMessage() + '\n');
            control_pw.flush();
            return;
        }

        bw = new BufferedWriter(new OutputStreamWriter(data_os));
        String line;
        String data="";
        line = br.readLine();
        while (line != null ) {
            System.out.println("in while oppo");
            data = data +  "\n" +line;
            line = br.readLine();
        }
        data = data + "\n" + "END" + "\n";
        bw.write(data);

        bw.flush();

        System.out.println("文件数据："+data);
        if(control_socket.isConnected()){
            System.out.println("没断掉");
        }else{
            System.out.println("断掉了");
        }
        control_pw.println("226 Transfer complete.");
        control_pw.flush();

    }catch (Exception e) {
//        try {
//            data_pasv.close();
//            data_port.close();
//            pasvFlag = false;
//            portFlag = false;
//            pasvServer.close();
//        } catch (IOException e1) {
//            e1.printStackTrace();
//        }
        e.printStackTrace();
        }


}
    public  void loadSingleFile(String client_filepath) throws IOException {

        String filePath = currentPath+ File.separator + client_filepath;
        createFile(filePath);

        if (type == TYPE_ASCII) {
            try {
                BufferedWriter fin;
                BufferedReader br = null;

                if (portFlag) {
                    data_is = data_port.getInputStream();
                }
                if (pasvFlag) {
                    data_is = data_pasv.getInputStream();
                }
                try {
                    fin = new BufferedWriter(new FileWriter(filePath));
                    control_pw.println("150 Opening ASCII mode data connection for " + getFilename(filePath));
                    control_pw.flush();
                } catch (FileNotFoundException e) {
                    control_pw.println("550 Can't open " + getFilename(filePath) + ' ' + e.getMessage() + '\n');
                    control_pw.flush();
                    return;
                }
                br = new BufferedReader(new InputStreamReader(data_is));
                String line;
                String data="";
                line = br.readLine();
                while (!line.equals("END") ) {
                    System.out.println("in while oppo");
                    data = data +  "\n" +line;
                    line = br.readLine();
                }

                fin.write(data);
                System.out.println("OPPO VIVO HUAWEI IPHONE XIAOMI");
                fin.flush();
                fin.close();

                if(control_socket.isConnected()){
                    System.out.println("没断掉");
                }else{
                    System.out.println("断掉了");
                }
                control_pw.println("226 Transfer complete.");
                control_pw.flush();
                System.out.println("oppo");

//                if (portFlag) {
//                    portFlag = false;
//                    data_port.close();
//                }
//                if (pasvFlag) {
//                    data_pasv.close();
//                    pasvServer.close();
//                    pasvFlag = false;
//                }
            } catch (Exception e) {
                try {
                    data_pasv.close();
                    data_port.close();
                    pasvFlag = false;
                    portFlag = false;
                    pasvServer.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }

        if (type == TYPE_BINARY) {
            try {
                BufferedWriter fin;
                BufferedReader br = null;

                if (portFlag) {
                    data_is = data_port.getInputStream();
                }
                if (pasvFlag) {
                    data_is = data_pasv.getInputStream();
                }
                try {
                    fin = new BufferedWriter(new FileWriter(filePath));
                    control_pw.println("150 Opening ASCII mode data connection for " + getFilename(filePath));
                    control_pw.flush();
                } catch (FileNotFoundException e) {
                    control_pw.println("550 Can't open " + getFilename(filePath) + ' ' + e.getMessage() + '\n');
                    control_pw.flush();
                    return;
                }
                br = new BufferedReader(new InputStreamReader(data_is));
                String line;
                String data="";
                line = br.readLine();
                while (!line.equals("END") ) {
                    System.out.println("in while oppo");
                    data = data +  "\n" +line;
                    line = br.readLine();
                }

                fin.write(data);
                System.out.println("OPPO VIVO HUAWEI IPHONE XIAOMI");
                fin.flush();
                fin.close();
                if(control_socket.isConnected()){
                    System.out.println("没断掉");
                }else{
                    System.out.println("断掉了");
                }
                control_pw.println("226 Transfer complete.");
                control_pw.flush();

//
//                if (portFlag) {
//                    portFlag = false;
//                    data_port.close();
//                }
//                if (pasvFlag) {
//                    data_pasv.close();
//                    pasvServer.close();
//                    pasvFlag = false;
//                }
            } catch (Exception e) {
                try {
                    data_pasv.close();
                    data_port.close();
                    pasvFlag = false;
                    portFlag = false;
                    pasvServer.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }


    public void PORT(String parm) throws IOException {//来自客户端的命令形式为：PORT h1,h2,h3,h4,p1,p2
        try {

            StringTokenizer st = new StringTokenizer(parm, ",");//按逗号分隔
            String[] parmArray = new String[st.countTokens()];
            int i = 0;
            char[] temp;
            while (st.hasMoreTokens()) {
                parmArray[i] = st.nextToken();
                temp= parmArray[i].toCharArray();
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
            System.out.println(portIp + " "+portPort);
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
                control_pw.println("150 receiveOK.");
                control_pw.flush();
                data_port = new Socket(portIp, portPort);
                data_is = data_port.getInputStream();
                data_os = data_port.getOutputStream();
                portFlag = true;
                System.out.println("PORT successfully");

            } catch (Exception e) {
                e.printStackTrace();
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
        if (pasvFlag)pasvServer.close();

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
        int p1=pasv_port / 256;
        int p2= pasv_port % 256;
        control_pw.println("227 " + parmArray[0] + "," + parmArray[1] + "," + parmArray[2] + "," + parmArray[3] + "," + p1 + "," + p2);
        control_pw.flush();
        System.out.println("return OK");

        try {
            data_pasv = pasvServer.accept();
            data_is = data_pasv.getInputStream();
            data_os = data_pasv.getOutputStream();
            System.out.println("connect OK");
        } catch (IOException e) {
            return;
        }
        pasvFlag = true;
    }




    public void login(String username, String password) {
        System.out.println("登陆中......"+username+password);

        if(login){
            control_pw.println("230 already login");
            control_pw.flush();
            return;
        }

        if (username == null || password == null) {
            control_pw.println("500 username or password empty");
            control_pw.flush();
            return;
        }

        if (username.trim().equals("anonymous") && password.trim().equals("anonymous")) {//匿名用户
            anonymous = true;
            login=true;
            control_pw.println("230 anonymous login");
            control_pw.flush();
            return;
        }

        for(UserInfo u : BlackList){//非法用户判断
            if (username.trim().equals(u.getUsername())) {
                if (password.trim().equals(u.getPassword())) {
                    login = false;

                    control_pw.println("530 illegal user");
                    control_pw.flush();
                    return;
                }
            }
        }

        for(UserInfo u : Users){

            if (username.trim().equals(u.getUsername())) {
                System.out.println("find the user");
                System.out.println(u.getPassword());
                if (password.trim().equals(u.getPassword())) {
                    login = true;//登陆成功
                    control_pw.println("230 login successfully");
                    control_pw.flush();
                    System.out.println("find password");
                } else {//密码不对
                    login = false;
                    control_pw.println("500 wrong password");
                    control_pw.flush();
                    return;
                }
            } else {//用户不存在时
                login = false;
                control_pw.println("500 no this user");
                control_pw.flush();
                return;
            }
        }

    }

    public void LIST(String listPath){

        String tempPath = path + listPath;
        System.out.println("list的路径" +tempPath);
        File f =new File(tempPath);
        if( ! f.exists()){
            control_pw.println("450 no this file");//找不到对应文件或文件夹
            control_pw.flush();
        }

        if(f.isFile() && !f.isDirectory()){//如果是文件返回 “300 文件名”
            control_pw.println("300 "+f.getName());
            control_pw.flush();
            return;

        }else { //如果是文件夹返回 “200 文件名1 文件名2 文件名3”
            File[] files = f.listFiles();
            if(files == null){
                control_pw.println("200 empty");
                control_pw.flush();
                return;
            }
            StringBuilder result =new StringBuilder();
            result.append("200 ");

            for(int i =0; i<files.length;i++){
                result.append(files[i].getName()).append(",");
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

        String tempPath =  currentPath  + newpath;//path为服务器主目录
        File f = new File(currentPath);

        if (f.isDirectory() && f.exists()) {
            control_pw.println("250 new path is" + currentPath);
            control_pw.flush();
            currentPath = tempPath;
            System.out.println("CWD到了"+currentPath);
        }else {
            control_pw.println("550 is file ");
            control_pw.flush();//如果新路径指向文件，或者找不到路径会返回550
        }
    }

    public void CDUP() {
        currentPath = path;//切换到服务器主目录
        control_pw.println("250 current path is" + currentPath);
        control_pw.flush();

    }

    public void DELE(String filename){

        if(anonymous){
            control_pw.println("530 anonymous not allow");
            control_pw.flush();
            return;
        }

        File f =new File(currentPath + filename);
        //一些判断
        if(!f.exists()){
            System.out.println();//要删除的文件不存在
            control_pw.println("550 no such file");
            control_pw.flush();
        }

        //开始删除
        if(f.delete()) {
            control_pw.println("250 successfully deleted");
            control_pw.flush();
        }else {
            control_pw.println("530 deleted failed");
            control_pw.flush();

        }
    }

    public int createFolder(String folderPath) {//在安卓手机本地创建文件夹

        File appDir = new File(currentPath  +folderPath);
        System.out.println("创建文件在 " + appDir);

        if (!appDir.exists()) {
            boolean isSuccess = appDir.mkdirs();
            System.out.println("isSuccess:" + isSuccess);
            return 0;
        }
        return -1;
    }

    public void MKD(String newFolderPath){//创建folder，参数为路径

        if(anonymous){
            control_pw.println("530 anonymous not allow");
            control_pw.flush();
            return;
        }

        if(createFolder(newFolderPath) == 0){
            control_pw.println("257 folder successfully created");
            control_pw.flush();
        }else {
            control_pw.println("550 folder failed to be created");
            control_pw.flush();
        }
    }

    public void RMD(String folderPath){

        if(anonymous){
            control_pw.println("530 anonymous not allow");
            control_pw.flush();
            return;
        }

        File f =new File(path+folderPath);

        if(!f.isDirectory() || !f.exists()){
            System.out.println("550 no such dir or is not dir");
            control_pw.println("550 no such dir or is not dir");
            control_pw.flush();
            return;
        }

        File [] files = f.listFiles();
        for(int i=0;i < files.length;i++){//递归删除文件和可能存在的子目录
            if(files[i].isFile()){
                files[i].delete();
            }else {
                RMD(folderPath +"/"+ files[i].getName());
            }
        }

        //文件夹下的文件和子目录删除完后删除文件夹本身
        if(f.delete()){

            control_pw.println("250 successfully delete dir");
            control_pw.flush();
        }else {

            control_pw.println("delete dir failure");
            control_pw.flush();
        }
    }

    public void NOOP(){

        control_pw.println("200 NOOP");
        control_pw.flush();
    }

    public void RNFR(String targetFile){
        if(anonymous){
            control_pw.println("530 anonymous not allow");
            control_pw.flush();
            return;
        }

        File target = new File(currentPath + targetFile);
        if(!target.exists()){
            control_pw.println("530 target not exist");//这是文件夹或者该文件不存在
            control_pw.flush();
            return;
        }

        this.target = currentPath + targetFile;
        control_pw.println("200 target found");//重命名第一步，已经瞄准好文件
        control_pw.flush();

    }

    public void RNTO(String newFileName){

        if(anonymous){
            control_pw.println("530 anonymous not allow");
            control_pw.flush();
            return;
        }

        if(target == null){//必须经过RNFR指定重命名的文件后才能使用此命令
            control_pw.println("530 no target file");
            control_pw.flush();
            return;
        }
        File target = new File(this.target);
        if(!target.exists()){
            control_pw.println("530 target not exist");
            control_pw.flush();

            return;
        }
        target.renameTo(new File(currentPath+"/"+newFileName));
        control_pw.println("200 rename success");
        control_pw.flush();

    }

    public void USER(String username){
        if (username == null) {
            control_pw.println("500 username empty");
            control_pw.flush();
            return;
        }
        if(username.equals("anonymous")){
            anonymous = true;
            control_pw.println("331 anonymous user");
            control_pw.flush();
            return;
        }
        for(UserInfo u : BlackList){//非法用户判断,直接在USER阶段卡掉
            if (username.trim().equals(u.getUsername())) {
                login = false;
                control_pw.println("530 illegal user");
                control_pw.flush();
                return;
            }
        }

        for(UserInfo u : Users){
            if (username.trim().equals(u.getUsername())) {
                System.out.println("find the user");
                control_pw.println("331 user find");
                control_pw.flush();
            } else {//用户不存在时
                login = false;
                control_pw.println("500 no this user");
                control_pw.flush();
                return;
            }
        }

    }

    public void PASS(String password){
        if(anonymous){
            login = true;//登陆成功
            control_pw.println("230 login successfully");
            control_pw.flush();
            return;
        }
        for(UserInfo u : Users){
            if (username.trim().equals(u.getUsername())) {
                if(password.trim().equals(u.getPassword())) {
                    login = true;//登陆成功
                    control_pw.println("230 login successfully");
                    control_pw.flush();
                }else{//密码错误
                    control_pw.println("500 wrong password");
                    control_pw.flush();
                }
            }
        }
    }


    public void handleCommand(String command) throws IOException {
        if(command == null){
            return;
        }
        String [] com = command.trim().split(" ");
        System.out.println(command);
        System.out.println("当前目录："+currentPath);
        if(com[0].equals("USER")){
            if(com.length != 2){
                control_pw.println("500 wrong format");
                control_pw.flush();
                return;
            }
            this.username=com[1];
            USER(com[1]);
            System.out.println(username);
        }

        if(com[0].equals("PASS")){
            if(com.length != 2){
                control_pw.println("500 wrong format");
                control_pw.flush();
                return;
            }
            if(this.username == null){
                control_pw.println("500 what username");
                control_pw.flush();
                return;
            }
            PASS(com[1]);
        }

        switch (com[0]) {

            case "USER":
                break;

            case "PASS":
                break;

            case "STOR":
                System.out.println("STOR处理中");
                loadSingleFile(com[1]);
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
//                control_pw.println("200 OK");
//                control_pw.flush();
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
                if(com.length == 1){
                    System.out.println("root");
                    LIST("");
                    break;
                }
                if(com.length == 2){
                    LIST(com[1]);
                    break;
                }else {
                    control_pw.println("500 wrong format");
                    control_pw.flush();
            }
//                control_pw.println("200 OK");
//                control_pw.flush();
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

            case "RETR":
                if(com.length != 2){
                    control_pw.println("500 wrong format");
                    control_pw.flush();
                    break;
                }

                sendSingleFile(com[1]);
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
