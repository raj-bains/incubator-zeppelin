/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.file;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import com.google.gson.Gson;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;

/**
 * HDFS implementation of File interpreter for Zeppelin.
 *
 */
public class HDFSFileInterpreter extends FileInterpreter {
  static final String HDFS_URL = "hdfs.url";
  static final String HDFS_USER = "hdfs.user";
  static final String HDFS_MAXLENGTH = "hdfs.maxlength";

  static {
    Interpreter.register(
        "hdfs",
        "hdfs",
        HDFSFileInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add(HDFS_URL, "http://localhost:50070/webhdfs/v1/", "The URL for WebHDFS")
            .add(HDFS_USER, "hdfs", "The WebHDFS user")
            .add(HDFS_MAXLENGTH, "1000", "Maximum number of lines of results fetched").build());
  }

  Exception exceptionOnConnect = null;
  HDFSCommand cmd = null;
  Gson gson = null;

  public void prepare() {
    String userName = getProperty(HDFS_USER);
    String hdfsUrl = getProperty(HDFS_URL);
    int i = Integer.parseInt(getProperty(HDFS_MAXLENGTH));
    cmd = new HDFSCommand(hdfsUrl, userName, logger, i);
    gson = new Gson();
  }

  public HDFSFileInterpreter(Properties property){
    super(property);
    prepare();
  }

  /**
   * Status of one file
   *
   * matches returned JSON
   */
  public class OneFileStatus {
    public long accessTime;
    public int blockSize;
    public int childrenNum;
    public int fileId;
    public String group;
    public long length;
    public long modificationTime;
    public String owner;
    public String pathSuffix;
    public String permission;
    public int replication;
    public int storagePolicy;
    public String type;
    public String toString() {
      String str = "";
      str += "\nAccessTime = " + accessTime;
      str += "\nBlockSize = " + blockSize;
      str += "\nChildrenNum = " + childrenNum;
      str += "\nFileId = " + fileId;
      str += "\nGroup = " + group;
      str += "\nLength = " + length;
      str += "\nModificationTime = " + modificationTime;
      str += "\nOwner = " + owner;
      str += "\nPathSuffix = " + pathSuffix;
      str += "\nPermission = " + permission;
      str += "\nReplication = " + replication;
      str += "\nStoragePolicy = " + storagePolicy;
      str += "\nType = " + type;
      return str;
    }
  }

  /**
   * Status of one file
   *
   * matches returned JSON
   */
  public class SingleFileStatus {
    public OneFileStatus FileStatus;
  }

  /**
   * Status of all files in a directory
   *
   * matches returned JSON
   */
  public class MultiFileStatus {
    public OneFileStatus[] FileStatus;
  }

  /**
   * Status of all files in a directory
   *
   * matches returned JSON
   */
  public class AllFileStatus {
    public MultiFileStatus FileStatuses;
  }

  // tests whether we're able to connect to HDFS

  private void testConnection() {
    try {
      if (isDirectory("/"))
        logger.info("Successfully created WebHDFS connection");
    } catch (Exception e) {
      logger.error("testConnection: Cannot open WebHDFS connection. Bad URL: " + "/", e);
      exceptionOnConnect = e;
    }
  }

  @Override
  public void open() {
    testConnection();
  }

  @Override
  public void close() {
  }

  private String listDir(String path) throws Exception {
    return cmd.runCommand(cmd.listStatus, path, null);
  }

  private String listPermission(OneFileStatus fs){
    String s = "";
    s += fs.type.equalsIgnoreCase("Directory") ? 'd' : '-';
    int p = Integer.parseInt(fs.permission, 16);
    s += ((p & 0x400) == 0) ? '-' : 'r';
    s += ((p & 0x200) == 0) ? '-' : 'w';
    s += ((p & 0x100) == 0) ? '-' : 'x';
    s += ((p & 0x40)  == 0) ? '-' : 'r';
    s += ((p & 0x20)  == 0) ? '-' : 'w';
    s += ((p & 0x10)  == 0) ? '-' : 'x';
    s += ((p & 0x4)   == 0) ? '-' : 'r';
    s += ((p & 0x2)   == 0) ? '-' : 'w';
    s += ((p & 0x1)   == 0) ? '-' : 'x';
    return s;
  }
  private String listDate(OneFileStatus fs) {
    return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(fs.modificationTime));
  }
  private String ListOne(String path, OneFileStatus fs) {
    if (args.flags.contains(new Character('l'))) {
      String s = "";
      s += listPermission(fs) + "\t ";
      s += ((fs.replication == 0) ? "-" : fs.replication) + "\t ";
      s += fs.owner + "\t ";
      s += fs.group + "\t ";
      s += fs.length + "\t ";
      s += listDate(fs) + " GMT\t ";
      s += (path.length() == 1) ? path + fs.pathSuffix : path + '/' + fs.pathSuffix;
      return s;
    }
    return fs.pathSuffix;
  }

  public String listAll(String path) {
    String all = "";
    if (exceptionOnConnect != null)
      return all;
    try {
      String sfs = listDir(path);
      if (sfs != null) {
        AllFileStatus allFiles = gson.fromJson(sfs, AllFileStatus.class);

        if (allFiles != null &&
            allFiles.FileStatuses != null &&
            allFiles.FileStatuses.FileStatus != null)
        {
          for (OneFileStatus fs : allFiles.FileStatuses.FileStatus)
            all = all + ListOne(path, fs) + '\n';
        }
      }
    } catch (Exception e) {
      logger.error("listall: listDir " + path, e);
    }
    return all;
  }

  public boolean isDirectory(String path) {
    boolean ret = false;
    if (exceptionOnConnect != null)
      return ret;
    try {
      String str = cmd.runCommand(cmd.getFileStatus, path, null);
      SingleFileStatus sfs = gson.fromJson(str, SingleFileStatus.class);
      if (sfs != null)
        return sfs.FileStatus.type.equals("DIRECTORY");
    } catch (Exception e) {
      logger.error("IsDirectory: " + path, e);
    }
    return ret;
  }
}
